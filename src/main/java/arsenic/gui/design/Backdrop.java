package arsenic.gui.design;

import arsenic.utils.render.RenderUtils;
import arsenic.utils.render.shader.KawaseBlur;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

public final class Backdrop {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static long retryAt;

    private Backdrop() {}

    public static void draw(float width, float height) {
        blur();
        scrim();
    }

    private static void blur() {
        if (System.currentTimeMillis() < retryAt)
            return;
        try {
            RenderUtils.resetColor();
            KawaseBlur.renderBlurFull(3, 2);
        } catch (Throwable t) {
            retryAt = System.currentTimeMillis() + 2000L;
        } finally {
            GlStateManager.enableBlend();
            GlStateManager.enableAlpha();
            GlStateManager.color(1f, 1f, 1f, 1f);
        }
    }

    private static void scrim() {
        int width = mc.displayWidth;
        int height = mc.displayHeight;
        int surface = M3.surface();
        float r = ((surface >> 16) & 0xFF) / 255f;
        float g = ((surface >> 8) & 0xFF) / 255f;
        float b = (surface & 0xFF) / 255f;

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
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GlStateManager.shadeModel(GL11.GL_SMOOTH);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glColor4f(r, g, b, 110f / 255f);
            GL11.glVertex2f(0f, 0f);
            GL11.glVertex2f(width, 0f);
            GL11.glColor4f(r, g, b, 145f / 255f);
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
