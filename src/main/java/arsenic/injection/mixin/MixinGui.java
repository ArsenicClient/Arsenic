package arsenic.injection.mixin;

import arsenic.event.impl.EventDisplayGuiScreen;
import arsenic.gui.click.GuiStyle;
import arsenic.main.Arsenic;
import arsenic.module.impl.visual.custommainmenu.CustomMenu;
import arsenic.module.impl.visual.custommainmenu.ScreenTransition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Screens moved from {@code Minecraft#displayGuiScreen} onto {@code Gui#setScreen}. */
@Mixin(Gui.class)
public abstract class MixinGui {

    @Shadow private Screen screen;
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void arsenic$captureTransition(Screen screen, CallbackInfo ci) {
        if (screen != this.screen && Arsenic.getArsenic() != null && GuiStyle.customMenus())
            ScreenTransition.capture(minecraft);
    }

    @Inject(method = "setScreen", at = @At("RETURN"))
    private void arsenic$setScreen(Screen screen, CallbackInfo ci) {
        Arsenic arsenic = Arsenic.getArsenic();
        if (arsenic == null || arsenic.getModuleManager() == null)
            return;
        if (screen instanceof TitleScreen && !CustomMenu.consumeVanillaRequest())
            CustomMenu.display();
        arsenic.getEventManager().post(new EventDisplayGuiScreen(screen));
    }

    /** Every screen draw is wrapped with the cross-fade from the previous screen. */
    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void arsenic$screenTransition(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, Operation<Void> original) {
        if (Arsenic.getArsenic() == null || !GuiStyle.customMenus()) {
            original.call(screen, graphics, mouseX, mouseY, a);
            return;
        }
        ScreenTransition.beginContent(graphics, graphics.guiWidth(), graphics.guiHeight());
        try {
            original.call(screen, graphics, mouseX, mouseY, a);
        } finally {
            ScreenTransition.endContent(graphics, graphics.guiWidth(), graphics.guiHeight());
        }
    }

    /** Closing a screen back into the game fades the old frame out over the HUD. */
    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Hud;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"))
    private void arsenic$hudTransition(Hud hud, GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, Operation<Void> original) {
        original.call(hud, graphics, deltaTracker);
        if (screen == null && Arsenic.getArsenic() != null && GuiStyle.customMenus())
            ScreenTransition.drawOverlay(graphics, graphics.guiWidth(), graphics.guiHeight());
    }
}
