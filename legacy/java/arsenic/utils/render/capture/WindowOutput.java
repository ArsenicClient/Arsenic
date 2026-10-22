package arsenic.utils.render.capture;

import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

/**
 * Shows captured frames live in a separate window, for OBS's Window Capture.
 * Readback goes through two PBOs like {@link FfmpegRecorder}, so each frame is shown one capture late but the GPU
 * is never waited on. The window never takes focus from the game.
 */
public final class WindowOutput implements FrameSink {

    private final String title;
    private final long minFrameNanos;
    private final Object lock = new Object();

    private final int[] pbo = new int[2];
    private final boolean[] pending = new boolean[2];
    private int slot, width, height;
    private long lastCapture;

    // the render thread fills back, then swaps it with front under the lock; the window paints front
    private BufferedImage front, back;
    private JFrame frame;
    private View view;
    private volatile boolean running;

    public WindowOutput(String title, int fps) {
        this.title = title;
        this.minFrameNanos = 1_000_000_000L / Math.max(1, fps);
    }

    public void start() {
        running = true;
        Minecraft mc = Minecraft.getMinecraft();
        int w = mc.displayWidth, h = mc.displayHeight;
        SwingUtilities.invokeLater(() -> {
            if (!running)
                return;
            frame = new JFrame(title);
            view = new View();
            view.setPreferredSize(new Dimension(w, h));
            frame.setContentPane(view);
            frame.setFocusableWindowState(false);
            frame.setAutoRequestFocus(false);
            frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    // the Recorder notices on its next tick and disables itself, which frees the GL side
                    running = false;
                    frame.dispose();
                }
            });
            frame.pack();
            frame.setVisible(true);
        });
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void capture() {
        if (!running)
            return;
        long now = System.nanoTime();
        if (now - lastCapture < minFrameNanos)
            return;
        lastCapture = now;

        Minecraft mc = Minecraft.getMinecraft();
        if (pbo[0] == 0 || mc.displayWidth != width || mc.displayHeight != height)
            initPbos(mc.displayWidth, mc.displayHeight);
        int write = slot;
        slot ^= 1;
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbo[write]);
        GL11.glReadPixels(0, 0, width, height, GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, 0L);
        pending[write] = true;
        show(slot);
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
    }

    @Override
    public void stop() {
        running = false;
        deletePbos();
        SwingUtilities.invokeLater(() -> {
            if (frame != null)
                frame.dispose();
        });
    }

    private void initPbos(int w, int h) {
        deletePbos();
        width = w;
        height = h;
        for (int i = 0; i < 2; i++) {
            pbo[i] = GL15.glGenBuffers();
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbo[i]);
            GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, (long) w * h * 4, GL15.GL_STREAM_READ);
            pending[i] = false;
        }
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
        synchronized (lock) {
            front = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        }
        back = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    }

    private void deletePbos() {
        if (pbo[0] == 0)
            return;
        GL15.glDeleteBuffers(pbo[0]);
        GL15.glDeleteBuffers(pbo[1]);
        pbo[0] = pbo[1] = 0;
    }

    private void show(int i) {
        if (!pending[i])
            return;
        pending[i] = false;
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbo[i]);
        ByteBuffer mapped = GL15.glMapBuffer(GL21.GL_PIXEL_PACK_BUFFER, GL15.GL_READ_ONLY, (long) width * height * 4, null);
        if (mapped == null)
            return;
        // little-endian BGRA bytes read as ints are 0xAARRGGBB; GL rows are bottom-up
        IntBuffer pixels = mapped.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
        int[] data = ((DataBufferInt) back.getRaster().getDataBuffer()).getData();
        for (int y = 0; y < height; y++) {
            pixels.position((height - 1 - y) * width);
            pixels.get(data, y * width, width);
        }
        GL15.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER);
        synchronized (lock) {
            BufferedImage shown = front;
            front = back;
            back = shown;
        }
        View v = view;
        if (v != null)
            v.repaint();
    }

    private final class View extends JComponent {
        @Override
        protected void paintComponent(Graphics g) {
            int w = getWidth(), h = getHeight();
            g.setColor(Color.BLACK);
            synchronized (lock) {
                if (front == null) {
                    g.fillRect(0, 0, w, h);
                    return;
                }
                // fit the frame, letterboxed, when the window is not the game's size
                double scale = Math.min((double) w / front.getWidth(), (double) h / front.getHeight());
                int dw = (int) Math.round(front.getWidth() * scale), dh = (int) Math.round(front.getHeight() * scale);
                int dx = (w - dw) / 2, dy = (h - dh) / 2;
                g.fillRect(0, 0, w, dy);
                g.fillRect(0, dy + dh, w, h - dy - dh);
                g.fillRect(0, dy, dx, dh);
                g.fillRect(dx + dw, dy, w - dx - dw, dh);
                ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(front, dx, dy, dw, dh, null);
            }
        }
    }
}
