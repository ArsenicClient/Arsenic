package arsenic.injection.mixin;

import arsenic.module.impl.client.PostProcessing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import arsenic.event.impl.EventRender2D;
import arsenic.main.Arsenic;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.main.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

@Mixin(priority = 1111, value = GuiIngame.class)
public class MixinGuiIngame {

    @Inject(method = "renderGameOverlay", at = @At("HEAD"))
    private void renderGameOverlay(float partialTicks, CallbackInfo ci) {
        if (!System.getProperty("os.name").toLowerCase().contains("mac")) {
            PostProcessing postProcessing = Arsenic.getArsenic().getModuleManager().getModuleByClass(PostProcessing.class);
            if (postProcessing.isEnabled()) {
                postProcessing.blurScreen();
            }
        }
        Arsenic.getInstance().getEventManager().post(new EventRender2D(partialTicks, new ScaledResolution(Minecraft.getMinecraft())));
    }

}
