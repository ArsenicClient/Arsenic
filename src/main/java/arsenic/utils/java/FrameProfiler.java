package arsenic.utils.java;

import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.utils.minecraft.PlayerUtils;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GLContext;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds out what costs frame time. While running ({@code .profile}), every event listener and a few named render
 * sections time themselves on the client thread; GPU time is measured with timer queries for the sections that
 * mostly cost GPU work (blur, Render2D, RenderWorldLast). When it stops, the top entries go to chat and the full
 * table to {@code profile.log} in the Arsenic folder.
 * <p>
 * Times are inclusive: a listener that posts another event (PostProcessing posting Bloom/Blur) includes the
 * listeners of that event. When not running, the cost is one field read per listener call.
 */
public final class FrameProfiler {

    private static final int GPU_RING = 4;

    private static boolean running;
    private static Thread clientThread;
    private static long startedAt, stopAt, lastFrameStart;
    private static long frames, frameNanos, worstFrameNanos;

    private static final Map<String, Section> SECTIONS = new LinkedHashMap<>();
    private static final List<GpuSection> GPU_SECTIONS = new ArrayList<>();

    private FrameProfiler() {}

    // ---- CPU sections ---------------------------------------------------------------------------

    public static final class Section {
        final String owner, detail;
        private final Object ownerRef;
        long nanos, calls, frameNanos, worstFrameNanos;

        Section(Object ownerRef, String owner, String detail) {
            this.ownerRef = ownerRef;
            this.owner = owner;
            this.detail = detail;
        }

        String label() {
            // module names are read late: a module can be renamed by its config
            String name = ownerRef instanceof Module ? ((Module) ownerRef).getName() : owner;
            return detail.isEmpty() ? name : name + " " + detail;
        }
    }

    /** The section for one event listener; the same section is returned when a module subscribes again. */
    public static synchronized Section listener(Object owner, String field, Type eventType) {
        String key = owner.getClass().getName() + "#" + field;
        Section section = SECTIONS.get(key);
        if (section == null) {
            section = new Section(owner, owner.getClass().getSimpleName(), "(" + simpleName(eventType) + ")");
            SECTIONS.put(key, section);
        }
        return section;
    }

    /** A named section timed by hand with {@link #start()} and {@link #end}. */
    public static synchronized Section section(String name) {
        return SECTIONS.computeIfAbsent("#" + name, k -> new Section(null, name, ""));
    }

    /** @return a start time to hand to {@link #end}, or 0 when this call isn't being timed */
    public static long start() {
        if (!running || Thread.currentThread() != clientThread)
            return 0;
        return System.nanoTime();
    }

    public static void end(Section section, long start) {
        if (start == 0)
            return;
        long took = System.nanoTime() - start;
        section.nanos += took;
        section.frameNanos += took;
        section.calls++;
    }

    // ---- GPU sections ---------------------------------------------------------------------------

    /** GPU time of a stretch of draw calls, read a few frames later so the CPU never waits for it. Not nestable. */
    public static final class GpuSection {
        final String name;
        private final int[] queries = new int[GPU_RING];
        private final boolean[] pending = new boolean[GPU_RING];
        private int next;
        long nanos, samples;

        GpuSection(String name) {
            this.name = name;
        }

        private void collect() {
            for (int i = 0; i < GPU_RING; i++) {
                if (!pending[i] || GL15.glGetQueryObjecti(queries[i], GL15.GL_QUERY_RESULT_AVAILABLE) == 0)
                    continue;
                if (running) {
                    nanos += GL33.glGetQueryObjectui64(queries[i], GL15.GL_QUERY_RESULT);
                    samples++;
                }
                pending[i] = false;
            }
        }
    }

    public static synchronized GpuSection gpuSection(String name) {
        GpuSection section = new GpuSection(name);
        GPU_SECTIONS.add(section);
        return section;
    }

