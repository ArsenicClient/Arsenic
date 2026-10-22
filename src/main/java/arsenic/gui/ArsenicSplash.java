package arsenic.gui;

import arsenic.gui.click.GuiStyle;
import arsenic.gui.themes.ThemeManager;
import arsenic.utils.font.TTFontRenderer;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.java.MathUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.QuadBatch;
import arsenic.utils.render.RenderContext;
import arsenic.utils.timer.FrameClock;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.InputStream;

/**
 * The loading screen, drawn in place of the game's own while resources load (see MixinLoadingOverlay).
 *
 * Two looks, following the menu style chosen in the ClickGUI (Appearance > Menu Style):
 * Element 33, a crystal lattice igniting from the bottom up with the load, and Ocean, water rising
 * with the load. Both carry the logo, the percentage and a status line in the theme's colours.
 *
 * Everything is drawn in window pixels (the GUI pose is scaled down by the GUI scale), so the
 * layout matches the 1.8 splash on every GUI scale.
 */
public final class ArsenicSplash {

    private static final Identifier LOGO = Identifier.fromNamespaceAndPath("arsenic", "splash_logo");
    private static boolean logoLoaded, logoFailed;
    private static int logoW = 4, logoH = 1;
    private static TTFontRenderer font;

    private static final FrameClock CLOCK = new FrameClock();
    private static float shown;
    private static long startedAt = -1;

    private static float[] bgTop, bgBottom, accent, accentEnd, ink;

    private ArsenicSplash() {}

    // ---- theme ------------------------------------------------------------------------------------

    private static void loadTheme() {
        int main = 0xDD425E, back = 0x494949;      // Classic, the default theme
        try {
            main = ThemeManager.getMainColor() & 0xFFFFFF;
            back = ThemeManager.getBlack() & 0xFFFFFF;
        } catch (Throwable ignored) { }

        boolean light = ColorUtils.luminance(back) > 0.6f;
        float[] bg = rgb(back), acc = rgb(main);
        bgTop = light ? bg : mix(bg, new float[]{0, 0, 0}, 0.35f);
        bgBottom = mix(bg, acc, light ? 0.10f : 0.14f);
        accent = acc;
        accentEnd = mix(acc, light ? new float[]{0, 0, 0} : new float[]{1, 1, 1}, 0.35f);
        ink = light ? new float[]{0.07f, 0.07f, 0.09f} : new float[]{1f, 1f, 1f};
        if (Math.abs(ColorUtils.luminance(main) - ColorUtils.luminance(back)) < 0.15f) {
            accent = ink;
            accentEnd = mix(ink, bg, 0.4f);
        }
    }

    private static float[] rgb(int c) {
        return new float[]{(c >> 16 & 0xFF) / 255f, (c >> 8 & 0xFF) / 255f, (c & 0xFF) / 255f};
    }

