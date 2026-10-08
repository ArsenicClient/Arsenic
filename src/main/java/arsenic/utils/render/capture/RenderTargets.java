package arsenic.utils.render.capture;

import arsenic.runtime.Platform;
import arsenic.injection.accessor.IMixinMinecraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

/**
 * Lets client rendering be pointed at a framebuffer other than the main one.
 * While a redirect is set, {@code mc.getFramebuffer()} returns it (see MixinMinecraft), so code that
 * rebinds "the" framebuffer mid-render keeps drawing into the redirect target.
 *
 * When a {@link FrameSink} is attached with hideVisuals, Render3D/Render2D listeners are drawn into a
 * transparent overlay; the main framebuffer is captured clean at the end of the HUD pass and the overlay is
 * composited on top afterwards so the player still sees everything. With a silent-view recorder the frame comes
 * from {@link SilentView} instead, which gets the vanilla HUD but never client visuals.
 */
public final class RenderTargets {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private static Framebuffer redirect;
    private static Framebuffer overlay;
    private static boolean overlayFresh, overlayUsed, frameDone;

    private static FrameSink recorder;
    private static boolean hideVisuals, silentView;

    private RenderTargets() {
    }

    /** The real main framebuffer, ignoring any redirect. Use it when reading the rendered scene. */
    public static Framebuffer getScene() {
        return ((IMixinMinecraft) mc).getFramebufferMc();
    }

    public static Framebuffer getRedirect() {
        return redirect;
    }

    /** Redirects rendering to {@code target}; returns the previous redirect to hand back to {@link #pop}. */
    public static Framebuffer push(Framebuffer target) {
        Framebuffer previous = redirect;
        redirect = target;
        target.bindFramebuffer(false);
        return previous;
    }

    public static void pop(Framebuffer previous) {
        redirect = previous;
        (previous != null ? previous : getScene()).bindFramebuffer(false);
    }

    /** @param silent record the world from the silent rotation (see {@link SilentView}) instead of the screen */
    public static void attachRecorder(FrameSink rec, boolean hide, boolean silent) {
        recorder = rec;
        hideVisuals = hide;
        silentView = silent;
    }

    public static void detachRecorder() {
        recorder = null;
        overlayUsed = false;
        if (overlay != null) {
            overlay.deleteFramebuffer();
            overlay = null;
        }
    }

    private static boolean recording() {
        return recorder != null && recorder.isRunning();
    }

    /** Whether the recorder needs the silent pass rendered this frame. */
    public static boolean recordsSilentView() {
        return silentView && recording();
    }

    /** Starts drawing client visuals into the overlay. Returns whether a redirect was made; pass it to {@link #endVisuals}. */
    public static boolean beginVisuals() {
        if (!recording() || !hideVisuals || silentView || redirect != null || !OpenGlHelper.isFramebufferEnabled())
            return false;
        Framebuffer scene = getScene();
        ensureOverlay(scene);
        if (!overlayFresh) {
            overlay.framebufferClear();
            overlayFresh = true;
        }
        copyDepth(scene, overlay);
        overlayUsed = true;
        push(overlay);
        return true;
    }

    public static void endVisuals(boolean began) {
        if (began)
            pop(null);
    }

    public static void onFrameStart() {
        SilentView.onFrameStart();
        frameDone = false;
        overlayFresh = false;
    }

    /** Called once per frame after the vanilla HUD ({@code hud} true), or at the end of the frame when there is none. */
    public static void onFrameEnd(float partialTicks, boolean hud) {
        if (frameDone)
            return;
        frameDone = true;
        if (!recording())
            return;
        Framebuffer scene = getScene();
        if (silentView && SilentView.hasFrame()) {
            // the silent pass has no client visuals, so there is nothing to composite back
            if (hud)
                SilentView.renderHud(partialTicks);
            SilentView.getFramebuffer().bindFramebuffer(true);
            recorder.capture();
            scene.bindFramebuffer(true);
            return;
        }
        scene.bindFramebuffer(true);
        recorder.capture();
        if (overlayUsed && overlay != null)
            composite();
        overlayUsed = false;
    }

    private static void ensureOverlay(Framebuffer scene) {
        if (overlay == null || overlay.framebufferWidth != scene.framebufferWidth || overlay.framebufferHeight != scene.framebufferHeight) {
            if (overlay != null)
                overlay.deleteFramebuffer();
            overlay = new Framebuffer(scene.framebufferWidth, scene.framebufferHeight, true);
            overlay.setFramebufferColor(0, 0, 0, 0);
            overlayFresh = false;
        }
        // framebuffer stencils are Forge's
        if (Platform.isForge() && scene.isStencilEnabled() && !overlay.isStencilEnabled())
            overlay.enableStencil();
    }

    /** Copies depth so 3D visuals are still occluded by the world. Needs GL 3.0; skipped otherwise. */
    public static void copyDepth(Framebuffer from, Framebuffer to) {
        if (!GLContext.getCapabilities().OpenGL30)
            return;
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        if (scissor)
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, from.framebufferObject);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, to.framebufferObject);
        GL30.glBlitFramebuffer(0, 0, from.framebufferWidth, from.framebufferHeight,
                0, 0, to.framebufferWidth, to.framebufferHeight, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        if (scissor)
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
    }

    private static void composite() {
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        // overlay colour is already multiplied by its alpha, so add it over the scene premultiplied
        GlStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        overlay.framebufferRenderExt(mc.displayWidth, mc.displayHeight, false);
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.popMatrix();
    }
}
