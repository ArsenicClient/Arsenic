package arsenic.injection.mixin;

import arsenic.module.ModuleManager;
import arsenic.module.impl.world.ChestStealer;
import net.minecraft.client.gui.inventory.GuiContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiContainer.class)
public class MixinGuiContainer {

    @Inject(method = "drawScreen", at = @At("HEAD"), cancellable = true)
    public void drawScreen(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        ChestStealer chestStealer = (ChestStealer) ModuleManager.Modules.CHESTSTEALER.getModule();
        if(chestStealer.isEnabled() && chestStealer.hideGui.getValue() && chestStealer.inChest)
            ci.cancel();
    }
}
