package arsenic.utils.render;

import arsenic.utils.render.shader.ShaderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List;
import java.util.Optional;

/**
 * Draws a soft coloured halo around player models that stays visible through walls.
 *
 * <p>Four passes: the models go into an offscreen buffer with its own freshly cleared depth buffer,
 * so nothing in the world occludes them; a mask shader flattens that to a uniform silhouette; it is
 * blurred horizontally, then vertically while discarding anything inside the original silhouette,
 * leaving just the halo.
 *
 * <p>Call {@link #render} from an {@code EventRenderWorldLast} listener, while the camera matrices
 * are still current.
 */
public final class GlowRenderer {

    private static final Minecraft mc = Minecraft.getMinecraft();

    /**
     * GL_FRAMEBUFFER_BINDING. Same value in core, ARB and EXT, so it is safe to query directly -
     * but binding has to go through OpenGlHelper, which picks the matching entry point.
     */
    private static final int FRAMEBUFFER_BINDING = 0x8CA6;
    private static final int CURRENT_PROGRAM = 0x8B8D;

    /** Anything at least this opaque in the rendered model counts as fully part of the silhouette. */
    private static final float ALPHA_THRESHOLD = 0.05f;

    private Framebuffer entityBuffer;
    private Framebuffer maskBuffer;
    private Framebuffer blurBuffer;

    private ShaderUtil maskShader;
    private ShaderUtil glowShader;
    private boolean shadersBroken;

    private FloatBuffer weights;
    private int weightRadius = -1;

    private final IntBuffer viewport = BufferUtils.createIntBuffer(16);
    private int previousProgram;

    /**
     * @param targets     entities to outline, already frustum culled
     * @param colour      RGB, alpha ignored
     * @param radius      blur radius in pixels
     * @param strength    exposure - how hot the halo burns
     * @param outlineOnly true for a hollow outline, false to let the glow cover the model too
     * @param fillAlpha   0-1 solid fill drawn inside the silhouette, 0 to skip
     */
    public void render(List<? extends Entity> targets, float partialTicks, int colour, int radius,
                       float strength, boolean outlineOnly, float fillAlpha) {
        if (targets == null || targets.isEmpty() || !OpenGlHelper.isFramebufferEnabled() || !ensureShaders())
            return;

        RenderManager renderManager = mc.getRenderManager();
        if (renderManager == null)
            return;

        int previousBuffer = GL11.glGetInteger(FRAMEBUFFER_BINDING);

        // Sized off the live viewport, not the display: the projection Minecraft set up maps to
        // whatever viewport is current, and with a shaderpack loaded that is not display sized.
        viewport.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        int width = viewport.get(2);
        int height = viewport.get(3);
        if (width <= 0 || height <= 0)
            return;

        previousProgram = GL11.glGetInteger(CURRENT_PROGRAM);

        entityBuffer = ensureBuffer(entityBuffer, width, height, true);
        maskBuffer = ensureBuffer(maskBuffer, width, height, false);
        blurBuffer = ensureBuffer(blurBuffer, width, height, false);

        try {
            renderSilhouettes(targets, partialTicks, renderManager, previousBuffer, width, height);
            composite(colour, radius, strength, outlineOnly, fillAlpha, previousBuffer, width, height);
        } finally {
            restoreViewport();
            GL20.glUseProgram(previousProgram);
        }
    }

    /** Frees the buffers; call from the owning module's onDisable. */
    public void release() {
        entityBuffer = deleteBuffer(entityBuffer);
        maskBuffer = deleteBuffer(maskBuffer);
        blurBuffer = deleteBuffer(blurBuffer);
    }

    // -----------------------------------------------------------------

