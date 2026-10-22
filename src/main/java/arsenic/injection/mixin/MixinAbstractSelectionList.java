package arsenic.injection.mixin;

import arsenic.gui.click.GuiStyle;
import arsenic.main.Arsenic;
import arsenic.module.impl.visual.custommainmenu.MenuTheme;
import arsenic.utils.render.RenderContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scrolling lists (worlds, servers, packs, options): a tinted pane with accent edges instead of the menu texture. */
@Mixin(AbstractSelectionList.class)
public abstract class MixinAbstractSelectionList {

    private static boolean themed() {
        return Arsenic.getArsenic() != null && GuiStyle.customMenus();
    }

    @Inject(method = "extractListBackground", at = @At("HEAD"), cancellable = true)
    private void arsenic$listBackground(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (!themed())
            return;
        ci.cancel();
        AbstractWidget self = (AbstractWidget) (Object) this;
        try (RenderContext ignored = RenderContext.begin(graphics)) {
            MenuTheme.drawListPane(self.getX(), self.getY(), self.getRight(), self.getBottom());
        }
    }

    @Inject(method = "extractListSeparators", at = @At("HEAD"), cancellable = true)
    private void arsenic$listSeparators(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (!themed())
            return;
        ci.cancel();
        AbstractWidget self = (AbstractWidget) (Object) this;
        try (RenderContext ignored = RenderContext.begin(graphics)) {
            MenuTheme.drawListEdge(self.getX(), self.getRight(), self.getY() - 2, true);
            MenuTheme.drawListEdge(self.getX(), self.getRight(), self.getBottom(), false);
        }
    }
}
