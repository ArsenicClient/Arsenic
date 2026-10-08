package arsenic.injection.mixin;

import arsenic.runtime.hooks.GuiHooks;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.ForgeHooksClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Wraps every screen draw with the cross-fade from the previous screen. */
@Mixin(value = ForgeHooksClient.class, remap = false)
public abstract class MixinForgeHooksClient {

    @Inject(method = "drawScreen", at = @At("HEAD"), remap = false)
    private static void arsenic$before(GuiScreen screen, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        GuiHooks.forgeDrawScreenHead(screen, mouseX, mouseY, partialTicks);
    }

    @Inject(method = "drawScreen", at = @At("RETURN"), remap = false)
    private static void arsenic$after(GuiScreen screen, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        GuiHooks.forgeDrawScreenReturn(screen, mouseX, mouseY, partialTicks);
    }
}