    private static float[] mix(float[] a, float[] b, float t) {
        return new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    private static int argb(float[] c, float a) {
        return MathUtils.clamp(Math.round(a * 255f), 0, 255) << 24
                | Math.round(MathUtils.clamp01(c[0]) * 255f) << 16
                | Math.round(MathUtils.clamp01(c[1]) * 255f) << 8
                | Math.round(MathUtils.clamp01(c[2]) * 255f);
    }

    /** The background colour, for the frame's clear colour. */
    public static int background() {
        if (bgTop == null) loadTheme();
        return argb(bgTop, 1f);
    }

    // ---- resources --------------------------------------------------------------------------------

    /** The logo comes straight from the jar: the resource manager is mid-reload while this draws. */
    private static void loadLogo() {
        if (logoLoaded || logoFailed) return;
        try (InputStream in = ArsenicSplash.class.getResourceAsStream("/assets/arsenic/logos/modern.png")) {
            NativeImage image = NativeImage.read(in);
            logoW = image.getWidth();
            logoH = image.getHeight();
            Minecraft.getInstance().getTextureManager().register(LOGO, new DynamicTexture(() -> "Arsenic splash logo", image));
            logoLoaded = true;
        } catch (Throwable t) {
            logoFailed = true;
        }
    }

    private static TTFontRenderer font() {
        if (font == null)
            font = new TTFontRenderer("comfortaa.ttf", 64);    // 32 GUI units, scaled to size
        return font;
    }

    // ---- frame ------------------------------------------------------------------------------------

    /**
     * Draws one frame of the splash.
     *
     * @param progress the load, 0 to 1
     * @param status   the line under the percentage
     * @param fade     the splash's opacity, for the fade into the game
     */
    public static void draw(GuiGraphicsExtractor graphics, float progress, String status, float fade) {
        if (startedAt < 0) {
            startedAt = System.currentTimeMillis();
            loadTheme();
        }
        loadLogo();
        float dt = CLOCK.tick();
        shown += (Math.min(1f, progress) - shown) * Math.min(1f, dt * 15f);

        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight();
        float guiScale = (float) graphics.guiWidth() / Math.max(1, w);

        graphics.nextStratum();
        try (RenderContext ignored = RenderContext.begin(graphics)) {
            graphics.pose().pushMatrix();
            graphics.pose().scale(guiScale, guiScale);
            try {
                drawFrame(w, h, shown, status, fade, dt);
            } finally {
                graphics.pose().popMatrix();
            }
        }
    }

    /** Called when the overlay is gone, so a later reload starts its fade-in afresh. */
    public static void reset() {
        startedAt = -1;
        shown = 0f;
    }

    private static void drawFrame(int w, int h, float progress, String status, float fade, float dt) {
        // the background follows only the fade into the game; the content also fades in at the start
        DrawUtils.drawVerticalGradient(0, 0, w, h, argb(bgTop, fade), argb(bgBottom, fade));

        float u = Math.max(0.5f, h / 720f);
        float cx = w / 2f;
        float cy = h * 0.40f;
        float t = (System.currentTimeMillis() % 100000L) / 1000f;
        float content = fade * Math.min(1f, (System.currentTimeMillis() - startedAt) / 600f);

        if (GuiStyle.element()) drawElementLoad(w, h, progress, u, t, content, dt);
        else drawOcean(w, h, progress, u, t, content, dt);

        float lw = 420f * u, lh = lw * logoH / logoW;
        if (logoLoaded)
            DrawUtils.drawTexture(LOGO, cx - lw / 2, cy - lh / 2, lw, lh, argb(ink, content));

        String pct = Math.round(progress * 100) + "%";
        float pctSize = 44f * u;
        drawText(pct, cx - textWidth(pct, pctSize) / 2f, cy + lh / 2 + 26f * u, pctSize, argb(ink, 0.95f * content));
        float textSize = 22f * u;
        String line = fit(status, 460f * u, textSize);
        drawText(line, cx - textWidth(line, textSize) / 2f, cy + lh / 2 + 26f * u + pctSize + 8f * u, textSize, argb(ink, 0.7f * content));
        if (GuiStyle.element()) {
            float ringRx = Math.max(textWidth(pct, pctSize) / 2f + 28f * u, 50f * u);
            drawOrbitRing(cx, cy + lh / 2 + 26f * u + pctSize * 0.5f, ringRx, pctSize * 0.62f, u, t, content);
        }
    }

    // ---- text -------------------------------------------------------------------------------------

    /** Text at a point size of {@code size} pixels: the font is a 32 unit em, scaled to it. */
    private static float textScale(float size) {
        return size / 32f;
    }

    private static float textWidth(String s, float size) {
        return font().getWidth(s) * textScale(size);
    }

    private static String fit(String s, float maxWidth, float size) {
        if (textWidth(s, size) <= maxWidth) return s;
        while (s.length() > 1 && textWidth(s + "...", size) > maxWidth) s = s.substring(0, s.length() - 1);
        return s + "...";
    }

    private static void drawText(String s, float x, float y, float size, int colour) {
        if ((colour >>> 24) < 4) return;          // the font reads an alpha under 4 as opaque
        float k = textScale(size);
        var pose = RenderContext.graphics().pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(k, k);
        font().drawString(s, 0, 0, colour);
        pose.popMatrix();
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

    private static void square(QuadBatch b, float x, float y, float s, int colour) {
        b.rect(x, y, x + s, y + s, colour);
    }

    /** One stepped line of small squares. */
    private static void pixLine(QuadBatch b, float x0, float y0, float x1, float y1, float size, int colour) {
        float dx = x1 - x0, dy = y1 - y0;
        int steps = (int) Math.max(1, Math.max(Math.abs(dx), Math.abs(dy)) / (size * 1.5f));
        for (int i = 0; i <= steps; i++) {
            float k = i / (float) steps;
            square(b, x0 + dx * k - size / 2f, y0 + dy * k - size / 2f, size, colour);
        }
    }

    /** A flat-topped hexagon filled with horizontal runs, so the edge is stepped like pixel art. */
    private static void hexFill(QuadBatch b, float cx, float cy, float r, float rowStep, int colour) {
        float hh = r * 0.866f;
        for (float dy = -hh; dy < hh; dy += rowStep) {
            float mid = Math.abs(dy + rowStep / 2f);
            float half = r - mid / 1.732f;
            b.rect(cx - half, cy + dy, cx + half, cy + dy + rowStep, colour);
        }
    }

    private static void drawElementLoad(int w, int h, float progress, float u, float t, float fade, float dt) {
        float R = 26f * u, colW = R * 1.5f, rowH = R * 1.732f;
        float level = MathUtils.clamp01(progress);
        int cols = (int) (w / colW) + 3;
        int rows = (int) (h / rowH) + 3;
        float front = level * (rows + 2);                   // how many rows have lit, counting from the bottom
        float frontY = h - level * h * 0.985f;

        // the faint lattice, drifting slowly, over the whole screen
        QuadBatch lattice = new QuadBatch();
        int latticeColour = argb(accent, 0.075f * fade);
        float dx = (t * 6f * u) % (colW * 2f), dy = (t * 3f * u) % rowH;
        for (int c = -2; c < cols; c++) {
            for (int r = -1; r < rows; r++) {
                float cx = c * colW - dx, cy = r * rowH - dy + ((c & 1) != 0 ? rowH / 2f : 0f);
                float hh = R * 0.866f;
                pixLine(lattice, cx - R / 2, cy - hh, cx + R / 2, cy - hh, 2f * u, latticeColour);
                pixLine(lattice, cx - R, cy, cx - R / 2, cy - hh, 2f * u, latticeColour);
                pixLine(lattice, cx + R, cy, cx + R / 2, cy - hh, 2f * u, latticeColour);
            }
        }
        lattice.submit();

        // the block holding the logo, percentage and status: cells behind it are dimmed so the text keeps its contrast
        float lhLogo = 420f * u * logoH / logoW;
        float tl = w / 2f - 250f * u - R, tr = w / 2f + 250f * u + R;
        float tt = h * 0.40f - lhLogo / 2f - R, tb = h * 0.40f + lhLogo / 2f + 130f * u + R;

        // the lit cells: dim and dark behind the front, bright and saturated at it
        QuadBatch cells = new QuadBatch();
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
                hexFill(cells, cx, cy, R * 0.9f * grow, 3f * u, argb(mix(accent, accentEnd, heat), a));
            }
        }
        cells.submit();

        // the growth front: a soft band of light with a thin bright line in the middle
        if (level > 0.002f && level < 0.999f) {
            QuadBatch band = new QuadBatch();
            int clear = argb(accentEnd, 0f), lit = argb(accentEnd, 0.26f * fade);
            band.quad(0, frontY - 26f * u, clear, w, frontY - 26f * u, clear, w, frontY, lit, 0, frontY, lit);
            band.quad(0, frontY, lit, w, frontY, lit, w, frontY + 22f * u, clear, 0, frontY + 22f * u, clear);
            // the line is cut into dashes that flicker, so it reads as crystal edges, not a ruler
            for (int c = 0; c < cols; c++) {
                float flick = 0.45f + 0.55f * cellHash(c, (int) (t * 7f));
                float x = c * colW - 4f;
                band.rect(x, frontY - 1.2f * u, x + colW * 0.8f, frontY + 1.2f * u, argb(new float[]{1, 1, 1}, 0.55f * flick * fade));
            }
            band.submit();

            // sparks pop off the front, vapour rises from the lit cells
            for (int k = 0; k < 2; k++)
                emitPart((float) (Math.random() * w), frontY, (float) (Math.random() - 0.5) * 50f * u,
                        -(40f + (float) Math.random() * 80f) * u, 0.7f + (float) Math.random() * 0.6f, false);
            if (Math.random() < 0.7)
                emitPart((float) (Math.random() * w), frontY + (float) Math.random() * (h - frontY),
                        (float) (Math.random() - 0.5) * 8f * u, -(16f + (float) Math.random() * 14f) * u,
                        2f + (float) Math.random() * 1.8f, true);
        }

        QuadBatch parts = new QuadBatch();
        for (int i = 0; i < E_PARTS; i++) {
            if (epLife[i] <= 0f) continue;
            epLife[i] -= dt;
            epX[i] += epVX[i] * dt;
            epY[i] += epVY[i] * dt;
            if (!epVapour[i]) epVY[i] += 70f * u * dt;
            float k = Math.max(0f, epLife[i] / epMax[i]);
            float s = (epVapour[i] ? 4.5f + (1f - k) * 6f : 2.2f) * u;
            int colour = epVapour[i] ? argb(accentEnd, k * 0.16f * fade) : argb(new float[]{1, 1, 1}, k * 0.95f * fade);
            square(parts, epX[i], epY[i], s, colour);
        }
        parts.submit();
    }

