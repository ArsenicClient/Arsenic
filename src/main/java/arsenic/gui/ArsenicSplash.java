package arsenic.gui;

import arsenic.utils.java.ColorUtils;
import arsenic.utils.java.MathUtils;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.Drawable;
import org.lwjgl.opengl.SharedDrawable;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.IntBuffer;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_BGRA;
import static org.lwjgl.opengl.GL12.GL_UNSIGNED_INT_8_8_8_8_REV;

public final class ArsenicSplash {

    private static final Lock lock = new ReentrantLock(true);
    private static final float FONT_BASE_SIZE = 32f;
    private static final int FIRST_CHAR = 32, LAST_CHAR = 126;

    private static volatile boolean done, enabled;
    private static volatile float goal;
    private static volatile String message = "Starting";
    private static volatile Throwable threadError;
    private static Drawable drawable;
    private static Thread thread;
    private static Semaphore mutex = new Semaphore(1);

    private static int logoTex, logoW, logoH;
    private static int fontTex, fontW, fontH, fontLineH;
    private static final float[] glyphU = new float[LAST_CHAR - FIRST_CHAR + 1];
    private static final float[] glyphV = new float[glyphU.length];
    private static final int[] glyphAdv = new int[glyphU.length];
    private static final int[] glyphCellW = new int[glyphU.length];

    private ArsenicSplash() {}


    private static float[] bgTop = {0.043f, 0.043f, 0.063f}, bgBottom = {0.090f, 0.082f, 0.133f};
    private static float[] accent = {0.486f, 0.227f, 0.929f}, accentEnd = accent, ink = {1f, 1f, 1f};

    private static final Object[][] THEMES = {
            {"Classic", 0xDD425E, 0x494949}, {"Void", 0x7C3AED, 0x1A1025}, {"Specter", 0x38BDF8, 0x0D1B2A},
            {"Ember", 0xEA580C, 0x1C1008}, {"Jade", 0x10B981, 0x081A12}, {"Obsidian", 0xE2E2E2, 0x0A0A0A},
            {"Sakura", 0xF472B6, 0x2D1520}, {"Toxin", 0x84CC16, 0x0C1200},
            {"Ocean", 0x3B82F6, 0x000000}
    };

    /** The menu style is chosen in the click GUI (Appearance > Menu Style); it is read from the config like the theme. Element 33 is the default. */
    private static boolean element;

    private static void loadTheme() {
        element = true;
        try (java.io.Reader r = new java.io.FileReader(new java.io.File(Minecraft.getMinecraft().mcDataDir, "Arsenic/clientConfig.json"))) {
            com.google.gson.JsonObject style = new com.google.gson.JsonParser().parse(r).getAsJsonObject().getAsJsonObject("GuiStyle");
            String chosen = style.has("screenStyle") ? style.get("screenStyle").getAsString() : style.get("loadingScreen").getAsString();
            element = !"Ocean".equals(chosen);          // anything but Ocean (including the old Toxic) is Element 33
        } catch (Throwable ignored) { }
        int main = 0xDD425E, back = 0x494949;      // Classic, the default theme
        try (java.io.Reader r = new java.io.FileReader(new java.io.File(Minecraft.getMinecraft().mcDataDir, "Arsenic/clientConfig.json"))) {
            String name = new com.google.gson.JsonParser().parse(r).getAsJsonObject()
                    .getAsJsonObject("themeManager").get("currentTheme").getAsString();
            for (Object[] t : THEMES)
                if (t[0].equals(name) || (name.equals("Midnight") && t[0].equals("Ocean"))) {
                    main = (Integer) t[1];
                    back = (Integer) t[2];
                }
        } catch (Throwable ignored) {  }

        boolean light = luminance(back) > 0.6f;
        float[] bg = rgb(back), acc = rgb(main);
        bgTop = light ? bg : mix(bg, new float[]{0, 0, 0}, 0.35f);
        bgBottom = mix(bg, acc, light ? 0.10f : 0.14f);
        accent = acc;
        accentEnd = mix(acc, light ? new float[]{0, 0, 0} : new float[]{1, 1, 1}, 0.35f);
        ink = light ? new float[]{0.07f, 0.07f, 0.09f} : new float[]{1f, 1f, 1f};
        if (Math.abs(luminance(main) - luminance(back)) < 0.15f) {
            accent = ink;
            accentEnd = mix(ink, bg, 0.4f);
        }
    }


