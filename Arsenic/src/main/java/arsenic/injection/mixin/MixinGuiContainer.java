package arsenic.injection.mixin;

import arsenic.main.Arsenic;
import arsenic.module.impl.player.ChestStealer;
import net.minecraft.client.gui.inventory.GuiContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiContainer.class)
public class MixinGuiContainer {

    @Inject(method = "drawScreen", at = @At("HEAD"), cancellable = true)
    public void drawScreen(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        ChestStealer chestStealer = Arsenic.getArsenic().getModuleManager().getModuleByClass(ChestStealer.class);
        if(chestStealer.isEnabled() && chestStealer.hideGui.getValue() && chestStealer.isInChest()) {
            chestStealer.draw((GuiContainer) (Object) this);
            ci.cancel();
        }
    }
}
