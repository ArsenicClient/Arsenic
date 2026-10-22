package arsenic.utils.render.capture;

import arsenic.main.Arsenic;
import arsenic.utils.rotations.SilentRotationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.client.audio.SoundEventAccessorComposite;
import net.minecraft.client.audio.SoundPoolEntry;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

/**
 * Game audio for the Recorder. OpenAL's output can't be read back, so every sound the game plays is logged with the
 * volume, pitch and position it was given (see MixinSoundManager). After recording, the sound files are decoded with
 * ffmpeg, mixed into a stereo track panned around the recorded view, and muxed into the video.
 *
 * Moving sounds (minecarts etc.) are mixed where they started; everything else follows what the sound system does.
 */
public final class SoundCapture {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int RATE = 48000, BLOCK = 4800;
    // how far a fully sideways sound is panned; OpenAL never hard-pans either
    private static final float PAN = 0.7F;

    private static SoundCapture active;

    private final boolean silentListener;
    private final List<Voice> voices = new ArrayList<>();
    private final Map<String, Voice> channels = new HashMap<>();
    // game pauses as [start, end) frame pairs; sounds playing at a pause hold their place
    private final List<long[]> pauses = new ArrayList<>();
    private long startNanos;
    private volatile long endNanos = -1;

    /** @param silentListener pan sounds around the silent rotation instead of the camera */
    public SoundCapture(boolean silentListener) {
        this.silentListener = silentListener;
    }

    /** Starts logging; {@code startNanos} is the {@link System#nanoTime()} the video's first frame belongs to. */
    public void begin(long startNanos) {
        this.startNanos = startNanos;
        active = this;
    }

    /** Stops logging. Must run on the main thread before the video recorder is stopped. */
    public void finish() {
        if (endNanos < 0)
            endNanos = System.nanoTime();
        if (!pauses.isEmpty() && pauses.get(pauses.size() - 1)[1] < 0)
            pauses.get(pauses.size() - 1)[1] = frame(endNanos);
        if (active == this)
            active = null;
    }

    public static boolean isActive() {
        return active != null;
    }

    // ---- main thread, from MixinSoundManager ----

    public static void onPlay(String channel, ISound sound, SoundPoolEntry entry) {
        SoundCapture capture = active;
        if (capture == null || capture.channels.containsKey(channel))
            return;
        capture.play(channel, sound, entry);
    }

    public static void onStop(String channel) {
        SoundCapture capture = active;
        if (capture == null)
            return;
        Voice voice = capture.channels.remove(channel);
        if (voice != null && voice.stop < 0)
            voice.stop = capture.frame(System.nanoTime());
    }

    public static void onPause() {
        SoundCapture capture = active;
        if (capture != null && (capture.pauses.isEmpty() || capture.pauses.get(capture.pauses.size() - 1)[1] >= 0))
            capture.pauses.add(new long[]{capture.frame(System.nanoTime()), -1});
    }

    public static void onResume() {
        SoundCapture capture = active;
        if (capture != null && !capture.pauses.isEmpty() && capture.pauses.get(capture.pauses.size() - 1)[1] < 0)
            capture.pauses.get(capture.pauses.size() - 1)[1] = capture.frame(System.nanoTime());
    }

