package arsenic.utils.font;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import arsenic.utils.interfaces.IFontRenderer;

/**
 * A TrueType font rasterised at the size it is actually shown.
 * <p>
 * Strings are measured exactly as before (the advance, height and baseline of the font rasterised
 * at its base size and drawn at half scale), so every layout and the centring tuned against them is
 * unchanged. Drawing reads the current projection and modelview matrices to work out how many
 * screen pixels the text covers, rasterises the glyphs at that pixel size into an atlas, and snaps
 * each glyph to the pixel grid - so text is sharp at every GUI scale and zoom instead of a 17px
 * bitmap stretched or squashed into place.
 */
public class TTFontRenderer implements IFontRenderer {

    private static final char COLOR_INVOKER = '\247';
    private static final Random RANDOM = new Random();
    /** The cell margin the original renderer laid glyphs out with, in base-size pixels. */
    private static final int MARGIN = 6;
    private static final long ATLAS_IDLE_MS = 5000;
    private static final int MAX_ATLASES = 12;

    private final Font font;
    private final boolean antiAlias;
    private final boolean fracMetrics;
    private final int[] colorCodes = new int[32];
    /** Size of the font in GUI units (it was rasterised at twice this and drawn at half scale). */
    private final float emSize;

    // metrics at the base size, in GUI units
    private final Map<Integer, Float> advances = new HashMap<>();
    private FontMetrics baseMetrics;
    private float height;
    private float baseline;
    private float cellHeight;

