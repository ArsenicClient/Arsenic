package arsenic.utils.render.capture;

import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;

/**
 * Streams frames of the bound framebuffer to an ffmpeg process.
 * Readback goes through two PBOs so the GPU is never waited on; each frame is mapped one capture later.
 * Frames are paced against wall-clock time: slow game frames are repeated so the video keeps real-time length.
 */
public final class FfmpegRecorder implements FrameSink {

    private static final int BUFFERS = 4;
    private static final Frame END = new Frame(null, 0);

    private final String ffmpeg;
    private final File output;
    private final int width, height, fps, crf, frameBytes;

    private final BlockingQueue<Frame> queue = new ArrayBlockingQueue<>(BUFFERS + 1);
    private final BlockingQueue<byte[]> pool = new ArrayBlockingQueue<>(BUFFERS);
    private final int[] pbo = new int[2];
    private final int[] pending = new int[2];
    private int slot, carry;
    private long startNanos, emitted;

    private Process process;
    private volatile boolean running;
    private volatile String failure;

    public FfmpegRecorder(String ffmpeg, File output, int width, int height, int fps, int crf) {
        this.ffmpeg = ffmpeg;
        this.output = output;
        this.width = width;
        this.height = height;
        this.fps = fps;
        this.crf = crf;
        this.frameBytes = width * height * 4;
    }

    /** Launches ffmpeg. {@code onFinished} runs on the writer thread with null on success or an error message. */
    public void start(Consumer<String> onFinished) throws IOException {
        output.getParentFile().mkdirs();
        File log = new File(output.getPath() + ".log");
        List<String> cmd = Arrays.asList(ffmpeg, "-y", "-loglevel", "error",
                "-f", "rawvideo", "-pix_fmt", "bgra", "-s", width + "x" + height, "-framerate", String.valueOf(fps), "-i", "-",
                // GL rows are bottom-up; libx264 + yuv420p also needs even dimensions
                "-vf", "vflip,crop=trunc(iw/2)*2:trunc(ih/2)*2",
                "-c:v", "libx264", "-preset", "veryfast", "-crf", String.valueOf(crf), "-pix_fmt", "yuv420p",
                output.getAbsolutePath());
        process = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(log).start();

        for (int i = 0; i < BUFFERS; i++)
            pool.add(new byte[frameBytes]);
        startNanos = System.nanoTime();
        running = true;

        Thread writer = new Thread(() -> {
            try (OutputStream out = new BufferedOutputStream(process.getOutputStream(), 1 << 20)) {
                for (Frame frame; (frame = queue.take()) != END; ) {
                    for (int i = 0; i < frame.repeats; i++)
                        out.write(frame.data);
                    pool.offer(frame.data);
                }
            } catch (IOException e) {
                failure = "ffmpeg stopped accepting frames (see " + log.getName() + ")";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            running = false;
            queue.clear();
            try {
                int code = process.waitFor();
                if (failure == null && code != 0)
                    failure = "ffmpeg exited with code " + code + " (see " + log.getName() + ")";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (failure == null)
                log.delete();
            onFinished.accept(failure);
        }, "Arsenic-Recorder");
        writer.setDaemon(true);
        writer.start();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    public String getFailure() {
        return failure;
    }

    public File getOutput() {
        return output;
    }

    /** The {@link System#nanoTime()} the first frame belongs to. */
    public long getStartNanos() {
        return startNanos;
    }

    /** Reads the bound framebuffer. Must run on the render thread. */
    @Override
    public void capture() {
        if (!running)
            return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.displayWidth != width || mc.displayHeight != height) {
            failure = "Window was resized";
            stop();
            return;
        }
        long due = (System.nanoTime() - startNanos) * fps / 1_000_000_000L + 1;
        if (due <= emitted)
            return;
        int repeats = (int) Math.min(due - emitted, fps * 5L);
        emitted = due;

        if (pbo[0] == 0)
            initPbos();
        int write = slot;
        slot ^= 1;
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbo[write]);
        GL11.glReadPixels(0, 0, width, height, GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, 0L);
        pending[write] = repeats;
        drain(slot);
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
    }

    /** Flushes the last frame and closes ffmpeg's input. Must run on the render thread. */
    @Override
    public void stop() {
        if (pbo[0] != 0) {
            drain(slot);
            drain(slot ^ 1);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            GL15.glDeleteBuffers(pbo[0]);
            GL15.glDeleteBuffers(pbo[1]);
            pbo[0] = pbo[1] = 0;
        }
        if (running) {
            running = false;
            queue.offer(END);
        }
    }

    private void initPbos() {
        for (int i = 0; i < 2; i++) {
            pbo[i] = GL15.glGenBuffers();
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbo[i]);
            GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, frameBytes, GL15.GL_STREAM_READ);
        }
    }

    private void drain(int i) {
        if (pending[i] == 0)
            return;
        int repeats = pending[i] + carry;
        pending[i] = 0;
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbo[i]);
        ByteBuffer mapped = GL15.glMapBuffer(GL21.GL_PIXEL_PACK_BUFFER, GL15.GL_READ_ONLY, frameBytes, null);
        byte[] data = pool.poll();
        if (mapped != null && data != null && running) {
            mapped.get(data);
            queue.offer(new Frame(data, repeats));
            carry = 0;
        } else {
            // encoder is behind: hold the time slot over to the next frame we can send
            if (data != null)
                pool.offer(data);
            carry = repeats;
        }
        if (mapped != null)
            GL15.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER);
    }

    private static final class Frame {
        final byte[] data;
        final int repeats;

        Frame(byte[] data, int repeats) {
            this.data = data;
            this.repeats = repeats;
        }
    }
}
