package arsenic.gui.design;

import arsenic.gui.click.GuiStyle;
import arsenic.gui.themes.ThemeManager;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.render.shader.KawaseBlur;
import arsenic.utils.render.shader.ShaderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/**
 * What sits behind the GUI: the blurred world, a tonal scrim (dark or light), and the animated
 * background shader the theme or GUI tab picked. {@link #overlay} adds the scanline pass on top.
 */
public final class Backdrop {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static long blurRetryAt, shaderRetryAt;

    private Backdrop() {}

    public static void draw(float width, float height) {
        blur();
        scrim();
        shader();
    }

    /** Drawn after the GUI: the preset's scanline/VHS film. */
    public static void overlay() {
        if (!GuiStyle.scanlinesEnabled() || System.currentTimeMillis() < shaderRetryAt)
            return;
        try {
            RenderUtils.resetColor();
            ShaderUtil.renderFullscreen("vhsGlitch", M3.isLight() ? 0.05f : 0.10f, 1.0f,
                    ShaderUtil.BlendMode.NORMAL, ThemeManager.getMainColor(), 0f);
        } catch (Throwable t) {
            shaderRetryAt = System.currentTimeMillis() + 2000L;
        } finally {
            RenderUtils.resetColor();
        }
    }

    private static void blur() {
        if (System.currentTimeMillis() < blurRetryAt)
            return;
        try {
            RenderUtils.resetColor();
            KawaseBlur.renderBlurFull(3, 2);
        } catch (Throwable t) {
            blurRetryAt = System.currentTimeMillis() + 2000L;
        } finally {
            GlStateManager.enableBlend();
            GlStateManager.enableAlpha();
            GlStateManager.color(1f, 1f, 1f, 1f);
        }
    }

    private static void shader() {
        if (!GuiStyle.backgroundEnabled() || System.currentTimeMillis() < shaderRetryAt)
            return;
        float alpha = GuiStyle.backgroundOpacity();
        if (alpha <= 0.001f)
            return;
        try {
            RenderUtils.resetColor();
            ShaderUtil.renderFullscreen(GuiStyle.backgroundShader(), alpha, GuiStyle.backgroundSpeed(),
                    ShaderUtil.BlendMode.NORMAL, ThemeManager.getMainColor(), 0.45f);
        } catch (Throwable t) {
            shaderRetryAt = System.currentTimeMillis() + 2000L;
        } finally {
            RenderUtils.resetColor();
            GlStateManager.enableBlend();
            GlStateManager.color(1f, 1f, 1f, 1f);
        }
    }

    /** Light mode frosts the world white; dark mode dims it toward the surface tone. */
    private static void scrim() {
        int width = mc.displayWidth;
        int height = mc.displayHeight;
        boolean light = M3.isLight();
        int surface = M3.surface();
        float r = ((surface >> 16) & 0xFF) / 255f;
        float g = ((surface >> 8) & 0xFF) / 255f;
        float b = (surface & 0xFF) / 255f;
        float top = (light ? 120f : 110f) / 255f;
        float bottom = (light ? 165f : 145f) / 255f;

        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.ortho(0.0D, width, height, 0.0D, -1.0D, 1.0D);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        try {
            GlStateManager.disableTexture2D();
            GlStateManager.disableAlpha();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.shadeModel(GL11.GL_SMOOTH);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glColor4f(r, g, b, top);
            GL11.glVertex2f(0f, 0f);
            GL11.glVertex2f(width, 0f);
            GL11.glColor4f(r, g, b, bottom);
            GL11.glVertex2f(width, height);
            GL11.glVertex2f(0f, height);
            GL11.glEnd();
            GlStateManager.shadeModel(GL11.GL_FLAT);
            GlStateManager.enableTexture2D();
            GlStateManager.enableAlpha();
            GlStateManager.color(1f, 1f, 1f, 1f);
        } finally {
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.popMatrix();
        }
    }
}
