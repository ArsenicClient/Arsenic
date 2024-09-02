package arsenic.injection.mixin;

import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.client.gui.GuiSpectator;

@Mixin(priority = 1111, value = GuiSpectator.class)
public class MixinGuiSpectator {
    //why does this exist? why is it being called here?
    /*@Inject(method = "renderTooltip", at = @At("RETURN"))
    private void renderTooltip(ScaledResolution sr, float partialTicks, CallbackInfo ci) {
        Arsenic.getInstance().getEventManager().post(new EventRender2D(partialTicks, sr));
    }*/

}
