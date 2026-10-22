package arsenic.module.impl.visual.custommainmenu;

import arsenic.utils.timer.MSTimer;
import arsenic.utils.timer.TickMode;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.util.ARGB;
import org.joml.Matrix3x2f;

/**
 * Cross-fades between screens. When a screen is about to change, the last rendered frame is copied
 * into a texture; the new screen then draws normally, slightly zoomed in and settling to size,
 * while the old frame fades out on top of it. Closing a screen back into the game fades the same
 * way over the world.
 *
 * The copy is a GPU texture-to-texture copy of the main render target, which still holds the last
 * presented frame when the screen changes. Any failure just loses the transition.
 */
public final class ScreenTransition {

    private static final float DURATION = 0.32f;
    private static GpuTexture tex;
    private static GpuTextureView view;
    private static final MSTimer start = new MSTimer();
    private static boolean armed;
    private static boolean pushed;

    private ScreenTransition() {}

    /** Called just before the current screen is replaced. */
    public static void capture(Minecraft mc) {
        try {
            // nothing worth fading from during start-up
            if (mc.gui.screen() == null && mc.level == null)
                return;
            RenderTarget target = mc.gameRenderer.mainRenderTarget();
            GpuTexture source = target == null ? null : target.getColorTexture();
            if (source == null || target.width <= 0 || target.height <= 0)
                return;
            int w = target.width, h = target.height;

            GpuDevice device = RenderSystem.getDevice();
            if (tex == null || tex.isClosed() || tex.getWidth(0) != w || tex.getHeight(0) != h || tex.getFormat() != source.getFormat()) {
                release();
                tex = device.createTexture("Arsenic screen transition",
                        GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, source.getFormat(), w, h, 1, 1);
                view = device.createTextureView(tex);
            }
            device.createCommandEncoder().copyTextureToTexture(source, tex, 0, 0, 0, 0, 0, w, h);

            start.reset();
            armed = true;
        } catch (Throwable t) {
            armed = false;
        }
    }

    private static void release() {
        if (view != null) view.close();
        if (tex != null) tex.close();
        view = null;
        tex = null;
    }

    private static float progress() {
        return start.getTime() / 1000f / DURATION;
    }

    /** Before the new screen draws: zoom it in slightly, settling to full size. */
    public static void beginContent(GuiGraphicsExtractor graphics, int sw, int sh) {
        pushed = false;
        if (!armed) return;
        float p = progress();
        if (p >= 1f) {
            armed = false;
            return;
        }
        float s = 1f + 0.035f * (1f - TickMode.CUBIC.clamped(p));
        graphics.pose().pushMatrix();
        graphics.pose().translate(sw / 2f, sh / 2f);
        graphics.pose().scale(s, s);
        graphics.pose().translate(-sw / 2f, -sh / 2f);
        pushed = true;
    }

    /** After the new screen draws: undo the zoom and lay the fading old frame over it. */
    public static void endContent(GuiGraphicsExtractor graphics, int sw, int sh) {
        if (pushed) {
            graphics.pose().popMatrix();
            pushed = false;
        }
        drawOverlay(graphics, sw, sh);
    }

    /** The fading old frame. */
    public static void drawOverlay(GuiGraphicsExtractor graphics, int sw, int sh) {
        if (!armed || view == null || tex.isClosed()) return;
        float p = progress();
        if (p >= 1f) {
            armed = false;
            return;
        }
        float alpha = 1f - TickMode.CUBIC.clamped(p);
        graphics.nextStratum();
        // render targets are stored bottom row first, so the texture is drawn flipped
        graphics.guiRenderState.addGuiElement(new BlitRenderState(RenderPipelines.GUI_TEXTURED,
                TextureSetup.singleTexture(view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)),
                new Matrix3x2f(graphics.pose()), 0, 0, sw, sh, 0f, 1f, 1f, 0f, ARGB.white(alpha), graphics.scissorStack.peek()));
    }
}
