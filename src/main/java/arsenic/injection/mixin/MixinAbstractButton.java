package arsenic.injection.mixin;

import arsenic.gui.click.GuiStyle;
import arsenic.main.Arsenic;
import arsenic.module.impl.visual.custommainmenu.MenuTheme;
import arsenic.utils.render.RenderContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every vanilla button is drawn as a themed pill; the label is still drawn by the game. */
@Mixin(AbstractButton.class)
public abstract class MixinAbstractButton {

    @Inject(method = "extractDefaultSprite", at = @At("HEAD"), cancellable = true)
    private void arsenic$themedButton(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (Arsenic.getArsenic() == null || !GuiStyle.customMenus())
            return;
        ci.cancel();
        AbstractWidget self = (AbstractWidget) (Object) this;
        try (RenderContext ignored = RenderContext.begin(graphics)) {
            MenuTheme.drawButton(self, self.isHoveredOrFocused());
        }
    }
}
