package arsenic.injection.mixin;

import arsenic.main.Arsenic;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiScreen.class)
public class MixinGuiScreen {

    @Inject(method = "sendChatMessage(Ljava/lang/String;Z)V", at = @At(value = "HEAD"), cancellable = true)
    public void sendChatMessage(String msg, boolean addToChat, CallbackInfo ci) {
        if(msg.startsWith(".")) {
            Arsenic.getInstance().getCommandManagerManager().executeCommand(msg);
            ci.cancel();
        }
    }

}
