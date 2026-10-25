package arsenic.gui.design;

import arsenic.main.Arsenic;
import arsenic.utils.render.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;


/**
 * Brand mark: the animated pixel flask ({@link PixelFlask}, shared with the rail mascot) plus the
 * theme's modern ARSENIC wordmark, its letters as tall as the flask.
 */
public final class LabLogo {

    private LabLogo() {}

    // ------------------------------------------------------------------ logo

    /** The modern wordmark texture is 588 x 166; its letters only fill rows 37..129, so we crop to those. */
    private static final float TEX_W = 588f, TEX_H = 166f, INK_TOP = 37f, INK_BOTTOM = 130f;
    private static final float INK_ASPECT = TEX_W / (INK_BOTTOM - INK_TOP);
    private static final float GAP = 0.22f;

    /** The mark is the same pixel flask as the rail mascot's, so the two always match. */
    private static final PixelFlask PIXEL_FLASK = new PixelFlask(0x10C0L);
    private static final float PIXEL_ASPECT = PixelFlask.W / (float) PixelFlask.H;
    private static float wordHover;

    public static float width(float height) {
        return height * (PIXEL_ASPECT + GAP + INK_ASPECT);
    }

    /** The tallest logo that fits in {@code maxWidth}, capped at {@code maxHeight}. */
    public static float heightFor(float maxWidth, float maxHeight) {
        return Math.min(maxHeight, maxWidth / (PIXEL_ASPECT + GAP + INK_ASPECT));
    }

    /** Briefly bursts bubbles (e.g. on module toggle). */
    public static void pulse() {
        PIXEL_FLASK.pulse();
    }

    public static void draw(float x, float y, float height, boolean hovered) {
        float H = height;
        // ARSENIC, then the flask; the letters stand as tall as the flask
        wordHover += ((hovered ? 1f : 0f) - wordHover) * 0.2f;
        drawWordmark(x, y, H * INK_ASPECT, H, wordHover);
        PIXEL_FLASK.draw(x + H * (INK_ASPECT + GAP), y, H / PixelFlask.H, hovered);
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private static void drawWordmark(float x, float y, float w, float h, float hover) {
        ResourceLocation logo = Arsenic.getArsenic().getThemeManager().getCurrentTheme().getAltLogoPath();
        Minecraft.getMinecraft().getTextureManager().bindTexture(logo);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 771);
        RenderUtils.applyGuiBlend();
        RenderUtils.color2(M3.onSurface(), 0.93f + 0.07f * hover);
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(7, DefaultVertexFormats.POSITION_TEX);
        float v1 = INK_TOP / TEX_H, v2 = INK_BOTTOM / TEX_H;
        wr.pos(x, y + h, 0).tex(0, v2).endVertex();
        wr.pos(x + w, y + h, 0).tex(1, v2).endVertex();
        wr.pos(x + w, y, 0).tex(1, v1).endVertex();
        wr.pos(x, y, 0).tex(0, v1).endVertex();
        tess.draw();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }
}
