package arsenic.injection.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import arsenic.event.impl.EventRender2D;
import arsenic.main.Arsenic;
import net.minecraft.client.gui.GuiSpectator;
import net.minecraft.client.gui.ScaledResolution;

@Mixin(priority = 1111, value = GuiSpectator.class)
public class MixinGuiSpectator {
    //why does this exist? why is it being called here?
    /*@Inject(method = "renderTooltip", at = @At("RETURN"))
    private void renderTooltip(ScaledResolution sr, float partialTicks, CallbackInfo ci) {
        Arsenic.getInstance().getEventManager().post(new EventRender2D(partialTicks, sr));
    }*/

}
