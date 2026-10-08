package arsenic.injection.mixin;

import arsenic.runtime.hooks.GuiHooks;
import net.minecraft.client.gui.GuiChat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hook logic lives in {@link GuiHooks}, shared with the injected client. */
@Mixin(value = GuiChat.class)
public class MixinGuiChat {

    @Inject(method = "keyTyped", at = @At("RETURN"))
    public void keyTypedReturn(char typedChar, int keyCode, CallbackInfo ci) {
        GuiHooks.chatKeyTypedReturn((GuiChat) (Object) this, typedChar, keyCode);
    }

    @Inject(method = "keyTyped", at = @At("HEAD"), cancellable = true)
    public void keyTypedHead(char typedChar, int keyCode, CallbackInfo ci) {
        if (GuiHooks.chatKeyTypedHead((GuiChat) (Object) this, typedChar, keyCode))
            ci.cancel();
    }

    @Inject(method = "drawScreen", at = @At("RETURN"))
    public void drawScreen(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        GuiHooks.chatDrawScreenReturn((GuiChat) (Object) this, mouseX, mouseY, partialTicks);
    }
}
