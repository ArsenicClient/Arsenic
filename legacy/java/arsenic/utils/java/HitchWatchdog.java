package arsenic.utils.java;

import arsenic.main.Arsenic;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Finds out where the game stalls. The render thread beats once per frame; when a beat is late, a daemon thread
 * samples the render thread's stack while it is stuck and, once it recovers, writes the stall (length, stack samples,
 * garbage collection during it, heap) to {@code hitches.log} in the Arsenic folder.
 */
public final class HitchWatchdog {

    private static final long THRESHOLD_NANOS = 120_000_000L;
    private static final long SAMPLE_EVERY_NANOS = 100_000_000L;
    private static final int MAX_SAMPLES = 8;
    private static final int MAX_FRAMES = 30;
    private static final long MAX_LOG_BYTES = 1_000_000L;

    private static volatile long lastBeat;
    private static volatile Thread renderThread;
    private static volatile boolean running;

    private HitchWatchdog() {}

    /** Render thread, once per frame. */
    public static void beat() {
        if (renderThread == null)
            renderThread = Thread.currentThread();
        lastBeat = System.nanoTime();
    }

    public static synchronized void start() {
        if (running)
            return;
        running = true;
        lastBeat = System.nanoTime();
        Thread t = new Thread(HitchWatchdog::watch, "Arsenic-HitchWatchdog");
        t.setDaemon(true);
        t.start();
    }

    public static void stop() {
        running = false;
    }

    private static void watch() {
        File file = new File(FileUtils.getArsenicFolderDirAsFile(), "hitches.log");
        List<GarbageCollectorMXBean> gcs = ManagementFactory.getGarbageCollectorMXBeans();
        SimpleDateFormat clock = new SimpleDateFormat("HH:mm:ss.SSS");

        while (running) {
            try {
                Thread.sleep(20);
                Thread rt = renderThread;
                long beat = lastBeat;
                if (rt == null || System.nanoTime() - beat < THRESHOLD_NANOS)
                    continue;

                // a stall: sample the stuck thread until it beats again
                long[] gcBefore = gcTotals(gcs);
                long started = beat;
                long stalledAt = System.currentTimeMillis();
                StringBuilder samples = new StringBuilder();
                int taken = 0;
                long nextSample = System.nanoTime();
                while (running && lastBeat == beat) {
                    if (System.nanoTime() >= nextSample && taken < MAX_SAMPLES) {
                        samples.append("  sample ").append(++taken).append(" at +")
                                .append((System.nanoTime() - started) / 1_000_000L).append("ms\n")
                                .append(stack(rt));
                        nextSample += SAMPLE_EVERY_NANOS;
                    }
                    Thread.sleep(10);
                }
                if (!running)
                    return;

                long[] gcAfter = gcTotals(gcs);
                Runtime rtm = Runtime.getRuntime();
                long used = (rtm.totalMemory() - rtm.freeMemory()) >> 20;
                long ms = (lastBeat - started) / 1_000_000L;
                String entry = "[" + clock.format(new Date(stalledAt)) + "] frame stalled " + ms + "ms"
                        + " | gc +" + (gcAfter[0] - gcBefore[0]) + " runs, +" + (gcAfter[1] - gcBefore[1]) + "ms"
                        + " | heap " + used + "/" + (rtm.maxMemory() >> 20) + "MB\n" + samples;
                write(file, entry);
            } catch (InterruptedException e) {
                return;
            } catch (Throwable ignored) {
            }
        }
    }

    private static long[] gcTotals(List<GarbageCollectorMXBean> gcs) {
        long count = 0, time = 0;
        for (GarbageCollectorMXBean gc : gcs) {
            if (gc.getCollectionCount() > 0)
                count += gc.getCollectionCount();
            if (gc.getCollectionTime() > 0)
                time += gc.getCollectionTime();
        }
        return new long[]{count, time};
    }

    private static String stack(Thread t) {
        StackTraceElement[] frames = t.getStackTrace();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(frames.length, MAX_FRAMES); i++)
            sb.append("    at ").append(frames[i]).append('\n');
        if (frames.length > MAX_FRAMES)
            sb.append("    ... ").append(frames.length - MAX_FRAMES).append(" more\n");
        return sb.toString();
    }

    private static void write(File file, String entry) {
        try {
            boolean fresh = file.length() > MAX_LOG_BYTES;
            try (FileWriter w = new FileWriter(file, !fresh)) {
                w.write(entry);
            }
            Arsenic a = Arsenic.getInstance();
            if (a != null)
                a.getLogger().warn("Frame stalled, see " + file.getPath());
        } catch (IOException ignored) {
        }
    }
}