    private void play(String channel, ISound sound, SoundPoolEntry entry) {
        // same volume and pitch SoundManager hands the sound system
        SoundEventAccessorComposite event = mc.getSoundHandler().getSound(sound.getSoundLocation());
        SoundCategory category = event == null ? SoundCategory.MASTER : event.getSoundCategory();
        float gain = MathHelper.clamp_float(sound.getVolume() * (float) entry.getVolume(), 0, 1)
                * (category == SoundCategory.MASTER ? 1 : mc.gameSettings.getSoundLevel(category))
                * mc.gameSettings.getSoundLevel(SoundCategory.MASTER);
        float pitch = (float) MathHelper.clamp_double(sound.getPitch() * entry.getPitch(), 0.5, 2.0);
        float left = 1, right = 1;

        Entity listener = mc.getRenderViewEntity();
        if (sound.getAttenuationType() == ISound.AttenuationType.LINEAR && listener != null) {
            double dx = sound.getXPosF() - listener.posX;
            double dy = sound.getYPosF() - (listener.posY + listener.getEyeHeight());
            double dz = sound.getZPosF() - listener.posZ;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            // paulscode's linear rolloff
            gain *= MathHelper.clamp_float(1 - (float) (dist / (16 * Math.max(1, sound.getVolume()))), 0, 1);
            if (dist > 1.0E-3) {
                double yaw = Math.toRadians(listenerYaw(listener));
                // listener's right is (-cos yaw, -sin yaw) on x/z
                float pan = (float) ((-dx * Math.cos(yaw) - dz * Math.sin(yaw)) / dist) * PAN;
                left = Math.min(1, 1 - pan);
                right = Math.min(1, 1 + pan);
            }
        }
        if (gain <= 0)
            return;
        Voice voice = new Voice(entry.getSoundPoolEntryLocation(), frame(System.nanoTime()), gain * left, gain * right,
                pitch, sound.canRepeat() && sound.getRepeatDelay() == 0);
        voices.add(voice);
        channels.put(channel, voice);
    }

    private float listenerYaw(Entity listener) {
        if (silentListener && listener == mc.thePlayer) {
            SilentRotationManager srm = Arsenic.getArsenic().getSilentRotationManager();
            if (srm.isModified())
                return srm.yaw;
        }
        return listener.rotationYaw;
    }

    private long frame(long nanos) {
        return Math.max(0, (nanos - startNanos) * RATE / 1_000_000_000L);
    }

    // ---- writer thread, after finish() ----

    /** Mixes the track and muxes it with {@code video} into {@code output}. Returns null on success or why it failed. */
    public String mux(String ffmpeg, File video, File output) {
        File raw = new File(output.getPath() + ".audio.raw");
        File log = new File(output.getPath() + ".log");
        try {
            writeTrack(ffmpeg, raw);
            List<String> cmd = Arrays.asList(ffmpeg, "-y", "-loglevel", "error",
                    "-i", video.getAbsolutePath(),
                    "-f", "f32le", "-ar", String.valueOf(RATE), "-ac", "2", "-i", raw.getAbsolutePath(),
                    "-map", "0:v", "-map", "1:a", "-c:v", "copy", "-c:a", "aac", "-b:a", "192k", "-shortest",
                    output.getAbsolutePath());
            int code = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(log).start().waitFor();
            if (code != 0)
                return "ffmpeg exited with code " + code + " (see " + log.getName() + ")";
            log.delete();
            return null;
        } catch (IOException e) {
            return e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "interrupted";
        } finally {
            raw.delete();
        }
    }

