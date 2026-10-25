package arsenic.utils.render.shader;

import arsenic.utils.render.RenderUtils;
import arsenic.utils.render.capture.RenderTargets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_LINEAR;

public class KawaseBlur {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public static ShaderUtil kawaseDown = new ShaderUtil("kawaseDown");
    public static ShaderUtil kawaseUp = new ShaderUtil("kawaseUp");

    public static Framebuffer framebuffer = new Framebuffer(1, 1, false);

    public static void setupUniforms(float offset) {
        kawaseDown.setUniformf("offset", offset, offset);
        kawaseUp.setUniformf("offset", offset, offset);
    }

    private static int currentIterations;

    private static final List<Framebuffer> framebufferList = new ArrayList<>();

    private static void initFramebuffers(float iterations) {
        for (Framebuffer framebuffer : framebufferList) {
            framebuffer.deleteFramebuffer();
        }
        framebufferList.clear();

        framebufferList.add(framebuffer = ShaderUtil.createFrameBuffer(null));


        for (int i = 1; i <= iterations; i++) {
            Framebuffer currentBuffer = new Framebuffer((int) (mc.displayWidth / Math.pow(2, i)), (int) (mc.displayHeight / Math.pow(2, i)), false);
            currentBuffer.setFramebufferFilter(GL_LINEAR);
            GlStateManager.bindTexture(currentBuffer.framebufferTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL14.GL_MIRRORED_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL14.GL_MIRRORED_REPEAT);
            GlStateManager.bindTexture(0);

            framebufferList.add(currentBuffer);
        }
    }


    public static void renderBlur(int stencilFrameBufferTexture, int iterations, int offset) {
        renderBlur(stencilFrameBufferTexture, iterations, offset, true);
    }

    /**
     * Blurs the scene over the whole screen into whatever framebuffer is bound now, so a GUI that is being
     * captured (the ClickGUI's burn transition) gets its backdrop in the capture instead of on the main one.
     */
    public static void renderBlurFull(int iterations, int offset) {
        targetFbo = GL11.glGetInteger(36006);                // GL_FRAMEBUFFER_BINDING
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.ortho(0.0D, mc.displayWidth, mc.displayHeight, 0.0D, 1000.0D, 3000.0D);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.translate(0f, 0f, -2000f);
        try {
            renderBlur(0, iterations, offset, false, true);
        } finally {
            targetFbo = -1;
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.popMatrix();
        }
    }

    /** The framebuffer {@link #renderBlurFull} draws its result into; -1 means the main one. */
    private static int targetFbo = -1;

    private static void renderBlur(int stencilFrameBufferTexture, int iterations, int offset, boolean mask) {
        renderBlur(stencilFrameBufferTexture, iterations, offset, mask, false);
    }

    private static void renderBlur(int stencilFrameBufferTexture, int iterations, int offset, boolean mask, boolean full) {
        if (currentIterations != iterations || framebuffer.framebufferWidth != mc.displayWidth || framebuffer.framebufferHeight != mc.displayHeight) {
            initFramebuffers(iterations);
            currentIterations = iterations;
        }

        // read the real scene: while visuals are redirected, getFramebuffer() is the overlay
        renderFBO(framebufferList.get(1), RenderTargets.getScene().framebufferTexture, kawaseDown, offset, full);

        for (int i = 1; i < iterations; i++) {
            renderFBO(framebufferList.get(i + 1), framebufferList.get(i).framebufferTexture, kawaseDown, offset, full);
        }

        for (int i = iterations; i > 1; i--) {
            renderFBO(framebufferList.get(i - 1), framebufferList.get(i).framebufferTexture, kawaseUp, offset, full);
        }


        Framebuffer lastBuffer = framebufferList.get(0);
        lastBuffer.framebufferClear();
        lastBuffer.bindFramebuffer(false);
        kawaseUp.init();
        kawaseUp.setUniformf("offset", offset, offset);
        kawaseUp.setUniformi("inTexture", 0);
        kawaseUp.setUniformi("check", mask ? 1 : 0);
        kawaseUp.setUniformi("textureToCheck", 16);
        kawaseUp.setUniformf("halfpixel", 1.0f / lastBuffer.framebufferWidth, 1.0f / lastBuffer.framebufferHeight);
        kawaseUp.setUniformf("iResolution", lastBuffer.framebufferWidth, lastBuffer.framebufferHeight);
        if (mask)
            RenderUtils.bindTextureOnUnit(16, stencilFrameBufferTexture);
        RenderUtils.bindTexture(framebufferList.get(1).framebufferTexture);
        if (full)
            ShaderUtil.drawQuads(mc.displayWidth, mc.displayHeight);
        else
            ShaderUtil.drawQuads();
        kawaseUp.unload();


        if (targetFbo > 0 && targetFbo != mc.getFramebuffer().framebufferObject) {
            net.minecraft.client.renderer.OpenGlHelper.glBindFramebuffer(
                    net.minecraft.client.renderer.OpenGlHelper.GL_FRAMEBUFFER, targetFbo);
            GlStateManager.viewport(0, 0, mc.displayWidth, mc.displayHeight);
        } else {
            mc.getFramebuffer().bindFramebuffer(true);
        }
        RenderUtils.bindTexture(framebufferList.get(0).framebufferTexture);
        RenderUtils.setAlphaLimit(0);
        RenderUtils.startBlend();
        if (full)
            ShaderUtil.drawQuads(mc.displayWidth, mc.displayHeight);
        else
            ShaderUtil.drawQuads();
        GlStateManager.bindTexture(0);

    }

    private static void renderFBO(Framebuffer framebuffer, int framebufferTexture, ShaderUtil shader, float offset, boolean full) {
        framebuffer.framebufferClear();
        framebuffer.bindFramebuffer(false);
        shader.init();
        RenderUtils.bindTexture(framebufferTexture);
        shader.setUniformf("offset", offset, offset);
        shader.setUniformi("inTexture", 0);
        shader.setUniformi("check", 0);
        shader.setUniformf("halfpixel", 1.0f / framebuffer.framebufferWidth, 1.0f / framebuffer.framebufferHeight);
        shader.setUniformf("iResolution", framebuffer.framebufferWidth, framebuffer.framebufferHeight);
        if (full)
            ShaderUtil.drawQuads(mc.displayWidth, mc.displayHeight);
        else
            ShaderUtil.drawQuads();
        shader.unload();
    }


}