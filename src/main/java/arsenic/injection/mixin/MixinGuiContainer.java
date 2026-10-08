package arsenic.injection.mixin;

import arsenic.runtime.hooks.GuiHooks;
import net.minecraft.client.gui.inventory.GuiContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hook logic lives in {@link GuiHooks}, shared with the injected client. */
@Mixin(GuiContainer.class)
public abstract class MixinGuiContainer {

    @Inject(method = "initGui", at = @At("TAIL"))
    private void addKillAuraButton(CallbackInfo ci) {
        GuiHooks.containerInitGuiTail((GuiContainer) (Object) this);
    }

    @Inject(method = "drawScreen", at = @At("HEAD"))
    private void refreshKillAuraLabel(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        GuiHooks.containerDrawScreenHead((GuiContainer) (Object) this, mouseX, mouseY, partialTicks);
    }

    // GuiContainer doesn't declare actionPerformed, so catch the click here instead
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void killAuraButtonClicked(int mouseX, int mouseY, int mouseButton, CallbackInfo ci) {
        if (GuiHooks.containerMouseClickedHead((GuiContainer) (Object) this, mouseX, mouseY, mouseButton))
            ci.cancel();
    }
}