    /** @return the slot to hand to {@link #gpuEnd}, or -1 when this stretch isn't being timed */
    public static int gpuBegin(GpuSection section) {
        if (!running || Thread.currentThread() != clientThread || !GLContext.getCapabilities().OpenGL33)
            return -1;
        int slot = section.next;
        if (section.pending[slot])
            return -1;              // the GPU is more than a ring behind: skip this sample rather than wait
        if (section.queries[slot] == 0)
            section.queries[slot] = GL15.glGenQueries();
        GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, section.queries[slot]);
        return slot;
    }

    public static void gpuEnd(GpuSection section, int slot) {
        if (slot < 0)
            return;
        GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
        section.pending[slot] = true;
        section.next = (slot + 1) % GPU_RING;
    }

    // ---- frames and control ---------------------------------------------------------------------

    /** Render thread, at the start of every frame. */
    public static void frameStart() {
        clientThread = Thread.currentThread();
        if (!running)
            return;
        long now = System.nanoTime();
        if (lastFrameStart != 0) {
            long frame = now - lastFrameStart;
            frames++;
            frameNanos += frame;
            worstFrameNanos = Math.max(worstFrameNanos, frame);
        }
        lastFrameStart = now;
        synchronized (FrameProfiler.class) {
            for (Section section : SECTIONS.values()) {
                section.worstFrameNanos = Math.max(section.worstFrameNanos, section.frameNanos);
                section.frameNanos = 0;
            }
            if (GLContext.getCapabilities().OpenGL33)
                for (GpuSection section : GPU_SECTIONS)
                    section.collect();
        }
        if (now >= stopAt)
            stop();
    }

    public static boolean isRunning() {
        return running;
    }

    public static synchronized void start(int seconds) {
        for (Section section : SECTIONS.values())
            section.nanos = section.calls = section.frameNanos = section.worstFrameNanos = 0;
        for (GpuSection section : GPU_SECTIONS) {
            section.nanos = section.samples = 0;
            // results left over from an earlier run are dropped; their queries are simply reused
            java.util.Arrays.fill(section.pending, false);
        }
        frames = frameNanos = worstFrameNanos = lastFrameStart = 0;
        startedAt = System.nanoTime();
        stopAt = startedAt + seconds * 1_000_000_000L;
        running = true;
    }

    public static synchronized void stop() {
        if (!running)
            return;
        running = false;
        report();
    }

    // ---- report ---------------------------------------------------------------------------------

    private static void report() {
        if (frames == 0) {
            PlayerUtils.addWaterMarkedMessageToChat("§cprofiler saw no frames");
            return;
        }
        List<Section> sections = new ArrayList<>();
        for (Section section : SECTIONS.values())
            if (section.calls > 0)
                sections.add(section);
        sections.sort((a, b) -> Long.compare(b.nanos, a.nanos));

        double avgFrame = ms(frameNanos) / frames;
        PlayerUtils.addWaterMarkedMessageToChat(String.format("§fprofile: §a%d fps §7avg %.2fms, worst %.1fms over %d frames",
                Math.round(1000.0 / avgFrame), avgFrame, ms(worstFrameNanos), frames));
        for (int i = 0; i < Math.min(8, sections.size()); i++) {
            Section s = sections.get(i);
            double perFrame = ms(s.nanos) / frames;
            PlayerUtils.addWaterMarkedMessageToChat(String.format(" §f%.3fms §7(%.1f%%) §f%s §8worst %.2fms",
                    perFrame, perFrame / avgFrame * 100, s.label(), ms(s.worstFrameNanos)));
        }
        for (GpuSection g : GPU_SECTIONS)
            if (g.samples > 0)
                PlayerUtils.addWaterMarkedMessageToChat(String.format(" §bgpu §f%.3fms §7%s", ms(g.nanos) / g.samples, g.name));

        File file = new File(FileUtils.getArsenicFolderDirAsFile(), "profile.log");
        try (FileWriter w = new FileWriter(file)) {
            w.write(String.format("[%s] %d frames, avg %.3fms (%d fps), worst %.2fms%n",
                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()), frames, avgFrame,
                    Math.round(1000.0 / avgFrame), ms(worstFrameNanos)));
            w.write("CPU time on the client thread, inclusive of nested events.\n\n");
            w.write(String.format("%10s %7s %10s %10s  %s%n", "ms/frame", "%frame", "worst ms", "calls/fr", "section"));
            for (Section s : sections) {
                double perFrame = ms(s.nanos) / frames;
                w.write(String.format("%10.4f %6.2f%% %10.3f %10.1f  %s%n", perFrame, perFrame / avgFrame * 100,
                        ms(s.worstFrameNanos), (double) s.calls / frames, s.label()));
            }
            w.write("\nGPU time (timer queries, per sampled frame):\n");
            for (GpuSection g : GPU_SECTIONS)
                w.write(g.samples > 0 ? String.format("%10.4f  %s%n", ms(g.nanos) / g.samples, g.name)
                        : String.format("%10s  %s%n", "n/a", g.name));
            PlayerUtils.addWaterMarkedMessageToChat("§7full table: §f" + file.getPath());
        } catch (IOException e) {
            Arsenic.getArsenic().getLogger().warn("Could not write " + file.getPath(), e);
        }
    }

    private static double ms(long nanos) {
        return nanos / 1_000_000.0;
    }

    private static String simpleName(Type type) {
        if (type instanceof Class<?>) {
            Class<?> c = (Class<?>) type;
            return c.getEnclosingClass() != null ? c.getEnclosingClass().getSimpleName() + "." + c.getSimpleName() : c.getSimpleName();
        }
        if (type instanceof ParameterizedType)
            return simpleName(((ParameterizedType) type).getRawType());
        return type.getTypeName();
    }
}
