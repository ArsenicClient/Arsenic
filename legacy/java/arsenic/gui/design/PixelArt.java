package arsenic.gui.design;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;

/**
 * Batched pixel-art drawing for the GUI's sprites, as the Element main menu draws its props: a sprite is
 * rows of palette characters, each run of one character becomes a single quad. Call {@link #begin},
 * draw, then {@link #end}.
 */
final class PixelArt {

    private PixelArt() {}

    /** Palette by character; {@link #palette} refreshes it for the current scheme each frame. */
    static final int[] PAL = new int[128];

    /** Rects above this y (GUI units) are trimmed, so a scene can be cut off at the top of its box. */
    static float clipTop = Float.NEGATIVE_INFINITY;

    private static WorldRenderer wr;
    private static int depth;

    static void palette() {
        boolean light = M3.isLight();
        int primary = M3.primary() & 0xFFFFFF;
        // bench and shelf wood
        PAL['t'] = light ? 0xA88466 : 0x8A6A52;
        PAL['T'] = light ? 0x7A5C46 : 0x5E4636;
        PAL['c'] = light ? 0x8E6E56 : 0x4A372B;
        PAL['d'] = light ? 0x5E4636 : 0x2E221B;
        PAL['n'] = 0xD9B88E;
        // glassware
        PAL['f'] = light ? 0x7F94A8 : 0xDDE6EE;
        PAL['F'] = light ? 0x6F8296 : 0x9AA8B8;
        PAL['y'] = 0xFFFFFF;
        // liquids: the accent, the tertiary role, a green and an amber
        PAL['q'] = primary;
        PAL['Q'] = M3.mix(0xFF000000 | primary, 0xFFFFFFFF, 0.45f) & 0xFFFFFF;
        PAL['p'] = M3.tertiary() & 0xFFFFFF;
        PAL['P'] = M3.mix(M3.tertiary(), 0xFFFFFFFF, 0.4f) & 0xFFFFFF;
        PAL['g'] = 0x86D69B;
        PAL['G'] = 0xBDEBC9;
        // flame
        PAL['o'] = 0xF2A65A;
        PAL['O'] = 0xFFD58A;
        PAL['r'] = 0xE8584F;
        // metal stands and burner
        PAL['m'] = light ? 0x5C6474 : 0x6E7686;
        PAL['M'] = light ? 0x7E8696 : 0x9AA2B2;
        PAL['x'] = light ? 0x3E4450 : 0x464C58;
        // corks and the notebook
        PAL['b'] = 0x8B5A3C;
        PAL['B'] = 0xA8714D;
        PAL['e'] = light ? 0xFBF6EE : 0xE8EEF8;
        PAL['E'] = light ? 0x8C9AB8 : 0x9FB3D9;
    }

    /** Mortar lines of the scene's back wall: a hint of the text colour, so it reads in both schemes. */
    static int wallLine() {
        return M3.onSurface() & 0xFFFFFF;
    }

    /** Contact shadows. */
    static int shadow() {
        return M3.isLight() ? 0x5A4632 : 0x000000;
    }

        /** Nested calls share one batch, so a sprite helper can be drawn alone or inside a scene. */
    static void begin() {
        if (depth++ > 0)
            return;
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 771);
        arsenic.utils.render.RenderUtils.applyGuiBlend();
        wr = Tessellator.getInstance().getWorldRenderer();
        wr.begin(7, DefaultVertexFormats.POSITION_COLOR);
    }

    static void end() {
        if (--depth > 0)
            return;
        Tessellator.getInstance().draw();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    static void sprite(String[] rows, float x, float y, float p, int alpha) {
        for (int r = 0; r < rows.length; r++) {
            String s = rows[r];
            int c = 0;
            while (c < s.length()) {
                char ch = s.charAt(c);
                if (ch == ' ') {
                    c++;
                    continue;
                }
                int e = c;
                while (e < s.length() && s.charAt(e) == ch) e++;
                rect(x + c * p, y + r * p, (e - c) * p, p, PAL[ch], alpha);
                c = e;
            }
        }
    }

    static void rect(float x, float y, float w, float h, int rgb, int a) {
        if (a <= 0 || y + h <= clipTop) return;
        if (y < clipTop) {
            h -= clipTop - y;
            y = clipTop;
        }
        a = Math.min(255, a);
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        wr.pos(x, y, 0).color(r, g, b, a).endVertex();
        wr.pos(x, y + h, 0).color(r, g, b, a).endVertex();
        wr.pos(x + w, y + h, 0).color(r, g, b, a).endVertex();
        wr.pos(x + w, y, 0).color(r, g, b, a).endVertex();
    }
}
