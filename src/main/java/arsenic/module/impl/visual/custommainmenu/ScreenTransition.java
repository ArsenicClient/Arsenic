package arsenic.module.impl.visual.custommainmenu;

import arsenic.utils.timer.MSTimer;
import arsenic.utils.timer.TickMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;

/**
 * Cross-fades between screens. When a screen is about to change, the last rendered frame is copied
 * into a texture; the new screen then draws normally, slightly zoomed in and settling to size,
 * while the old frame fades out on top of it. Closing a screen back into the game fades the same
 * way over the world.
 *
 * Every GL call is guarded: a driver that refuses the copy just loses the transition.
 */
public final class ScreenTransition {

    private static final float DURATION = 0.32f;
    private static int tex = -1, texW, texH;
    private static final MSTimer start = new MSTimer();
    private static boolean armed;
    private static boolean pushed;

    private ScreenTransition() {}

    /** Called just before the current screen is replaced. */
    public static void capture(Minecraft mc) {
        try {
            int w = mc.displayWidth, h = mc.displayHeight;
            if (w <= 0 || h <= 0 || mc.getFramebuffer() == null || !OpenGlHelper.isFramebufferEnabled())
                return;
            // nothing worth fading from during start-up
            if (mc.currentScreen == null && mc.theWorld == null)
                return;

            int prevFbo = GL11.glGetInteger(36006);          // GL_FRAMEBUFFER_BINDING
            int prevTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

            if (tex == -1 || texW != w || texH != h) {
                if (tex != -1) GL11.glDeleteTextures(tex);
                tex = GL11.glGenTextures();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
                texW = w;
                texH = h;
            }

            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, mc.getFramebuffer().framebufferObject);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, prevFbo);
            GlStateManager.bindTexture(prevTex);

            start.reset();
            armed = true;
        } catch (Throwable t) {
            armed = false;
        }
    }

    private static float progress() {
        return start.getTime() / 1000f / DURATION;
    }

    /** Before the new screen draws: zoom it in slightly, settling to full size. */
    public static void beginContent(int sw, int sh) {
        pushed = false;
        if (!armed) return;
        float p = progress();
        if (p >= 1f) {
            armed = false;
            return;
        }
        float s = 1f + 0.035f * (1f - TickMode.CUBIC.clamped(p));
        GlStateManager.pushMatrix();
        GlStateManager.translate(sw / 2f, sh / 2f, 0f);
        GlStateManager.scale(s, s, 1f);
        GlStateManager.translate(-sw / 2f, -sh / 2f, 0f);
        pushed = true;
    }

    /** After the new screen draws: undo the zoom and lay the fading old frame over it. */
    public static void endContent(int sw, int sh) {
        if (pushed) {
            GlStateManager.popMatrix();
            pushed = false;
        }
        drawOverlay(sw, sh);
    }

    /** The fading old frame. */
    public static void drawOverlay(int sw, int sh) {
        if (!armed || tex == -1) return;
        float p = progress();
        if (p >= 1f) {
            armed = false;
            return;
        }
        float alpha = 1f - TickMode.CUBIC.clamped(p);
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableDepth();
        GlStateManager.bindTexture(tex);
        GlStateManager.color(1f, 1f, 1f, alpha);
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(7, DefaultVertexFormats.POSITION_TEX);
        wr.pos(0, sh, 0).tex(0, 0).endVertex();
        wr.pos(sw, sh, 0).tex(1, 0).endVertex();
        wr.pos(sw, 0, 0).tex(1, 1).endVertex();
        wr.pos(0, 0, 0).tex(0, 1).endVertex();
        tess.draw();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableAlpha();
        GlStateManager.enableDepth();
    }
}
