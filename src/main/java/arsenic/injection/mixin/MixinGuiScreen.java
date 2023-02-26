package arsenic.injection.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import arsenic.main.Arsenic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

@Mixin(value = GuiScreen.class)
public class MixinGuiScreen {

    @Shadow
    Minecraft mc;

    @Inject(method = "sendChatMessage(Ljava/lang/String;Z)V", at = @At(value = "HEAD"), cancellable = true)
    public void sendChatMessage(String msg, boolean addToChat, CallbackInfo ci) {
        if (msg.startsWith(".")) {
            Arsenic.getInstance().getCommandManager().executeCommand(msg);
            mc.ingameGUI.getChatGUI().addToSentMessages(msg);
            ci.cancel();
        }
    }

}
