package arsenic.injection.mixin;

import arsenic.module.impl.visual.PostProcessing;
import arsenic.utils.render.capture.RenderTargets;
import arsenic.utils.render.capture.SilentView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import arsenic.event.impl.EventRender2D;
import arsenic.main.Arsenic;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;

@Mixin(priority = 1111, value = GuiIngame.class)
public class MixinGuiIngame {

    @Inject(method = "renderGameOverlay", at = @At("RETURN"))
    private void arsenic$fadeIntoGame(float partialTicks, CallbackInfo ci) {
        if (SilentView.isRenderingHud())
            return;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        if (mc.currentScreen == null) {
            ScaledResolution sr = new ScaledResolution(mc);
            arsenic.module.impl.visual.custommainmenu.ScreenTransition.drawOverlay(sr.getScaledWidth(), sr.getScaledHeight());
        }
    }

    @Inject(method = "renderTooltip", at = @At("RETURN"))
    private void renderTooltip(ScaledResolution sr, float partialTicks, CallbackInfo ci) {
        // the silent recording only gets the vanilla HUD
        if (SilentView.isRenderingHud())
            return;
        boolean redirected = RenderTargets.beginVisuals();
        try {
            if (!System.getProperty("os.name").toLowerCase().contains("mac")) {
                PostProcessing postProcessing = Arsenic.getArsenic().getModuleManager().getModuleByClass(PostProcessing.class);
                if (postProcessing.isEnabled()) {
                    postProcessing.blurScreen();
                }
            }
            Arsenic.getInstance().getEventManager().post(new EventRender2D(partialTicks, sr));
        } finally {
            RenderTargets.endVisuals(redirected);
        }
    }

}
