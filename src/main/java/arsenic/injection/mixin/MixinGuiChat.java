package arsenic.injection.mixin;

import arsenic.main.Arsenic;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiChat.class)
public class MixinGuiChat extends GuiScreen {

    @Shadow protected GuiTextField inputField;

    /**
     * @author kv
     * @reason because green
     */
    @Inject(method = "keyTyped", at = @At("RETURN"))
    public void keyTypedReturn(char typedChar, int keyCode, CallbackInfo ci) {
        if(inputField.getText().startsWith(".")) {
            inputField.setTextColor(0x70FF70);
        } else {
            inputField.setTextColor(0xE0E0E0);
        }
    }

    @Inject(method = "keyTyped", at = @At("HEAD"), cancellable = true)
    public void keyTypedHead(char typedChar, int keyCode, CallbackInfo ci) {
        if (keyCode == 15) {
            inputField.writeText(Arsenic.getArsenic().getCommandManager().getAutoCompletion(inputField.getText()));
            ci.cancel();
        }
    }
}
