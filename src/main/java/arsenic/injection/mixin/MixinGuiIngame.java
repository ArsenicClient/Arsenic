package arsenic.injection.mixin;

import arsenic.main.Arsenic;
import arsenic.event.impl.EventRender2D;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(priority = 1111, value = GuiIngame.class)
public class MixinGuiIngame {

    @Inject(method = "renderTooltip", at = @At("RETURN"))
    private void renderTooltip(ScaledResolution sr, float partialTicks, CallbackInfo ci) {
        Arsenic.getInstance().getEventManager().post(new EventRender2D(partialTicks, sr));
    }

}