    private void renderSilhouettes(List<? extends Entity> targets, float partialTicks,
                                   RenderManager renderManager, int previousBuffer,
                                   int width, int height) {
        // hideGUI makes RendererLivingEntity#canRenderName return false, keeping nametags out of the
        // silhouette; the shadow flag does the same for the blob shadow under each entity
        Optional<GameSettings> settings = Optional.ofNullable(mc.gameSettings);
        boolean hidGui = settings.map(s -> s.hideGUI).orElse(false);
        settings.ifPresent(s -> s.hideGUI = true);
        renderManager.setRenderShadow(false);

        try {
            entityBuffer.framebufferClear();

            for (Entity target : targets) {
                // Rebound for every entity on purpose. Rendering a player runs Minecraft's (and
                // OptiFine's) own entity code, which is free to bind a framebuffer of its own and
                // leave it bound - after which every remaining entity would be drawn into the game's
                // buffer instead of ours. That is what limited the glow to whichever player happened
                // to be rendered first, and made it look camera dependent.
                entityBuffer.bindFramebuffer(false);
                GL11.glViewport(0, 0, width, height);

                // The silhouette has to be pure geometric coverage, so everything that could make a
                // fragment's alpha vary is taken out of the path:
                //
                //  - program 0, because with a shaderpack loaded the entity program is still bound
                //    here and those write their own data into alpha (lighting, normals, material
                //    ids), which changes with facing angle and differs per player;
                //  - texturing off, so the skin's alpha channel cannot thin the silhouette either;
                //  - lighting off and colour forced opaque white;
                //  - blending off, so fragments write rather than mix with the cleared buffer.
                //
                // What lands in the buffer is then alpha 1 wherever the model covers a pixel and 0
                // everywhere else, for every player, at every angle.
                GL20.glUseProgram(0);
                GlStateManager.disableTexture2D();
                GlStateManager.disableLighting();
                GlStateManager.disableBlend();
                GlStateManager.color(1f, 1f, 1f, 1f);
                GlStateManager.enableDepth();
                GlStateManager.depthMask(true);
                RenderUtils.setAlphaLimit(0f);

                try {
                    renderManager.renderEntityStatic(target, partialTicks, false);
                } catch (Throwable ignored) {
                    // one entity with a broken renderer should not kill the pass
                }
            }
        } finally {
            GlStateManager.enableTexture2D();
            GlStateManager.enableLighting();
            RenderUtils.setAlphaLimit(0.1f);
            GL20.glUseProgram(previousProgram);
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, previousBuffer);
            restoreViewport();
            renderManager.setRenderShadow(true);
            settings.ifPresent(s -> s.hideGUI = hidGui);
        }
    }

    private void composite(int colour, int radius, float strength, boolean outlineOnly,
                           float fillAlpha, int previousBuffer, int width, int height) {
        float r = ((colour >> 16) & 0xFF) / 255f;
        float g = ((colour >> 8) & 0xFF) / 255f;
        float b = (colour & 0xFF) / 255f;

        updateWeights(radius);
        float texelX = 1f / width;
        float texelY = 1f / height;

        begin2D();
        try {
            // Flat colour silhouette. The threshold is a safety net - the entity pass already writes
            // binary coverage - so anything that did slip through at partial alpha still counts as
            // fully covered rather than glowing at its own private brightness.
            maskBuffer.framebufferClear();
            maskBuffer.bindFramebuffer(false);
            GL11.glViewport(0, 0, width, height);
            maskShader.init();
            maskShader.setUniformi("textureIn", 0);
            maskShader.setUniformf("color", r, g, b, 1f);
            maskShader.setUniformf("threshold", ALPHA_THRESHOLD);
            RenderUtils.bindTexture(entityBuffer.framebufferTexture);
            ShaderUtil.drawQuads();
            maskShader.unload();

            // Horizontal blur. The y component is very slightly negative rather than zero on
            // purpose: glow.fsh gates its exposure curve on step(0.0, direction.y), which is 1 for
            // y == 0, so a flat zero would apply the curve on this pass as well as the vertical one.
            // Applying it twice makes brightness a doubly nonlinear function of coverage, which
            // exaggerates the difference between silhouettes of different size.
            blurBuffer.framebufferClear();
            blurBuffer.bindFramebuffer(false);
            GL11.glViewport(0, 0, width, height);
            setupGlow(radius, texelX, texelY, 1f, -1e-4f, 1f, false);
            RenderUtils.bindTexture(maskBuffer.framebufferTexture);
            ShaderUtil.drawQuads();
            glowShader.unload();

            // back to whatever the game was drawing into, viewport included
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, previousBuffer);
            restoreViewport();

            if (fillAlpha > 0f) {
                GlStateManager.color(1f, 1f, 1f, fillAlpha);
                RenderUtils.bindTexture(maskBuffer.framebufferTexture);
                ShaderUtil.drawQuads();
                GlStateManager.color(1f, 1f, 1f, 1f);
            }

            // vertical blur onto the screen; avoidTexture discards the interior, leaving the outline
            setupGlow(radius, texelX, texelY, 0f, 1f, strength, outlineOnly);
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE16);
            RenderUtils.bindTexture(maskBuffer.framebufferTexture);
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
            RenderUtils.bindTexture(blurBuffer.framebufferTexture);
            ShaderUtil.drawQuads();
            glowShader.unload();
        } finally {
            end2D(previousBuffer);
        }
    }

    private void setupGlow(int radius, float texelX, float texelY,
                           float dirX, float dirY, float exposure, boolean avoid) {
        glowShader.init();
        glowShader.setUniformi("textureIn", 0);
        glowShader.setUniformi("textureToCheck", 16);
        glowShader.setUniformi("avoidTexture", avoid ? 1 : 0);
        glowShader.setUniformf("texelSize", texelX, texelY);
        glowShader.setUniformf("direction", dirX, dirY);
        glowShader.setUniformf("radius", radius);
        glowShader.setUniformf("exposure", exposure);
        glowShader.setUniformf("color", 1f, 1f, 1f);
        GL20.glUniform1(glowShader.getUniform("weights"), weights);
    }

    /** Normalised gaussian taps, rebuilt only when the radius changes. */
    private void updateWeights(int radius) {
        if (radius == weightRadius && weights != null)
            return;
        weightRadius = radius;

        float[] values = new float[256];
        float sigma = Math.max(radius / 2f, 0.5f);
        float sum = 0f;
        for (int i = 0; i <= radius && i < 256; i++) {
            values[i] = (float) Math.exp(-(i * i) / (2f * sigma * sigma));
            sum += i == 0 ? values[i] : values[i] * 2f;
        }
        for (int i = 0; i <= radius && i < 256; i++)
            values[i] /= sum;

        weights = BufferUtils.createFloatBuffer(256);
        weights.put(values);
        weights.flip();
    }

    private boolean ensureShaders() {
        if (shadersBroken)
            return false;
        if (maskShader != null && glowShader != null)
            return true;
        try {
            maskShader = new ShaderUtil("glowMask");
            glowShader = new ShaderUtil("glow");
            return true;
        } catch (Throwable t) {
            shadersBroken = true;
            return false;
        }
    }

    /** Flat screen space projection for the fullscreen shader quads. */
    private void begin2D() {
        ScaledResolution sr = new ScaledResolution(mc);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, sr.getScaledWidth_double(), sr.getScaledHeight_double(), 0, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.disableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderUtils.setAlphaLimit(0);
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private void end2D(int previousBuffer) {
        GL20.glUseProgram(previousProgram);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE16);
        GlStateManager.bindTexture(0);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        GlStateManager.bindTexture(0);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();

        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, previousBuffer);
        restoreViewport();

        GlStateManager.disableBlend();
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        RenderUtils.setAlphaLimit(0.1f);
        RenderUtils.resetColor();
    }

    /**
     * ShaderUtil#createFrameBuffer always builds display sized buffers, which is the wrong size when
     * a shaderpack has changed the viewport, so this sizes to the viewport instead.
     */
    private static Framebuffer ensureBuffer(Framebuffer buffer, int width, int height, boolean depth) {
        if (buffer != null && buffer.framebufferWidth == width && buffer.framebufferHeight == height)
            return buffer;
        if (buffer != null)
            buffer.deleteFramebuffer();
        return new Framebuffer(width, height, depth);
    }

    private void restoreViewport() {
        GL11.glViewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
    }

    private static Framebuffer deleteBuffer(Framebuffer buffer) {
        if (buffer != null)
            buffer.deleteFramebuffer();
        return null;
    }
}
