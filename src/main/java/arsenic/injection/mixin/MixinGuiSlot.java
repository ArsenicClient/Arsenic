package arsenic.injection.mixin;

import arsenic.runtime.hooks.GuiHooks;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.renderer.Tessellator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** World, server, language and resource pack lists lose their dirt texture for the ocean theme. */
@Mixin(GuiSlot.class)
public abstract class MixinGuiSlot {

    // added by Forge, so it has no obfuscated name to remap
    @Inject(method = "drawContainerBackground", at = @At("HEAD"), cancellable = true, remap = false)
    private void arsenic$pane(Tessellator tessellator, CallbackInfo ci) {
        if (GuiHooks.slotContainerBackground((GuiSlot) (Object) this, tessellator))
            ci.cancel();
    }

    @Inject(method = "overlayBackground", at = @At("HEAD"), cancellable = true)
    private void arsenic$bar(int startY, int endY, int startAlpha, int endAlpha, CallbackInfo ci) {
        if (GuiHooks.slotOverlayBackground((GuiSlot) (Object) this, startY, endY, startAlpha, endAlpha))
            ci.cancel();
    }
}
