package arsenic.module.impl.visual;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventShader;
import arsenic.main.Arsenic;
import arsenic.utils.render.shader.KawaseBloom;
import arsenic.utils.render.shader.KawaseBlur;
import arsenic.utils.render.shader.ShaderUtil;
import net.minecraft.client.shader.Framebuffer;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.utils.render.*;

@ModuleInfo(name = "PostProcessing",category = ModuleCategory.GUI, hidden = true)
public class PostProcessing extends Module {
    // Kawase iteration counts and sample offsets are not a matter of taste - below these the
    // effect bands visibly, above them it costs frames for no visual difference. The module is
    // reached from the GUI pane as a single "Blur & Bloom" switch, so both passes run together.
    private static final int BLUR_ITERATIONS = 2;
    private static final int BLUR_OFFSET = 1;
    private static final int BLOOM_ITERATIONS = 2;
    private static final int BLOOM_OFFSET = 1;
    private Framebuffer stencilFramebuffer = new Framebuffer(1, 1, false);

    public void blurElements() {
        if (mc.gui.screen() == Arsenic.getArsenic().getClickGuiScreen()) {
            Arsenic.getInstance().getClickGuiScreen().drawBloom();
        }
    }

    public void blurScreen() {
        {
            stencilFramebuffer = ShaderUtil.createFrameBuffer(stencilFramebuffer);
            stencilFramebuffer.framebufferClear();
            stencilFramebuffer.bindFramebuffer(false);
            EventShader.Bloom bloomEvent = new EventShader.Bloom(BLOOM_ITERATIONS, BLOOM_OFFSET);
            Arsenic.getInstance().getEventManager().getBus().post(bloomEvent);
            applyBurnMaskFade();
            stencilFramebuffer.unbindFramebuffer();
            KawaseBloom.renderBlur(stencilFramebuffer.framebufferTexture, bloomEvent.getIterations(), bloomEvent.getOffset());
        }
        {
            stencilFramebuffer = ShaderUtil.createFrameBuffer(stencilFramebuffer);
            stencilFramebuffer.framebufferClear();
            stencilFramebuffer.bindFramebuffer(false);
            blurElements();
            EventShader.Blur blurEvent = new EventShader.Blur(BLUR_ITERATIONS, BLUR_OFFSET);
            Arsenic.getInstance().getEventManager().getBus().post(blurEvent);
            applyBurnMaskFade();
            stencilFramebuffer.unbindFramebuffer();
            KawaseBlur.renderBlur(stencilFramebuffer.framebufferTexture, BLUR_ITERATIONS, BLUR_OFFSET);
        }
    }

    /**
     * While the ClickGUI's open/close transition is mid-flight, multiplies the
     * currently-bound mask FBO by the transition's per-pixel "keep" factor so
     * blur and bloom vanish exactly where the GUI has burnt/dissolved away
     * (they render outside the burn capture and would otherwise stay at full
     * strength, then pop off). Note: this fades the whole mask, so any other
     * listeners' shapes fade with the GUI during the transition - acceptable,
     * since the transition only runs while the ClickGUI owns the screen.
     */
    private void applyBurnMaskFade() {
        arsenic.gui.click.ClickGuiScreen screen = Arsenic.getArsenic().getClickGuiScreen();
        if (screen == null || mc.gui.screen() != screen || !screen.isBurnActive())
            return;
        float[] box = screen.getBurnBoxPx();
        ShaderUtil.renderBurnMaskFade(screen.currentBurnProgress(), screen.getTransitionStyleId(),
                box[0], box[1], box[2], box[3], box[4]);
    }

    @EventLink
    public final Listener<EventShader.Bloom> shaderEventListener = event -> {
        if (mc.gui.screen() == Arsenic.getInstance().getClickGuiScreen()) {
            event.setIterations(3);
            event.setOffset(2);
            blurElements();
        }
    };
}
