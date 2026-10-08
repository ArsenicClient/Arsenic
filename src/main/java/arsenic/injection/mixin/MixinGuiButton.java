package arsenic.injection.mixin;

import arsenic.runtime.hooks.GuiHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every vanilla button is drawn as a themed pill (see {@link GuiHooks#drawButton}). */
@Mixin(GuiButton.class)
public abstract class MixinGuiButton {

    @Inject(method = "drawButton", at = @At("HEAD"), cancellable = true)
    private void arsenic$themedButton(Minecraft mc, int mouseX, int mouseY, CallbackInfo ci) {
        if (GuiHooks.drawButton((GuiButton) (Object) this, mc, mouseX, mouseY))
            ci.cancel();
    }
}
