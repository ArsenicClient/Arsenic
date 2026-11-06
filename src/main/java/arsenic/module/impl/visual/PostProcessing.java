package arsenic.module.impl.visual;

import arsenic.module.ModuleTier;
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
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

@ModuleInfo(name = "PostProcessing",category = ModuleCategory.GUI, hidden = true, tier = ModuleTier.QOL)
public class PostProcessing extends Module {
    private static final int BLUR_ITERATIONS = 2;
    private static final int BLUR_OFFSET = 1;
    private static final int BLOOM_ITERATIONS = 2;
    private static final int BLOOM_OFFSET = 1;
    private Framebuffer stencilFramebuffer = ShaderUtil.clearedFramebuffer(new Framebuffer(1, 1, false));

    public void blurElements() {
        if (mc.currentScreen == Arsenic.getArsenic().getClickGuiScreen()) {
            RenderUtils.resetColor();
            Arsenic.getInstance().getClickGuiScreen().drawBloom();
            RenderUtils.resetColor();
        }
    }

    public void blurScreen() {
        {
            stencilFramebuffer = ShaderUtil.createFrameBuffer(stencilFramebuffer);
            stencilFramebuffer.framebufferClear();
            stencilFramebuffer.bindFramebuffer(false);
            RenderUtils.resetColor();
            beginMask(BLOOM);
            EventShader.Bloom bloomEvent = new EventShader.Bloom(BLOOM_ITERATIONS, BLOOM_OFFSET);
            try {
                Arsenic.getInstance().getEventManager().getBus().post(bloomEvent);
                RenderUtils.resetColor();
                applyBurnMaskFade();
            } finally {
                endMask();
            }
            stencilFramebuffer.unbindFramebuffer();
            beginIfMaskDrawn(BLOOM);
            try {
                KawaseBloom.renderBlur(stencilFramebuffer.framebufferTexture, bloomEvent.getIterations(), bloomEvent.getOffset());
            } finally {
                endIfMaskDrawn();
            }
        }
        {
            stencilFramebuffer = ShaderUtil.createFrameBuffer(stencilFramebuffer);
            stencilFramebuffer.framebufferClear();
            stencilFramebuffer.bindFramebuffer(false);
            RenderUtils.resetColor();
            beginMask(BLUR);
            try {
                blurElements();
                RenderUtils.resetColor();
                EventShader.Blur blurEvent = new EventShader.Blur(BLUR_ITERATIONS, BLUR_OFFSET);
                Arsenic.getInstance().getEventManager().getBus().post(blurEvent);
                RenderUtils.resetColor();
                applyBurnMaskFade();
            } finally {
                endMask();
            }
            stencilFramebuffer.unbindFramebuffer();
            beginIfMaskDrawn(BLUR);
            try {
                KawaseBlur.renderBlur(stencilFramebuffer.framebufferTexture, BLUR_ITERATIONS, BLUR_OFFSET);
            } finally {
                endIfMaskDrawn();
            }
        }
    }

    /*
     * The blur and bloom chains are several full-screen passes, but their output is masked by what the listeners
     * drew: an empty mask changes nothing on screen. An occlusion query counts the mask's pixels and conditional
     * rendering makes the GPU skip the chain when there were none, without the CPU ever waiting on the result.
     */

    private static final int BLOOM = 0, BLUR = 1;
    private final int[] maskQueries = new int[2];
    private boolean querying, conditional;

    private static boolean canSkipEmptyMasks() {
        return GLContext.getCapabilities().OpenGL30;
    }

    private void beginMask(int pass) {
        if (!canSkipEmptyMasks())
            return;
        if (maskQueries[pass] == 0)
            maskQueries[pass] = GL15.glGenQueries();
        GL15.glBeginQuery(GL15.GL_SAMPLES_PASSED, maskQueries[pass]);
        querying = true;
    }

    private void endMask() {
        if (!querying)
            return;
        GL15.glEndQuery(GL15.GL_SAMPLES_PASSED);
        querying = false;
    }

    private void beginIfMaskDrawn(int pass) {
        if (maskQueries[pass] == 0 || !canSkipEmptyMasks())
            return;
        GL30.glBeginConditionalRender(maskQueries[pass], GL30.GL_QUERY_WAIT);
        conditional = true;
    }

    private void endIfMaskDrawn() {
        if (!conditional)
            return;
        GL30.glEndConditionalRender();
        conditional = false;
    }

    private void applyBurnMaskFade() {
        arsenic.gui.click.ClickGuiScreen screen = Arsenic.getArsenic().getClickGuiScreen();
        if (screen == null || mc.currentScreen != screen || !screen.isBurnActive())
            return;
        float[] box = screen.getBurnBoxPx();
        ShaderUtil.renderBurnMaskFade(screen.currentBurnProgress(), screen.getTransitionStyleId(),
                box[0], box[1], box[2], box[3], box[4]);
    }

    @EventLink
    public final Listener<EventShader.Bloom> shaderEventListener = event -> {
        if (mc.currentScreen == Arsenic.getInstance().getClickGuiScreen()) {
            event.setIterations(3);
            event.setOffset(2);
            blurElements();
        }
    };
}
