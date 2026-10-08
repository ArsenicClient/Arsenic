package arsenic.injection.mixin;

import arsenic.runtime.hooks.GuiHooks;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hook logic lives in {@link GuiHooks}, shared with the injected client. */
@Mixin(value = GuiScreen.class)
public class MixinGuiScreen {

    // the dirt tile and the plain dark veil are both replaced by the ocean theme
    @Inject(method = "drawWorldBackground", at = @At("HEAD"), cancellable = true)
    private void arsenic$worldBackground(int tint, CallbackInfo ci) {
        if (GuiHooks.drawWorldBackground((GuiScreen) (Object) this, tint))
            ci.cancel();
    }

    @Inject(method = "drawBackground", at = @At("HEAD"), cancellable = true)
    private void arsenic$dirtBackground(int tint, CallbackInfo ci) {
        if (GuiHooks.drawBackground((GuiScreen) (Object) this, tint))
            ci.cancel();
    }

    @Inject(method = "sendChatMessage(Ljava/lang/String;Z)V", at = @At(value = "HEAD"), cancellable = true)
    public void sendChatMessage(String msg, boolean addToChat, CallbackInfo ci) {
        if (GuiHooks.sendChatMessage((GuiScreen) (Object) this, msg, addToChat))
            ci.cancel();
    }
}
