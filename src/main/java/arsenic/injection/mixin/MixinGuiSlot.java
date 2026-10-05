package arsenic.injection.mixin;

import arsenic.module.impl.visual.custommainmenu.MenuTheme;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.renderer.Tessellator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** World, server, language and resource pack lists lose their dirt texture for the ocean theme. */
@Mixin(GuiSlot.class)
public abstract class MixinGuiSlot {

    @Shadow
    public int left;
    @Shadow
    public int right;
    @Shadow
    public int top;
    @Shadow
    public int bottom;
    @Shadow
    public int width;

    // added by Forge, so it has no obfuscated name to remap
    @Inject(method = "drawContainerBackground", at = @At("HEAD"), cancellable = true, remap = false)
    private void arsenic$pane(Tessellator tessellator, CallbackInfo ci) {
        MenuTheme.drawListPane(left, top, right, bottom);
        ci.cancel();
    }

    @Inject(method = "overlayBackground", at = @At("HEAD"), cancellable = true)
    private void arsenic$bar(int startY, int endY, int startAlpha, int endAlpha, CallbackInfo ci) {
        MenuTheme.drawListBar(left, left + width, startY, endY);
        ci.cancel();
    }
}
