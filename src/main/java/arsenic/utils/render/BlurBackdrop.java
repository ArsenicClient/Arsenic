package arsenic.utils.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;

/**
 * A blurred copy of the frame behind the HUD, for blur behind HUD panels.
 *
 * 1.8 drew the panels' shapes into a mask framebuffer and ran Kawase blur shaders through it. Here,
 * right before the GUI is drawn (when the main target holds just the world), the frame is copied
 * aside, the game's own menu blur is run over the main target, the result is copied into
 * {@link #view()}, and the sharp frame is copied back. The panels are then drawn as shapes that
 * sample that texture at their screen position (see {@link QuadBatch#sampleScreen}), so any shape
 * shows the blurred world behind it.
 *
 * The work is only done on frames where something asked for it during extraction ({@link #request}).
 * The strength follows the game's Menu Background Blur setting.
 */
public final class BlurBackdrop {

    private static GpuTexture sharp, blurred;
    private static GpuTextureView blurredView;
    private static boolean requested;

    private BlurBackdrop() {}

    /** Asks for the blurred copy this frame. Called while the GUI is extracted, before the frame is rendered. */
    public static void request() {
        requested = blurred != null;
    }

    /** The texture the blurred copy is made into, sized to the frame, or null if it cannot be made. */
    public static GpuTextureView texture() {
        try {
            RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
            GpuTexture source = target.getColorTexture();
            if (source == null)
                return null;
            int w = source.getWidth(0), h = source.getHeight(0);
            if (blurred == null || blurred.isClosed() || blurred.getWidth(0) != w || blurred.getHeight(0) != h
                    || blurred.getFormat() != source.getFormat()) {
                release();
                GpuDevice device = RenderSystem.getDevice();
                sharp = device.createTexture("Arsenic blur backdrop (sharp)",
                        GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST, source.getFormat(), w, h, 1, 1);
                blurred = device.createTexture("Arsenic blur backdrop",
                        GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, source.getFormat(), w, h, 1, 1);
                blurredView = device.createTextureView(blurred);
            }
            return blurredView;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Called right before the GUI is drawn into the main target. */
    public static void process() {
        if (!requested)
            return;
        requested = false;
        try {
            Minecraft mc = Minecraft.getInstance();
            RenderTarget target = mc.gameRenderer.mainRenderTarget();
            GpuTexture main = target.getColorTexture();
            if (main == null || blurred == null || blurred.isClosed())
                return;
            int w = Math.min(main.getWidth(0), blurred.getWidth(0)), h = Math.min(main.getHeight(0), blurred.getHeight(0));
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.copyTextureToTexture(main, sharp, 0, 0, 0, 0, 0, w, h);
            mc.gameRenderer.processBlurEffect();
            encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.copyTextureToTexture(main, blurred, 0, 0, 0, 0, 0, w, h);
            encoder.copyTextureToTexture(sharp, main, 0, 0, 0, 0, 0, w, h);
        } catch (Throwable ignored) {
        }
    }

    private static void release() {
        if (blurredView != null) blurredView.close();
        if (blurred != null) blurred.close();
        if (sharp != null) sharp.close();
        blurredView = null;
        blurred = null;
        sharp = null;
    }
}