    // ---- the Element 33 loading screen ------------------------------------------------------------
    // A crystal lattice igniting from the bottom up: hexagonal cells light row by row in the theme
    // colour, brightest at the growth front and cooling behind it. Sparks pop off the front, vapour
    // rises from the lit cells, and an electron circles the percentage on a thin orbit.

    private static final int E_PARTS = 110;
    private static final float[] epX = new float[E_PARTS], epY = new float[E_PARTS], epVX = new float[E_PARTS],
            epVY = new float[E_PARTS], epLife = new float[E_PARTS], epMax = new float[E_PARTS];
    private static final boolean[] epVapour = new boolean[E_PARTS];
    private static int epNext;

    private static float cellHash(int a, int b) {
        int n = a * 374761393 + b * 668265263;
        n = (n ^ (n >> 13)) * 1274126177;
        n ^= n >> 16;
        return (n & 0xFFFF) / 65535f;
    }

    private static void emitPart(float x, float y, float vx, float vy, float life, boolean vapour) {
        int i = epNext++ % E_PARTS;
        epX[i] = x;
        epY[i] = y;
        epVX[i] = vx;
        epVY[i] = vy;
        epLife[i] = epMax[i] = life;
        epVapour[i] = vapour;
    }

    /** One stepped line of small squares; call between glBegin(GL_QUADS) and glEnd. */
    private static void pixLine(float x0, float y0, float x1, float y1, float size) {
        float dx = x1 - x0, dy = y1 - y0;
        int steps = (int) Math.max(1, Math.max(Math.abs(dx), Math.abs(dy)) / (size * 1.5f));
        for (int i = 0; i <= steps; i++) {
            float k = i / (float) steps;
            float px = x0 + dx * k - size / 2f, py = y0 + dy * k - size / 2f;
            glVertex2f(px, py);
            glVertex2f(px + size, py);
            glVertex2f(px + size, py + size);
            glVertex2f(px, py + size);
        }
    }

    /** A flat-topped hexagon filled with horizontal runs, so the edge is stepped like pixel art. */
    private static void hexFill(float cx, float cy, float r, float rowStep) {
        float hh = r * 0.866f;
        for (float dy = -hh; dy < hh; dy += rowStep) {
            float mid = Math.abs(dy + rowStep / 2f);
            float half = r - mid / 1.732f;
            glVertex2f(cx - half, cy + dy);
            glVertex2f(cx + half, cy + dy);
            glVertex2f(cx + half, cy + dy + rowStep);
            glVertex2f(cx - half, cy + dy + rowStep);
        }
    }

