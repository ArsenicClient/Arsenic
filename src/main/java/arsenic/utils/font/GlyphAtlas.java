package arsenic.utils.font;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.texture.DynamicTexture;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The glyphs of one font at one pixel size, rasterised with AWT on first use and packed into
 * textures. Text is drawn from the atlas whose pixel size matches how large it appears on screen,
 * so it stays sharp at any GUI scale or zoom instead of stretching a fixed-size bitmap.
 */
final class GlyphAtlas implements AutoCloseable {

    private static final int PAGE_SIZE = 512;
    private static final int PADDING = 1;

    /** A packed glyph: its texture region and where it sits relative to the pen on the baseline, in raster pixels. */
    record Glyph(Page page, float u0, float v0, float u1, float v1, int offsetX, int offsetY, int width, int height) {
        boolean isEmpty() {
            return page == null;
        }
    }

    static final class Page {
        final DynamicTexture texture;
        final TextureSetup setup;
        int shelfX, shelfY, shelfHeight;
        boolean dirty;

        Page(String label) {
            texture = new DynamicTexture(() -> label, new NativeImage(PAGE_SIZE, PAGE_SIZE, true));
            setup = TextureSetup.singleTexture(texture.getTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
        }
    }

    private static final Glyph EMPTY = new Glyph(null, 0, 0, 0, 0, 0, 0, 0, 0);

    final int pixelSize;
    private final Font font;
    private final String label;
    private final FontRenderContext context = new FontRenderContext(null, true, true);
    private final Map<Integer, Glyph> glyphs = new HashMap<>();
    private final List<Page> pages = new ArrayList<>();
    long lastUsed;

    GlyphAtlas(Font baseFont, String label, int pixelSize) {
        this.pixelSize = pixelSize;
        this.font = baseFont.deriveFont((float) pixelSize);
        this.label = label;
    }

    Glyph glyph(int codePoint) {
        Glyph glyph = glyphs.get(codePoint);
        if (glyph == null) {
            glyph = rasterise(codePoint);
            glyphs.put(codePoint, glyph);
        }
        return glyph;
    }

    /** Sends any newly packed glyphs to the GPU; call before the glyphs are drawn. */
    void upload() {
        for (Page page : pages) {
            if (page.dirty) {
                page.texture.upload();
                page.dirty = false;
            }
        }
    }

    private Glyph rasterise(int codePoint) {
        GlyphVector vector = font.createGlyphVector(context, new String(Character.toChars(codePoint)));
        Rectangle bounds = vector.getPixelBounds(context, 0, 0);
        if (bounds.width <= 0 || bounds.height <= 0)
            return EMPTY;
        int w = bounds.width + PADDING * 2, h = bounds.height + PADDING * 2;
        if (w > PAGE_SIZE || h > PAGE_SIZE)
            return EMPTY;

        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setColor(Color.WHITE);
        g.drawGlyphVector(vector, PADDING - bounds.x, PADDING - bounds.y);
        g.dispose();

        Page page = place(w, h);
        int px = page.shelfX, py = page.shelfY;
        NativeImage pixels = page.texture.getPixels();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int alpha = image.getRGB(x, y) >>> 24;
                if (alpha != 0)
                    pixels.setPixel(px + x, py + y, alpha << 24 | 0xFFFFFF);
            }
        }
        page.shelfX += w;
        page.dirty = true;

        float scale = 1f / PAGE_SIZE;
        return new Glyph(page, px * scale, py * scale, (px + w) * scale, (py + h) * scale,
                bounds.x - PADDING, bounds.y - PADDING, w, h);
    }

    /** Finds room for a w x h glyph: the current shelf, a new shelf below it, or a new page. */
    private Page place(int w, int h) {
        Page page = pages.isEmpty() ? null : pages.get(pages.size() - 1);
        if (page != null && page.shelfX + w > PAGE_SIZE) {
            page.shelfX = 0;
            page.shelfY += page.shelfHeight;
            page.shelfHeight = 0;
        }
        if (page == null || page.shelfY + h > PAGE_SIZE) {
            page = new Page(label + " " + pixelSize + "px #" + pages.size());
            pages.add(page);
        }
        page.shelfHeight = Math.max(page.shelfHeight, h);
        return page;
    }

    @Override
    public void close() {
        for (Page page : pages)
            page.texture.close();
        pages.clear();
        glyphs.clear();
    }
}
