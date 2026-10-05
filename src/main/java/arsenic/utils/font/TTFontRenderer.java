package arsenic.utils.font;

import arsenic.utils.interfaces.IFontRenderer;
import arsenic.utils.render.RenderContext;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A TrueType font rasterised by the client itself, at the size it is actually shown.
 * <p>
 * Every string is measured in GUI units with exactly the metrics the 1.8 renderer used (advance,
 * line height and baseline), so layouts and the centring tuned against them carry over unchanged.
 * Drawing then picks a {@link GlyphAtlas} whose pixel size matches the text's on-screen size - GUI
 * scale times any pose scaling - and snaps each glyph to the screen's pixel grid, so text is crisp
 * at every size instead of a stretched fixed-size bitmap. Legacy {@code §} colour codes work.
 */
public class TTFontRenderer implements IFontRenderer {

    private static final char FORMAT = '§';
    /** How long an atlas for a pixel size nobody draws at any more is kept before it is freed. */
    private static final long ATLAS_IDLE_MS = 5000;
    private static final int MAX_ATLASES = 12;
    private static final int[] COLOR_CODES = new int[32];

    static {
        for (int i = 0; i < 32; i++) {
            int shade = (i >> 3 & 1) * 85;
            int r = (i >> 2 & 1) * 170 + shade, g = (i >> 1 & 1) * 170 + shade, b = (i & 1) * 170 + shade;
            if (i == 6) r += 85;
            if (i >= 16) {
                r /= 4;
                g /= 4;
                b /= 4;
            }
            COLOR_CODES[i] = (r & 255) << 16 | (g & 255) << 8 | b & 255;
        }
    }

    private final Font baseFont;
    private final String name;
    /** The font's size in GUI units: 1.8 rasterised at {@code baseSize} pixels and drew at half scale. */
    private final float emSize;
    private final float height;
    /** Baseline offset from the y a string is drawn at, as 1.8 placed it. */
    private final float baseline;
    private final FontMetrics baseMetrics;
    private final Map<Integer, Float> advances = new HashMap<>();
    private final Map<Integer, GlyphAtlas> atlases = new HashMap<>();
    private final FontRendererExtension<TTFontRenderer> extension = new FontRendererExtension<>(this);

    /**
     * @param file     TTF file under {@code assets/arsenic/font/}
     * @param baseSize the pixel size the 1.8 client rasterised it at (text is half that in GUI units)
     */
    public TTFontRenderer(String file, int baseSize) {
        this.name = file;
        this.baseFont = load(file).deriveFont((float) baseSize);
        this.emSize = baseSize / 2f;
        // 1.8 measured with a plain Graphics2D (integer metrics) and laid each glyph out in a cell
        // with a 6px margin; height and baseline below are that cell's, halved into GUI units
        Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        g.setFont(baseFont);
        this.baseMetrics = g.getFontMetrics();
        float cellHeight = (float) Math.ceil(baseMetrics.getStringBounds("A", g).getHeight());
        g.dispose();
        this.height = (cellHeight - 6) / 2f;
        this.baseline = -2 + baseMetrics.getAscent() / 2f;
    }

    private static Font load(String file) {
        try (InputStream in = TTFontRenderer.class.getResourceAsStream("/assets/arsenic/font/" + file)) {
            if (in == null)
                throw new IllegalStateException("Missing font " + file);
            return Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (Exception e) {
            throw new IllegalStateException("Could not load font " + file, e);
        }
    }

    @Override
    public FontRendererExtension<?> getFontRendererExtension() {
        return extension;
    }

    @Override
    public void drawString(String text, float x, float y, int color) {
        render(text, x, y, color, false);
    }

    @Override
    public void drawStringWithShadow(String text, float x, float y, int color) {
        render(text, x + 0.5f, y + 0.5f, color, true);
        render(text, x, y, color, false);
    }

    @Override
    public float getWidth(String text) {
        if (text == null || text.isEmpty())
            return 0;
        float width = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (cp == FORMAT) {
                i += 2;
                continue;
            }
            width += advance(cp);
            i += Character.charCount(cp);
        }
        return width;
    }

    @Override
    public float getHeight(String text) {
        return height;
    }

    /** 1.8's per-character advance: the integer advance at the base size, halved. */
    private float advance(int codePoint) {
        Float advance = advances.get(codePoint);
        if (advance == null) {
            advance = (float) Math.ceil(baseMetrics.charWidth(codePoint)) / 2f;
            advances.put(codePoint, advance);
        }
        return advance;
    }

    private void render(String text, float x, float y, int color, boolean shadow) {
        if (text == null || text.isEmpty())
            return;
        GuiGraphicsExtractor graphics = RenderContext.graphics();
        Matrix3x2f pose = new Matrix3x2f(graphics.pose());
        int guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        float poseScale = (float) Math.sqrt(pose.m00() * pose.m00() + pose.m01() * pose.m01());
        float deviceScale = Math.max(0.01f, poseScale * guiScale);
        GlyphAtlas atlas = atlas(Math.round(emSize * deviceScale));
        // raster pixels to GUI units
        float unit = emSize / atlas.pixelSize;
        boolean snap = Math.abs(pose.m01()) < 1e-4f && Math.abs(pose.m10()) < 1e-4f;

        color = VanillaFontRenderer.fixColor(color);
        int alpha = color & 0xFF000000;
        int base = shadow ? shadowOf(color) : color;
        int current = base;

        // rasterise first so every page is uploaded before its quads are queued
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (cp == FORMAT) {
                i += 2;
                continue;
            }
            atlas.glyph(cp);
            i += Character.charCount(cp);
        }
        atlas.upload();