    private static void drawElementLoad(int w, int h, float progress, float u, float t, float fade) {
        float R = 26f * u, colW = R * 1.5f, rowH = R * 1.732f;
        float level = Math.max(0f, Math.min(1f, progress));
        int cols = (int) (w / colW) + 3;
        int rows = (int) (h / rowH) + 3;
        float front = level * (rows + 2);                   // how many rows have lit, counting from the bottom
        float frontY = h - level * h * 0.985f;

        // the faint lattice, drifting slowly, over the whole screen
        float dx = (t * 6f * u) % (colW * 2f), dy = (t * 3f * u) % rowH;
        glBegin(GL_QUADS);
        glColor4f(accent[0], accent[1], accent[2], 0.075f * fade);
        for (int c = -2; c < cols; c++) {
            for (int r = -1; r < rows; r++) {
                float cx = c * colW - dx, cy = r * rowH - dy + ((c & 1) != 0 ? rowH / 2f : 0f);
                float hh = R * 0.866f;
                pixLine(cx - R / 2, cy - hh, cx + R / 2, cy - hh, 2f * u);
                pixLine(cx - R, cy, cx - R / 2, cy - hh, 2f * u);
                pixLine(cx + R, cy, cx + R / 2, cy - hh, 2f * u);
            }
        }
        glEnd();

        // the block holding the logo, percentage and status: cells behind it are dimmed so the text keeps its contrast
        float lhLogo = 420f * u * logoH / logoW;
        float tl = w / 2f - 250f * u - R, tr = w / 2f + 250f * u + R;
        float tt = h * 0.40f - lhLogo / 2f - R, tb = h * 0.40f + lhLogo / 2f + 130f * u + R;

        // the lit cells: dim and dark behind the front, bright and saturated at it
        glBegin(GL_QUADS);
        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < rows; r++) {
                float jitter = (cellHash(c, r) - 0.5f) * 1.6f;          // a ragged front, like crystal growth
                float rr = r + ((c & 1) != 0 ? 0.5f : 0f) + jitter;
                float age = front - rr;
                if (age <= 0f) continue;
                float cx = c * colW + R * 0.2f, cy = h - (r * rowH + ((c & 1) != 0 ? rowH / 2f : 0f)) + rowH * 0.1f;
                float heat = (float) Math.exp(-age / 1.7f);              // 1 at the front, fading behind
                float grow = Math.min(1f, age * 1.4f);
                float a = (0.07f + 0.58f * heat) * fade;
                if (cx > tl && cx < tr && cy > tt && cy < tb) a *= 0.2f;
                float mixK = heat;
                glColor4f(accent[0] + (accentEnd[0] - accent[0]) * mixK,
                        accent[1] + (accentEnd[1] - accent[1]) * mixK,
                        accent[2] + (accentEnd[2] - accent[2]) * mixK, a);
                hexFill(cx, cy, R * 0.9f * grow, 3f * u);
            }
        }
        glEnd();

        // the growth front: a soft band of light with a thin bright line in the middle
        if (level > 0.002f && level < 0.999f) {
            glBegin(GL_QUADS);
            glColor4f(accentEnd[0], accentEnd[1], accentEnd[2], 0f);
            glVertex2f(0, frontY - 26f * u);
            glVertex2f(w, frontY - 26f * u);
            glColor4f(accentEnd[0], accentEnd[1], accentEnd[2], 0.26f * fade);
            glVertex2f(w, frontY);
            glVertex2f(0, frontY);
            glColor4f(accentEnd[0], accentEnd[1], accentEnd[2], 0.26f * fade);
            glVertex2f(0, frontY);
            glVertex2f(w, frontY);
            glColor4f(accentEnd[0], accentEnd[1], accentEnd[2], 0f);
            glVertex2f(w, frontY + 22f * u);
            glVertex2f(0, frontY + 22f * u);
            glEnd();
            // the line is cut into dashes that flicker, so it reads as crystal edges, not a ruler
            glBegin(GL_QUADS);
            for (int c = 0; c < cols; c++) {
                float flick = 0.45f + 0.55f * cellHash(c, (int) (t * 7f));
                glColor4f(1f, 1f, 1f, 0.55f * flick * fade);
                float x = c * colW - 4f;
                glVertex2f(x, frontY - 1.2f * u);
                glVertex2f(x + colW * 0.8f, frontY - 1.2f * u);
                glVertex2f(x + colW * 0.8f, frontY + 1.2f * u);
                glVertex2f(x, frontY + 1.2f * u);
            }
            glEnd();

            // sparks pop off the front, vapour rises from the lit cells
            for (int k = 0; k < 2; k++)
                emitPart((float) (Math.random() * w), frontY, (float) (Math.random() - 0.5) * 50f * u,
                        -(40f + (float) Math.random() * 80f) * u, 0.7f + (float) Math.random() * 0.6f, false);
            if (Math.random() < 0.7)
                emitPart((float) (Math.random() * w), frontY + (float) Math.random() * (h - frontY),
                        (float) (Math.random() - 0.5) * 8f * u, -(16f + (float) Math.random() * 14f) * u,
                        2f + (float) Math.random() * 1.8f, true);
        }

        glBegin(GL_QUADS);
        for (int i = 0; i < E_PARTS; i++) {
            if (epLife[i] <= 0f) continue;
            epLife[i] -= 0.016f;
            epX[i] += epVX[i] * 0.016f;
            epY[i] += epVY[i] * 0.016f;
            if (!epVapour[i]) epVY[i] += 70f * u * 0.016f;
            float k = Math.max(0f, epLife[i] / epMax[i]);
            float s = (epVapour[i] ? 4.5f + (1f - k) * 6f : 2.2f) * u;
            if (epVapour[i]) glColor4f(accentEnd[0], accentEnd[1], accentEnd[2], k * 0.16f * fade);
            else glColor4f(1f, 1f, 1f, k * 0.95f * fade);
            glVertex2f(epX[i], epY[i]);
            glVertex2f(epX[i] + s, epY[i]);
            glVertex2f(epX[i] + s, epY[i] + s);
            glVertex2f(epX[i], epY[i] + s);
        }
        glEnd();
    }

    /** A thin orbit around the percentage with one electron and a short fading trail. */
    private static void drawOrbitRing(float cx, float cy, float rx, float ry, float u, float t, float fade) {
        glBegin(GL_QUADS);
        glColor4f(ink[0], ink[1], ink[2], 0.30f * fade);
        int dots = 84;
        for (int i = 0; i < dots; i++) {
            double a = i * Math.PI * 2 / dots;
            float x = cx + (float) Math.cos(a) * rx - u, y = cy + (float) Math.sin(a) * ry - u;
            glVertex2f(x, y);
            glVertex2f(x + 2f * u, y);
            glVertex2f(x + 2f * u, y + 2f * u);
            glVertex2f(x, y + 2f * u);
        }
        glEnd();
        glBegin(GL_QUADS);
        for (int k = 7; k >= 0; k--) {
            double a = t * 2.2 - k * 0.11;
            float s = (k == 0 ? 6f : 4.2f - k * 0.3f) * u;
            float x = cx + (float) Math.cos(a) * rx - s / 2f, y = cy + (float) Math.sin(a) * ry - s / 2f;
            glColor4f(accentEnd[0], accentEnd[1], accentEnd[2], (k == 0 ? 1f : 0.55f - k * 0.06f) * fade);
            glVertex2f(x, y);
            glVertex2f(x + s, y);
            glVertex2f(x + s, y + s);
            glVertex2f(x, y + s);
        }
        glEnd();
    }
    private static float[] rgb(int c) {
        return new float[]{(c >> 16 & 0xFF) / 255f, (c >> 8 & 0xFF) / 255f, (c & 0xFF) / 255f};
    }

    private static float[] mix(float[] a, float[] b, float t) {
        return new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    private static float luminance(int c) {
        return ColorUtils.luminance(c);
    }

    /** Sets the bar to {@code fraction} (0 to 1) and the line under it to {@code text}. Safe from any thread. */
    public static void progress(float fraction, String text) {
        goal = fraction;
        message = text;
    }

    public static void start() {
        try {
            loadTheme();
            drawable = new SharedDrawable(Display.getDrawable());
            Display.getDrawable().releaseContext();
            drawable.makeCurrent();

            thread = new Thread(ArsenicSplash::run, "Arsenic Splash");
            thread.setUncaughtExceptionHandler((t, e) -> {
                e.printStackTrace();
                threadError = e;
            });
            enabled = true;
            thread.start();
        } catch (Throwable t) {
            t.printStackTrace();
            enabled = false;
            try { Display.getDrawable().makeCurrent(); } catch (LWJGLException ignored) {}
        }
    }

    public static void finish() {
        if (!enabled) return;
        enabled = false;
        try {
            done = true;
            thread.join();
            drawable.releaseContext();
            Display.getDrawable().makeCurrent();
            glDeleteTextures(logoTex);
            glDeleteTextures(fontTex);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void run() {
        setGL();
        loadLogo();
        buildFont();

        final long startTime = System.currentTimeMillis();
        float shown = 0f;
        long last = startTime;

        while (!done) {
            long now = System.currentTimeMillis();
            float dt = Math.min(0.1f, (now - last) / 1000f);
            last = now;

            float target = Math.min(1f, goal);
            String status = message;
            shown += (target - shown) * Math.min(1f, dt * 6f);

            int w = Display.getWidth(), h = Display.getHeight();
            float fade = Math.min(1f, (now - startTime) / 600f);
            draw(w, h, shown, status, fade);

            mutex.acquireUninterruptibly();
            Display.update();
            mutex.release();
            Display.sync(60);
        }
        clearGL();
    }

    private static void draw(int w, int h, float progress, String status, float fade) {
        glClear(GL_COLOR_BUFFER_BIT);
        glViewport(0, 0, w, h);
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, w, h, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        glDisable(GL_TEXTURE_2D);

        glBegin(GL_QUADS);
        glColor3f(bgTop[0], bgTop[1], bgTop[2]);
        glVertex2f(0, 0);
        glVertex2f(w, 0);
        glColor3f(bgBottom[0], bgBottom[1], bgBottom[2]);
        glVertex2f(w, h);
        glVertex2f(0, h);
        glEnd();

        float u = Math.max(0.5f, h / 720f);
        float cx = w / 2f;
        float cy = h * 0.40f;
        float t = (System.currentTimeMillis() % 100000L) / 1000f;

        if (element) drawElementLoad(w, h, progress, u, t, fade);
        else drawOcean(w, h, progress, u, t, fade);

        float lw = 420f * u, lh = lw * logoH / logoW;
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, logoTex);
        glColor4f(ink[0], ink[1], ink[2], fade);
        quad(cx - lw / 2, cy - lh / 2, lw, lh, 0, 0, 1, 1);
        glDisable(GL_TEXTURE_2D);

        float bw = 460f * u;
        String pct = Math.round(progress * 100) + "%";
        float pctSize = 44f * u;
        glColor4f(ink[0], ink[1], ink[2], 0.95f * fade);
        drawText(pct, cx - textWidth(pct, pctSize) / 2f, cy + lh / 2 + 26f * u, pctSize);
        float textSize = 22f * u;
        String shown = fit(status, bw, textSize);
        glColor4f(ink[0], ink[1], ink[2], 0.7f * fade);
        drawText(shown, cx - textWidth(shown, textSize) / 2f, cy + lh / 2 + 26f * u + pctSize + 8f * u, textSize);
        if (element) {
            float ringRx = Math.max(textWidth(pct, pctSize) / 2f + 28f * u, 50f * u);
            drawOrbitRing(cx, cy + lh / 2 + 26f * u + pctSize * 0.5f, ringRx, pctSize * 0.62f, u, t, fade);
        }

        glColor4f(1f, 1f, 1f, 1f);
    }



    private static final int BUBBLES = 56;
    private static float[] bubX, bubY, bubV, bubP, bubS;

    private static void initBubbles(int w, int h) {
        java.util.Random r = new java.util.Random(11);
        bubX = new float[BUBBLES];
        bubY = new float[BUBBLES];
        bubV = new float[BUBBLES];
        bubP = new float[BUBBLES];
        bubS = new float[BUBBLES];
        for (int i = 0; i < BUBBLES; i++) {
            bubX[i] = r.nextFloat() * w;
            bubY[i] = r.nextFloat() * h;
            bubV[i] = 20 + r.nextFloat() * 50;
            bubP[i] = r.nextFloat() * 6.28f;
            bubS[i] = 2 + r.nextInt(3);
        }
    }

    private static float waveY(float x, float surface, float amp, float wavelength, float speed, float t, float phase) {
        double k = Math.PI * 2 / wavelength;
        return surface + (float) (Math.sin(x * k + t * speed + phase) * amp + Math.sin(x * k * 1.9 - t * speed * 0.8 + phase * 1.7) * amp * 0.35);
    }

    private static void drawOcean(int w, int h, float progress, float u, float t, float fade) {
        if (bubX == null) initBubbles(w, h);
        float level = MathUtils.clamp01(progress);
        if (level <= 0.001f) return;
        float surface = h * (1f - level * 0.985f);
        float swell = Math.min(1f, level * 12f);

        float[] deep = mix(accent, new float[]{0f, 0f, 0f}, 0.6f);
        float[] light = mix(accent, new float[]{1f, 1f, 1f}, 0.3f);
        float[][] top = {mix(accent, deep, 0.3f), accent, light};
        float[] lift = {38f * u, 18f * u, 0f};
        float[] amps = {14f * u, 18f * u, 14f * u};
        float[] lens = {640f * u, 440f * u, 300f * u};
        float[] speeds = {-0.7f, 1.0f, -1.4f};
        float[] aTop = {0.22f, 0.32f, 0.42f};
        float[] aBot = {0.45f, 0.58f, 0.70f};
        int step = 2;
        for (int layer = 0; layer < 3; layer++) {
            float s = surface - lift[layer];
            float amp = amps[layer] * swell;
            float[] c1 = top[layer];
            float[] c2 = mix(c1, deep, 0.75f);
            glBegin(GL_QUADS);
            for (float x = 0; x < w; x += step) {
                float y1 = Math.min(h, waveY(x, s, amp, lens[layer], speeds[layer], t, layer * 1.9f));
                float y2 = Math.min(h, waveY(x + step, s, amp, lens[layer], speeds[layer], t, layer * 1.9f));
                glColor4f(c1[0], c1[1], c1[2], aTop[layer] * fade);
                glVertex2f(x, y1);
                glVertex2f(x + step, y2);
                glColor4f(c2[0], c2[1], c2[2], aBot[layer] * fade);
                glVertex2f(x + step, h);
                glVertex2f(x, h);
            }
            glEnd();
        }

        float amp = amps[2] * swell;
        glBegin(GL_QUADS);
        for (float x = 0; x < w; x += step) {
            float y1 = waveY(x, surface, amp, lens[2], speeds[2], t, 3.8f);
            float y2 = waveY(x + step, surface, amp, lens[2], speeds[2], t, 3.8f);
            glColor4f(1f, 1f, 1f, 0f);
            glVertex2f(x, y1 - 11f * u);
            glVertex2f(x + step, y2 - 11f * u);
            glColor4f(1f, 1f, 1f, 0.22f * fade);
            glVertex2f(x + step, y2);
            glVertex2f(x, y1);
        }
        glEnd();
        glBegin(GL_QUADS);
        glColor4f(1f, 1f, 1f, 0.55f * fade);
        for (float x = 0; x < w; x += step) {
            float y1 = waveY(x, surface, amp, lens[2], speeds[2], t, 3.8f);
            float y2 = waveY(x + step, surface, amp, lens[2], speeds[2], t, 3.8f);
            glVertex2f(x, y1 - 0.8f * u);
            glVertex2f(x + step, y2 - 0.8f * u);
            glVertex2f(x + step, y2 + 0.8f * u);
            glVertex2f(x, y1 + 0.8f * u);
        }
        glEnd();

        glBegin(GL_QUADS);
        for (int i = 0; i < BUBBLES; i++) {
            bubY[i] -= bubV[i] * 0.016f * u;
            float x = bubX[i] + (float) Math.sin(t * 1.4f + bubP[i]) * 8f * u;
            float wy = waveY(x, surface, amps[2] * swell, lens[2], speeds[2], t, 3.8f);
            if (bubY[i] < wy) {
                bubY[i] = h + 4;
                bubX[i] = (float) (Math.random() * w);
            }
            if (bubY[i] > h) continue;
            float s = bubS[i] * u;
            glColor4f(1f, 1f, 1f, 0.28f * fade);
            glVertex2f(x, bubY[i]);
            glVertex2f(x + s, bubY[i]);
            glVertex2f(x + s, bubY[i] + s);
            glVertex2f(x, bubY[i] + s);
        }
        glEnd();
    }

    private static void rect(float x, float y, float w, float h) {
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + w, y);
        glVertex2f(x + w, y + h);
        glVertex2f(x, y + h);
        glEnd();
    }

    private static void quad(float x, float y, float w, float h, float u0, float v0, float u1, float v1) {
        glBegin(GL_QUADS);
        glTexCoord2f(u0, v0);
        glVertex2f(x, y);
        glTexCoord2f(u1, v0);
        glVertex2f(x + w, y);
        glTexCoord2f(u1, v1);
        glVertex2f(x + w, y + h);
        glTexCoord2f(u0, v1);
        glVertex2f(x, y + h);
        glEnd();
    }


    private static int index(char c) {
        return (c < FIRST_CHAR || c > LAST_CHAR ? '?' : c) - FIRST_CHAR;
    }

    private static float textWidth(String s, float size) {
        float scale = size / FONT_BASE_SIZE, width = 0;
        for (int i = 0; i < s.length(); i++) width += glyphAdv[index(s.charAt(i))] * scale;
        return width;
    }

    private static String fit(String s, float maxWidth, float size) {
        if (textWidth(s, size) <= maxWidth) return s;
        while (s.length() > 1 && textWidth(s + "...", size) > maxWidth) s = s.substring(0, s.length() - 1);
        return s + "...";
    }

    private static void drawText(String s, float x, float y, float size) {
        float scale = size / FONT_BASE_SIZE;
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, fontTex);
        for (int i = 0; i < s.length(); i++) {
            int g = index(s.charAt(i));
            float cw = glyphCellW[g], u0 = glyphU[g] / fontW, v0 = glyphV[g] / fontH;
            quad(x, y, cw * scale, fontLineH * scale, u0, v0, u0 + cw / fontW, v0 + (float) fontLineH / fontH);
            x += glyphAdv[g] * scale;
        }
        glDisable(GL_TEXTURE_2D);
    }


    private static void loadLogo() {
        try (InputStream in = ArsenicSplash.class.getResourceAsStream("/assets/arsenic/logos/modern.png")) {
            BufferedImage img = ImageIO.read(in);
            logoW = img.getWidth();
            logoH = img.getHeight();
            logoTex = upload(img);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void buildFont() {
        try (InputStream in = ArsenicSplash.class.getResourceAsStream("/assets/arsenic/comfortaa.ttf")) {
            Font font = Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(Font.PLAIN, FONT_BASE_SIZE);

            BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D pg = probe.createGraphics();
            pg.setFont(font);
            FontMetrics fm = pg.getFontMetrics();
            fontLineH = fm.getHeight() + 2;
            int pad = 2, atlasW = 1024, x = pad, y = pad;
            for (int i = 0; i < glyphU.length; i++) {
                char c = (char) (FIRST_CHAR + i);
                glyphAdv[i] = fm.charWidth(c);
                glyphCellW[i] = glyphAdv[i] + 4;
                if (x + glyphCellW[i] + pad > atlasW) {
                    x = pad;
                    y += fontLineH + pad;
                }
                glyphU[i] = x;
                glyphV[i] = y;
                x += glyphCellW[i] + pad;
            }
            pg.dispose();

            fontW = atlasW;
            fontH = y + fontLineH + pad;
            BufferedImage atlas = new BufferedImage(fontW, fontH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = atlas.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setFont(font);
            g.setColor(Color.WHITE);
            for (int i = 0; i < glyphU.length; i++)
                g.drawString(String.valueOf((char) (FIRST_CHAR + i)), glyphU[i] + 2, glyphV[i] + fm.getAscent() + 1);
            g.dispose();
            fontTex = upload(atlas);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static int upload(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int[] argb = img.getRGB(0, 0, w, h, null, 0, w);
        IntBuffer buf = BufferUtils.createIntBuffer(argb.length);
        buf.put(argb).flip();
        int id;
        synchronized (ArsenicSplash.class) {
            id = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, id);
        }
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, buf);
        glBindTexture(GL_TEXTURE_2D, 0);
        return id;
    }


    private static void setGL() {
        lock.lock();
        try {
            Display.getDrawable().makeCurrent();
        } catch (LWJGLException e) {
            throw new RuntimeException(e);
        }
        glClearColor(bgTop[0], bgTop[1], bgTop[2], 1f);
        glDisable(GL_LIGHTING);
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    private static void clearGL() {
        Minecraft mc = Minecraft.getMinecraft();
        mc.displayWidth = Display.getWidth();
        mc.displayHeight = Display.getHeight();
        mc.resize(mc.displayWidth, mc.displayHeight);
        glColor4f(1f, 1f, 1f, 1f);
        glClearColor(1, 1, 1, 1);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_GREATER, .1f);
        try {
            Display.getDrawable().releaseContext();
        } catch (LWJGLException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }
}
