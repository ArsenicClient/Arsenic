package arsenic.injection.mixin;

import arsenic.module.impl.visual.custommainmenu.MenuTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every vanilla button is drawn as a themed pill (see {@link MenuTheme#drawButton}). */
@Mixin(GuiButton.class)
public abstract class MixinGuiButton {

    @Shadow
    public boolean visible;

    @Shadow
    protected boolean hovered;

    @Shadow
    protected abstract void mouseDragged(Minecraft mc, int mouseX, int mouseY);

    @Inject(method = "drawButton", at = @At("HEAD"), cancellable = true)
    private void arsenic$themedButton(Minecraft mc, int mouseX, int mouseY, CallbackInfo ci) {
        if (visible) {
            hovered = MenuTheme.drawButton((GuiButton) (Object) this, mc, mouseX, mouseY);
            // sliders do their dragging (and used to draw their knob) here
            mouseDragged(mc, mouseX, mouseY);
        }
        ci.cancel();
    }
}