        Map<GlyphAtlas.Page, Quads> byPage = new HashMap<>();
        float penX = x;
        float baseY = y + baseline;
        if (snap)
            baseY = snap(baseY, pose.m11(), pose.m21(), guiScale);
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (cp == FORMAT) {
                if (i + 1 < text.length())
                    current = formatColor(Character.toLowerCase(text.charAt(i + 1)), current, base, alpha, shadow);
                i += 2;
                continue;
            }
            i += Character.charCount(cp);
            GlyphAtlas.Glyph glyph = atlas.glyph(cp);
            if (!glyph.isEmpty()) {
                float gx = penX + glyph.offsetX() * unit;
                if (snap)
                    gx = snap(gx, pose.m00(), pose.m20(), guiScale);
                float gy = baseY + glyph.offsetY() * unit;
                byPage.computeIfAbsent(glyph.page(), p -> new Quads())
                        .add(gx, gy, gx + glyph.width() * unit, gy + glyph.height() * unit,
                                glyph.u0(), glyph.v0(), glyph.u1(), glyph.v1(), RenderContext.applyAlpha(current));
            }
            penX += advance(cp);
        }

        ScreenRectangle scissor = graphics.scissorStack.peek();
        for (Map.Entry<GlyphAtlas.Page, Quads> entry : byPage.entrySet())
            graphics.guiRenderState.addGuiElement(entry.getValue().toState(entry.getKey().setup, pose, scissor));
    }

    /** Moves a GUI-unit coordinate onto the nearest screen pixel, given the pose's scale and offset on that axis. */
    private static float snap(float v, float scale, float offset, int guiScale) {
        if (Math.abs(scale) < 1e-4f)
            return v;
        float device = Math.round((scale * v + offset) * guiScale);
        return (device / guiScale - offset) / scale;
    }

    private static int shadowOf(int color) {
        int r = (color >> 16 & 255) / 4, g = (color >> 8 & 255) / 4, b = (color & 255) / 4;
        return color & 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int formatColor(char code, int current, int base, int alpha, boolean shadow) {
        int index = "0123456789abcdef".indexOf(code);
        if (index >= 0)
            return alpha | COLOR_CODES[index + (shadow ? 16 : 0)];
        return code == 'r' ? base : current;
    }

    private GlyphAtlas atlas(int pixelSize) {
        pixelSize = Math.max(4, Math.min(256, pixelSize));
        long now = System.currentTimeMillis();
        GlyphAtlas atlas = atlases.get(pixelSize);
        if (atlas == null) {
            evictIdle(now);
            atlas = new GlyphAtlas(baseFont, name, pixelSize);
            atlases.put(pixelSize, atlas);
        }
        atlas.lastUsed = now;
        return atlas;
    }

    /** Frees atlases for sizes not drawn recently (e.g. the in-between sizes of a zoom animation). */
    private void evictIdle(long now) {
        if (atlases.size() < MAX_ATLASES)
            return;
        Iterator<GlyphAtlas> it = atlases.values().iterator();
        while (it.hasNext()) {
            GlyphAtlas atlas = it.next();
            if (now - atlas.lastUsed > ATLAS_IDLE_MS) {
                atlas.close();
                it.remove();
            }
        }
    }

    /** Glyph quads that share a texture page, collected into one GUI element. */
    private static final class Quads {
        private final List<float[]> quads = new ArrayList<>();
        private final List<Integer> colors = new ArrayList<>();
        private float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;

        void add(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, int color) {
            quads.add(new float[]{x0, y0, x1, y1, u0, v0, u1, v1});
            colors.add(color);
            minX = Math.min(minX, x0);
            minY = Math.min(minY, y0);
            maxX = Math.max(maxX, x1);
            maxY = Math.max(maxY, y1);
        }

        State toState(TextureSetup setup, Matrix3x2f pose, @Nullable ScreenRectangle scissor) {
            int bx = (int) Math.floor(minX), by = (int) Math.floor(minY);
            ScreenRectangle bounds = new ScreenRectangle(bx, by,
                    (int) Math.ceil(maxX) - bx + 1, (int) Math.ceil(maxY) - by + 1).transformMaxBounds(pose);
            if (scissor != null)
                bounds = scissor.intersection(bounds);
            int[] c = new int[colors.size()];
            for (int i = 0; i < c.length; i++)
                c[i] = colors.get(i);
            return new State(quads.toArray(new float[0][]), c, setup, pose, scissor, bounds);
        }
    }

    private record State(float[][] quads, int[] colors, TextureSetup textureSetup, Matrix3x2fc pose,
                         @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds)
            implements GuiElementRenderState {

        @Override
        public void buildVertices(VertexConsumer consumer) {
            for (int i = 0; i < quads.length; i++) {
                float[] q = quads[i];
                int color = colors[i];
                consumer.addVertexWith2DPose(pose, q[0], q[1]).setUv(q[4], q[5]).setColor(color);
                consumer.addVertexWith2DPose(pose, q[0], q[3]).setUv(q[4], q[7]).setColor(color);
                consumer.addVertexWith2DPose(pose, q[2], q[3]).setUv(q[6], q[7]).setColor(color);
                consumer.addVertexWith2DPose(pose, q[2], q[1]).setUv(q[6], q[5]).setColor(color);
            }
        }

        @Override
        public RenderPipeline pipeline() {
            return RenderPipelines.GUI_TEXTURED;
        }
    }
}
