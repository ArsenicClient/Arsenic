package arsenic.injection.mixin;

import arsenic.runtime.hooks.GuiHooks;
import net.minecraft.client.gui.GuiOptionSlider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Replaces the two textured halves of the vanilla slider knob with one themed pill. */
@Mixin(GuiOptionSlider.class)
public abstract class MixinGuiOptionSlider {

    @Redirect(method = "mouseDragged", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiOptionSlider;drawTexturedModalRect(IIIIII)V", ordinal = 0))
    private void arsenic$knob(GuiOptionSlider self, int x, int y, int u, int v, int w, int h) {
        GuiHooks.sliderKnob(self, x, y, u, v, w, h);
    }

    @Redirect(method = "mouseDragged", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiOptionSlider;drawTexturedModalRect(IIIIII)V", ordinal = 1))
    private void arsenic$knobSecondHalf(GuiOptionSlider self, int x, int y, int u, int v, int w, int h) {
        GuiHooks.sliderKnobSecondHalf(self, x, y, u, v, w, h);
    }
}