    /** A thin orbit around the percentage with one electron and a short fading trail. */
    private static void drawOrbitRing(float cx, float cy, float rx, float ry, float u, float t, float fade) {
        QuadBatch b = new QuadBatch();
        int dotColour = argb(ink, 0.30f * fade);
        int dots = 84;
        for (int i = 0; i < dots; i++) {
            double a = i * Math.PI * 2 / dots;
            square(b, cx + (float) Math.cos(a) * rx - u, cy + (float) Math.sin(a) * ry - u, 2f * u, dotColour);
        }
        for (int k = 7; k >= 0; k--) {
            double a = t * 2.2 - k * 0.11;
            float s = (k == 0 ? 6f : 4.2f - k * 0.3f) * u;
            square(b, cx + (float) Math.cos(a) * rx - s / 2f, cy + (float) Math.sin(a) * ry - s / 2f, s,
                    argb(accentEnd, (k == 0 ? 1f : 0.55f - k * 0.06f) * fade));
        }
        b.submit();
    }

    // ---- the Ocean loading screen -----------------------------------------------------------------

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

    private static void drawOcean(int w, int h, float progress, float u, float t, float fade, float dt) {
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
        float step = 2f * u;
        for (int layer = 0; layer < 3; layer++) {
            float s = surface - lift[layer];
            float amp = amps[layer] * swell;
            int c1 = argb(top[layer], aTop[layer] * fade);
            int c2 = argb(mix(top[layer], deep, 0.75f), aBot[layer] * fade);
            QuadBatch b = new QuadBatch();
            for (float x = 0; x < w; x += step) {
                float y1 = Math.min(h, waveY(x, s, amp, lens[layer], speeds[layer], t, layer * 1.9f));
                float y2 = Math.min(h, waveY(x + step, s, amp, lens[layer], speeds[layer], t, layer * 1.9f));
                b.quad(x, y1, c1, x + step, y2, c1, x + step, h, c2, x, h, c2);
            }
            b.submit();
        }

        float amp = amps[2] * swell;
        QuadBatch crest = new QuadBatch();
        int white0 = argb(new float[]{1, 1, 1}, 0f), glow = argb(new float[]{1, 1, 1}, 0.22f * fade),
                line = argb(new float[]{1, 1, 1}, 0.55f * fade);
        for (float x = 0; x < w; x += step) {
            float y1 = waveY(x, surface, amp, lens[2], speeds[2], t, 3.8f);
            float y2 = waveY(x + step, surface, amp, lens[2], speeds[2], t, 3.8f);
            crest.quad(x, y1 - 11f * u, white0, x + step, y2 - 11f * u, white0, x + step, y2, glow, x, y1, glow);
            crest.quad(x, y1 - 0.8f * u, x + step, y2 - 0.8f * u, x + step, y2 + 0.8f * u, x, y1 + 0.8f * u, line);
        }
        crest.submit();

        QuadBatch bubbles = new QuadBatch();
        int bubble = argb(new float[]{1, 1, 1}, 0.28f * fade);
        for (int i = 0; i < BUBBLES; i++) {
            bubY[i] -= bubV[i] * dt * u;
            float x = bubX[i] + (float) Math.sin(t * 1.4f + bubP[i]) * 8f * u;
            float wy = waveY(x, surface, amps[2] * swell, lens[2], speeds[2], t, 3.8f);
            if (bubY[i] < wy) {
                bubY[i] = h + 4;
                bubX[i] = (float) (Math.random() * w);
            }
            if (bubY[i] > h) continue;
            square(bubbles, x, bubY[i], bubS[i] * u, bubble);
        }
        bubbles.submit();
    }
}
