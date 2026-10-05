package arsenic.injection.mixin;

import arsenic.event.impl.EventDisplayGuiScreen;
import arsenic.main.Arsenic;
import arsenic.module.impl.visual.custommainmenu.CustomMenu;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Screens moved from {@code Minecraft#displayGuiScreen} onto {@code Gui#setScreen}. */
@Mixin(Gui.class)
public abstract class MixinGui {

    @Inject(method = "setScreen", at = @At("RETURN"))
    private void arsenic$setScreen(Screen screen, CallbackInfo ci) {
        Arsenic arsenic = Arsenic.getArsenic();
        if (arsenic == null || arsenic.getModuleManager() == null)
            return;
        if (screen instanceof TitleScreen && !CustomMenu.consumeVanillaRequest())
            CustomMenu.display();
        arsenic.getEventManager().post(new EventDisplayGuiScreen(screen));
    }
}
