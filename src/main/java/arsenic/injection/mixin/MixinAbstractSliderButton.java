package arsenic.injection.mixin;

import arsenic.gui.click.GuiStyle;
import arsenic.main.Arsenic;
import arsenic.module.impl.visual.custommainmenu.MenuTheme;
import arsenic.utils.render.RenderContext;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A vanilla slider: the track becomes a themed pill, the handle a filled bar and an accent knob. */
@Mixin(AbstractSliderButton.class)
public abstract class MixinAbstractSliderButton {

    private static final String BLIT_SPRITE =
            "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V";

    private static boolean themed() {
        return Arsenic.getArsenic() != null && GuiStyle.customMenus();
    }

    @WrapOperation(method = "extractWidgetRenderState", at = @At(value = "INVOKE", target = BLIT_SPRITE, ordinal = 0))
    private void arsenic$track(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h, int colour, Operation<Void> original) {
        if (!themed()) {
            original.call(graphics, pipeline, sprite, x, y, w, h, colour);
            return;
        }
        AbstractWidget self = (AbstractWidget) (Object) this;
        try (RenderContext ignored = RenderContext.begin(graphics)) {
            MenuTheme.drawButton(self, self.isHoveredOrFocused());
        }
    }

    @WrapOperation(method = "extractWidgetRenderState", at = @At(value = "INVOKE", target = BLIT_SPRITE, ordinal = 1))
    private void arsenic$handle(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h, int colour, Operation<Void> original) {
        if (!themed()) {
            original.call(graphics, pipeline, sprite, x, y, w, h, colour);
            return;
        }
        AbstractWidget self = (AbstractWidget) (Object) this;
        try (RenderContext ignored = RenderContext.begin(graphics)) {
            MenuTheme.drawSlider(self.getX(), x, y, h);
        }
    }
}