    private final Map<Integer, Atlas> atlases = new HashMap<>();
    private final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);
    private final float[] projection = new float[16];
    private final float[] modelview = new float[16];

    private final FontRendererExtension<TTFontRenderer> fontRendererExtension = new FontRendererExtension<>(this);

    public TTFontRenderer(Font font, boolean antiAlias, boolean fracMetrics) {
        generateColors();
        this.font = font;
        this.antiAlias = antiAlias;
        this.fracMetrics = fracMetrics;
        this.emSize = font.getSize2D() / 2f;
        measure();
    }

    /**
     * Glyphs are rasterised on first use at whatever size they are drawn, so there is nothing to
     * build up front; kept so existing start-up code still works.
     */
    public void generateTextures() {
    }

    /** The original renderer's metrics: a plain Graphics2D, so integer advances and ascent. */
    private void measure() {
        Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        g.setFont(font);
        baseMetrics = g.getFontMetrics();
        Rectangle2D bounds = baseMetrics.getStringBounds("A", g);
        g.dispose();
        cellHeight = (float) Math.ceil(bounds.getHeight());
        height = (cellHeight - MARGIN) / 2f;
        // glyphs were drawn 2 units above y, with the baseline at the ascent in base pixels
        baseline = -2 + baseMetrics.getAscent() / 2f;
    }

    @Override
    public FontRendererExtension<?> getFontRendererExtension() {
        return fontRendererExtension;
    }

    @Override
    public void drawString(String text, float x, float y, int color) {
        renderString(text, x, y, color, false);
    }

    @Override
    public void drawStringWithShadow(String text, float x, float y, int color) {
        renderString(text, x + 0.5f, y + 0.5f, color, true);
        renderString(text, x, y, color, false);
    }

    @Override
    public float getWidth(String text) {
        if (text == null || text.length() == 0)
            return 0;
        float width = 0;
        int length = text.length();
        for (int i = 0; i < length; i++) {
            char character = text.charAt(i);
            if (character == COLOR_INVOKER || (i > 0 ? text.charAt(i - 1) : '.') == COLOR_INVOKER
                    || !isValid(character))
                continue;
            width += advance(character);
        }
        return width;
    }

    @Override
    public float getHeight(@NotNull String text) {
        return height;
    }

    /** The original per-character advance: the glyph's cell width less its margins, halved. */
    private float advance(char character) {
        Float advance = advances.get((int) character);
        if (advance == null) {
            advance = (float) Math.ceil(baseMetrics.charWidth(character)) / 2f;
            advances.put((int) character, advance);
        }
        return advance;
    }

    private void renderString(CharSequence text, float x, float y, int color, boolean shadow) {
        if (text == null || text.length() == 0)
            return;

        Transform transform = currentTransform();
        Atlas atlas = atlas(Math.round(emSize * transform.pixelsPerUnit));
        float unit = emSize / atlas.pixelSize;

        // rasterise everything first so all uploads happen before drawing starts
        int length = text.length();
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if (c != COLOR_INVOKER && (i == 0 || text.charAt(i - 1) != COLOR_INVOKER) && isValid(c))
                atlas.glyph(c);
        }

        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT
                | GL11.GL_TEXTURE_BIT | GL11.GL_LINE_BIT);

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);

        if ((color & 0xFC000000) == 0) { color |= 0xFF000000; }
        if (color == 0x20FFFFFF) { color = 0xFFAFAFAF; }

        float multiplier = (shadow ? 4 : 1);
        float a = (float) (color >> 24 & 255) / 255.0F;
        float r = (float) (color >> 16 & 255) / 255.0F;
        float g = (float) (color >> 8 & 255) / 255.0F;
        float b = (float) (color & 255) / 255.0F;
        GL11.glColor4f(r / multiplier, g / multiplier, b / multiplier, a);

        boolean underlined = false;
        boolean strikethrough = false;
        boolean obfuscated = false;

        float baseY = transform.snapY(y + baseline);
        int boundTexture = -1;
        boolean drawing = false;

        for (int i = 0; i < length; i++) {
            char character = text.charAt(i);
            char previous = i > 0 ? text.charAt(i - 1) : '.';
            if (previous == COLOR_INVOKER)
                continue;
            if (character == COLOR_INVOKER) {
                if (i + 1 >= length)
                    break;
                int index = "0123456789abcdefklmnor".indexOf(Character.toLowerCase(text.charAt(i + 1)));
                if (drawing) {
                    GL11.glEnd();
                    drawing = false;
                }
                if (index < 16) {
                    obfuscated = false;
                    strikethrough = false;
                    underlined = false;
                    if (index < 0)
                        index = 15;
                    if (shadow)
                        index += 16;
                    int textColor = this.colorCodes[index];
                    GL11.glColor4f((textColor >> 16) / 255.0F, (textColor >> 8 & 255) / 255.0F, (textColor & 255) / 255.0F, a);
                } else if (index == 16)
                    obfuscated = true;
                else if (index == 18)
                    strikethrough = true;
                else if (index == 19)
                    underlined = true;
                else {
                    obfuscated = false;
                    strikethrough = false;
                    underlined = false;
                    GL11.glColor4d(1 / multiplier, 1 / multiplier, 1 / multiplier, a);
                }
                continue;
            }
            if (!isValid(character))
                continue;

            float advance = advance(character);
            if (obfuscated) {
                char swapped = (char) (character + RANDOM.nextInt(Math.max(1, 256 - character)));
                if (isValid(swapped)) {
                    character = swapped;
                    atlas.glyph(character);
                }
            }

            Glyph glyph = atlas.glyph(character);
            if (glyph != null) {
                if (glyph.texture != boundTexture) {
                    if (drawing) {
                        GL11.glEnd();
                        drawing = false;
                    }
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, glyph.texture);
                    boundTexture = glyph.texture;
                }
                if (!drawing) {
                    GL11.glBegin(GL11.GL_QUADS);
                    drawing = true;
                }
                float gx = transform.snapX(x + glyph.offsetX * unit);
                float gy = baseY + glyph.offsetY * unit;
                float gw = glyph.width * unit, gh = glyph.height * unit;
                GL11.glTexCoord2f(glyph.u0, glyph.v0);
                GL11.glVertex2f(gx, gy);
                GL11.glTexCoord2f(glyph.u0, glyph.v1);
                GL11.glVertex2f(gx, gy + gh);
                GL11.glTexCoord2f(glyph.u1, glyph.v1);
                GL11.glVertex2f(gx + gw, gy + gh);
                GL11.glTexCoord2f(glyph.u1, glyph.v0);
                GL11.glVertex2f(gx + gw, gy);
            }

            if (strikethrough || underlined) {
                if (drawing) {
                    GL11.glEnd();
                    drawing = false;
                }
                // the same rows the original drew its lines at, in GUI units
                float lineY = strikethrough ? y - 2 + cellHeight / 4f : y - 2 + (cellHeight - 15) / 2f;
                drawLine(x, lineY, x + advance, lineY, 3);
            }
            x += advance;
        }
        if (drawing)
            GL11.glEnd();

        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    private boolean isValid(char c) {
        return c > 10 && c < 256 && c != 127;
    }

    private void drawLine(float x, float y, float x2, float y2, float width) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glLineWidth(width);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x2, y2);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private void generateColors() {
        for (int i = 0; i < 32; i++) {
            int thingy = (i >> 3 & 1) * 85;
            int red = (i >> 2 & 1) * 170 + thingy;
            int green = (i >> 1 & 1) * 170 + thingy;
            int blue = (i & 1) * 170 + thingy;
            if (i == 6)
                red += 85;
            if (i >= 16) {
                red /= 4;
                green /= 4;
                blue /= 4;
            }
            this.colorCodes[i] = (red & 255) << 16 | (green & 255) << 8 | blue & 255;
        }
    }

    // ---- where text lands on screen ------------------------------------------------------------

    /**
     * Reads projection x modelview to find the text's size in screen pixels and to snap glyphs to
     * the pixel grid. Works whatever transforms are active, including the ClickGUI's own projection.
     */
    private Transform currentTransform() {
        matrixBuffer.clear();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, matrixBuffer);
        matrixBuffer.get(projection);
        matrixBuffer.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, matrixBuffer);
        matrixBuffer.get(modelview);
        // rows 0, 1 and 3 of P * M (column-major), for the x, y and w of a point at z = 0
        float[] row0 = row(0), row1 = row(1), row3 = row(3);
        Minecraft mc = Minecraft.getMinecraft();
        return new Transform(row0, row1, row3, mc.displayWidth, mc.displayHeight);
    }

    private float[] row(int r) {
        float[] out = new float[4];
        for (int c = 0; c < 4; c++) {
            float sum = 0;
            for (int k = 0; k < 4; k++)
                sum += projection[k * 4 + r] * modelview[c * 4 + k];
            out[c] = sum;
        }
        return out;
    }

    private static final class Transform {
        final float pixelsPerUnit;
        private final float[] rx, ry;
        private final float width, height;
        private final boolean snap;

        Transform(float[] rx, float[] ry, float[] rw, int width, int height) {
            this.rx = rx;
            this.ry = ry;
            this.width = width;
            this.height = height;
            float w = Math.abs(rw[3]) < 1e-6f ? 1f : rw[3];
            // only plain 2D transforms (no rotation, no perspective) can be snapped exactly
            boolean flat = Math.abs(rw[0]) < 1e-6f && Math.abs(rw[1]) < 1e-6f && Math.abs(w - 1f) < 1e-4f;
            this.snap = flat && Math.abs(rx[1]) < 1e-6f && Math.abs(ry[0]) < 1e-6f && Math.abs(rx[0]) > 1e-6f && Math.abs(ry[1]) > 1e-6f;
            float sx = (float) Math.hypot(rx[0], ry[0]) * width / 2f / Math.abs(w);
            this.pixelsPerUnit = Math.max(0.01f, sx);
        }

        float snapX(float v) {
            if (!snap)
                return v;
            float device = Math.round((rx[0] * v + rx[3] + 1f) / 2f * width);
            return ((device * 2f / width - 1f) - rx[3]) / rx[0];
        }

        float snapY(float v) {
            if (!snap)
                return v;
            float device = Math.round((1f - (ry[1] * v + ry[3])) / 2f * height);
            return ((1f - device * 2f / height) - ry[3]) / ry[1];
        }
    }

    // ---- glyph atlases ---------------------------------------------------------------------------

    private Atlas atlas(int pixelSize) {
        pixelSize = Math.max(4, Math.min(256, pixelSize));
        long now = System.currentTimeMillis();
        Atlas atlas = atlases.get(pixelSize);
        if (atlas == null) {
            if (atlases.size() >= MAX_ATLASES) {
                Iterator<Atlas> it = atlases.values().iterator();
                while (it.hasNext()) {
                    Atlas old = it.next();
                    if (now - old.lastUsed > ATLAS_IDLE_MS) {
                        old.delete();
                        it.remove();
                    }
                }
            }
            atlas = new Atlas(pixelSize);
            atlases.put(pixelSize, atlas);
        }
        atlas.lastUsed = now;
        return atlas;
    }

    private static final class Glyph {
        final int texture;
        final float u0, v0, u1, v1;
        /** Position relative to the pen on the baseline, and size, in raster pixels. */
        final int offsetX, offsetY, width, height;

        Glyph(int texture, float u0, float v0, float u1, float v1, int offsetX, int offsetY, int width, int height) {
            this.texture = texture;
            this.u0 = u0;
            this.v0 = v0;
            this.u1 = u1;
            this.v1 = v1;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.width = width;
            this.height = height;
        }
    }

    /** The glyphs at one pixel size, packed onto 512x512 texture pages. */
    private final class Atlas {
        private static final int PAGE = 512;
        private static final int PAD = 1;

        final int pixelSize;
        long lastUsed;
        private final Font raster;
        private final FontRenderContext context;
        private final Map<Character, Glyph> glyphs = new HashMap<>();
        private final List<Integer> pages = new ArrayList<>();
        private int shelfX, shelfY, shelfHeight;

        Atlas(int pixelSize) {
            this.pixelSize = pixelSize;
            this.raster = font.deriveFont((float) pixelSize);
            this.context = new FontRenderContext(null, antiAlias, fracMetrics);
        }

        /** The glyph for a character, rasterising it on first use; null for blank characters. */
        Glyph glyph(char c) {
            if (glyphs.containsKey(c))
                return glyphs.get(c);
            Glyph glyph = rasterise(c);
            glyphs.put(c, glyph);
            return glyph;
        }

        private Glyph rasterise(char c) {
            GlyphVector vector = raster.createGlyphVector(context, String.valueOf(c));
            Rectangle bounds = vector.getPixelBounds(context, 0, 0);
            if (bounds.width <= 0 || bounds.height <= 0)
                return null;
            int w = bounds.width + PAD * 2, h = bounds.height + PAD * 2;
            if (w > PAGE || h > PAGE)
                return null;

            BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            if (antiAlias) {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            }
            if (fracMetrics)
                g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setColor(Color.WHITE);
            g.drawGlyphVector(vector, PAD - bounds.x, PAD - bounds.y);
            g.dispose();

            // find room: this shelf, a new shelf, or a new page
            if (shelfX + w > PAGE) {
                shelfX = 0;
                shelfY += shelfHeight;
                shelfHeight = 0;
            }
            if (pages.isEmpty() || shelfY + h > PAGE) {
                pages.add(newPage());
                shelfX = 0;
                shelfY = 0;
                shelfHeight = 0;
            }
            int texture = pages.get(pages.size() - 1);
            int px = shelfX, py = shelfY;
            shelfX += w;
            shelfHeight = Math.max(shelfHeight, h);

            ByteBuffer pixels = BufferUtils.createByteBuffer(w * h * 4);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    pixels.put((byte) 255).put((byte) 255).put((byte) 255).put((byte) (image.getRGB(x, y) >>> 24));
                }
            }
            pixels.flip();
            GL11.glPushAttrib(GL11.GL_TEXTURE_BIT);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, px, py, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            GL11.glPopAttrib();

            float s = 1f / PAGE;
            return new Glyph(texture, px * s, py * s, (px + w) * s, (py + h) * s,
                    bounds.x - PAD, bounds.y - PAD, w, h);
        }

        private int newPage() {
            int texture = GL11.glGenTextures();
            GL11.glPushAttrib(GL11.GL_TEXTURE_BIT);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, PAGE, PAGE, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE,
                    BufferUtils.createByteBuffer(PAGE * PAGE * 4));
            GL11.glPopAttrib();
            return texture;
        }

        void delete() {
            for (int texture : pages)
                GL11.glDeleteTextures(texture);
            pages.clear();
            glyphs.clear();
        }
    }
}