    private void writeTrack(String ffmpeg, File raw) throws IOException {
        long total = frame(endNanos);
        List<Voice> pending = new ArrayList<>(voices);
        pending.sort(Comparator.comparingLong(v -> v.start));
        // decoded sounds are dropped once no later voice uses them; music would otherwise pile up
        Map<ResourceLocation, Integer> uses = new HashMap<>();
        for (Voice v : pending)
            uses.merge(v.location, 1, Integer::sum);
        Map<ResourceLocation, float[]> decoded = new HashMap<>();
        List<Voice> playing = new ArrayList<>();
        float[] mix = new float[BLOCK * 2];
        ByteBuffer bytes = ByteBuffer.allocate(BLOCK * 8).order(ByteOrder.LITTLE_ENDIAN);
        int next = 0;

        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(raw), 1 << 20)) {
            for (long blockStart = 0; blockStart < total; blockStart += BLOCK) {
                int n = (int) Math.min(BLOCK, total - blockStart);
                Arrays.fill(mix, 0);
                while (next < pending.size() && pending.get(next).start < blockStart + n) {
                    Voice v = pending.get(next++);
                    float[] data = decoded.get(v.location);
                    if (data == null && !decoded.containsKey(v.location))
                        decoded.put(v.location, data = decode(ffmpeg, v.location));
                    if (data != null && data.length >= 2) {
                        v.data = data;
                        playing.add(v);
                    } else {
                        release(v, uses, decoded);
                    }
                }
                for (Iterator<Voice> it = playing.iterator(); it.hasNext(); ) {
                    Voice v = it.next();
                    if (render(v, blockStart, n, mix)) {
                        it.remove();
                        release(v, uses, decoded);
                    }
                }
                bytes.clear();
                for (int i = 0; i < n * 2; i++)
                    bytes.putFloat(Math.max(-1, Math.min(1, mix[i])));
                out.write(bytes.array(), 0, n * 8);
            }
        }
    }

    /** Adds {@code v} to the block; returns true once it has finished. */
    private boolean render(Voice v, long blockStart, int n, float[] mix) {
        float[] data = v.data;
        int frames = data.length / 2;
        for (int i = (int) Math.max(0, v.start - blockStart); i < n; i++) {
            long t = blockStart + i;
            if (v.stop >= 0 && t >= v.stop)
                return true;
            if (heldByPause(v, t))
                continue;
            if (v.pos >= frames) {
                if (!v.loop)
                    return true;
                v.pos %= frames;
            }
            int a = (int) v.pos;
            int b = a + 1 < frames ? a + 1 : v.loop ? 0 : a;
            float f = (float) (v.pos - a);
            mix[i * 2] += (data[a * 2] + (data[b * 2] - data[a * 2]) * f) * v.left;
            mix[i * 2 + 1] += (data[a * 2 + 1] + (data[b * 2 + 1] - data[a * 2 + 1]) * f) * v.right;
            v.pos += v.pitch;
        }
        return false;
    }

    private boolean heldByPause(Voice v, long t) {
        for (long[] p : pauses) {
            if (p[0] > t)
                return false;
            if (t < p[1] && v.start <= p[0])
                return true;
        }
        return false;
    }

    private static void release(Voice v, Map<ResourceLocation, Integer> uses, Map<ResourceLocation, float[]> decoded) {
        v.data = null;
        if (uses.merge(v.location, -1, Integer::sum) <= 0)
            decoded.remove(v.location);
    }

    /** Decodes a sound to interleaved stereo floats at {@link #RATE}, or null if it can't be read. */
    private static float[] decode(String ffmpeg, ResourceLocation location) {
        try {
            byte[] ogg;
            try (InputStream in = mc.getResourceManager().getResource(location).getInputStream()) {
                ogg = IOUtils.toByteArray(in);
            }
            Process process = new ProcessBuilder(ffmpeg, "-loglevel", "quiet", "-i", "pipe:0",
                    "-f", "f32le", "-ac", "2", "-ar", String.valueOf(RATE), "pipe:1").start();
            Thread feeder = new Thread(() -> {
                try (OutputStream stdin = process.getOutputStream()) {
                    stdin.write(ogg);
                } catch (IOException ignored) {
                }
            }, "Arsenic-Recorder-Decode");
            feeder.setDaemon(true);
            feeder.start();
            byte[] pcm;
            try (InputStream stdout = process.getInputStream()) {
                pcm = IOUtils.toByteArray(stdout);
            }
            if (process.waitFor() != 0)
                return null;
            float[] samples = new float[pcm.length / 4];
            ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(samples);
            return samples;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static final class Voice {
        final ResourceLocation location;
        final long start;
        final float left, right, pitch;
        final boolean loop;
        long stop = -1;
        double pos;
        float[] data;

        Voice(ResourceLocation location, long start, float left, float right, float pitch, boolean loop) {
            this.location = location;
            this.start = start;
            this.left = left;
            this.right = right;
            this.pitch = pitch;
            this.loop = loop;
        }
    }
}
