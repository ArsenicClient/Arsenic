package arsenic.module.impl.client;

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
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.FolderProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.render.*;

@ModuleInfo(name = "PostProcessing",category = ModuleCategory.CLIENT)
public class PostProcessing extends Module {
    //blur
    private final BooleanProperty blur = new BooleanProperty("Blur", true);
    private final DoubleProperty blurIterations = new DoubleProperty("Blur Iterations", new DoubleValue(1, 8, 2, 1));
    private final DoubleProperty blurOffset = new DoubleProperty("Blur Offset", new DoubleValue(1, 10, 1, 1));
    public final FolderProperty blurFolder = new FolderProperty("Blur Settings", blur, blurIterations, blurOffset);
    //bloom
    private final BooleanProperty bloom = new BooleanProperty("Bloom", true);
    private final DoubleProperty shadowRadius = new DoubleProperty("Bloom Iterations", new DoubleValue(1, 8, 2, 1));
    private final DoubleProperty shadowOffset = new DoubleProperty("Bloom Offset", new DoubleValue(1, 10, 1, 1));
    public final FolderProperty glowFolder = new FolderProperty("Bloom Settings", bloom, shadowRadius, shadowOffset);
    private Framebuffer stencilFramebuffer = new Framebuffer(1, 1, false);

    public void blurElements() {
        if (mc.currentScreen == Arsenic.getArsenic().getClickGuiScreen()) {
            RenderUtils.resetColor();
            Arsenic.getInstance().getClickGuiScreen().drawBloom();
            RenderUtils.resetColor();
        }
    }

    public void blurScreen() {
        if (bloom.getValue()) {
            stencilFramebuffer = ShaderUtil.createFrameBuffer(stencilFramebuffer);
            stencilFramebuffer.framebufferClear();
            stencilFramebuffer.bindFramebuffer(false);
            RenderUtils.resetColor();
            EventShader.Bloom bloomEvent = new EventShader.Bloom((int) shadowRadius.getValue().getInput(), (int) shadowOffset.getValue().getInput());
            Arsenic.getInstance().getEventManager().getBus().post(bloomEvent);
            RenderUtils.resetColor();
            stencilFramebuffer.unbindFramebuffer();
            KawaseBloom.renderBlur(stencilFramebuffer.framebufferTexture, bloomEvent.getIterations(), bloomEvent.getOffset());
        }
        if (blur.getValue()) {
            stencilFramebuffer = ShaderUtil.createFrameBuffer(stencilFramebuffer);
            stencilFramebuffer.framebufferClear();
            stencilFramebuffer.bindFramebuffer(false);
            RenderUtils.resetColor();
            blurElements();
            RenderUtils.resetColor();
            EventShader.Blur blurEvent = new EventShader.Blur((int) blurIterations.getValue().getInput(), (int) blurOffset.getValue().getInput());
            Arsenic.getInstance().getEventManager().getBus().post(blurEvent);
            RenderUtils.resetColor();
            stencilFramebuffer.unbindFramebuffer();
            KawaseBlur.renderBlur(stencilFramebuffer.framebufferTexture, (int) blurIterations.getValue().getInput(), (int) blurOffset.getValue().getInput());
        }
    }

    @EventLink
    public final Listener<EventShader.Bloom> shaderEventListener = event -> {
        if (mc.currentScreen == Arsenic.getInstance().getClickGuiScreen()) {
            event.setIterations(3);
            event.setOffset(3);
            blurElements();
        }
    };
}