package arsenic.injection.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import arsenic.main.Arsenic;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

@Mixin(value = GuiChat.class)
public class MixinGuiChat extends GuiScreen {

    @Shadow
    protected GuiTextField inputField;

    private String trimmedAutoCompletion;

    /**
     * @author kv
     * @reason because green
     */
    @Inject(method = "keyTyped", at = @At("RETURN"))
    public void keyTypedReturn(char typedChar, int keyCode, CallbackInfo ci) {
        if (inputField.getText().startsWith(".")) {
            inputField.setTextColor(0x70FF70);

            if (keyCode != 15 && keyCode != 1) {
                Arsenic.getArsenic().getCommandManager().updateAutoCompletions(inputField.getText());
            }

            String latestAutoComplete = Arsenic.getArsenic().getCommandManager().getAutoCompletionWithoutRotation();
            String lastArg = inputField.getText()
                    .substring(inputField.getText().lastIndexOf((inputField.getText().contains(" ") ? ' ' : '.')) + 1);
            trimmedAutoCompletion = latestAutoComplete.length() > lastArg.length()
                    ? latestAutoComplete.toLowerCase().replaceFirst(lastArg.toLowerCase(), "")
                    : "";

        } else {
            inputField.setTextColor(0xE0E0E0);
        }
    }

    @Inject(method = "keyTyped", at = @At("HEAD"), cancellable = true)
    public void keyTypedHead(char typedChar, int keyCode, CallbackInfo ci) {
        if (inputField.getText().startsWith(".")) {
            if (keyCode == 15) {
                inputField.setText(inputField.getText().substring(0,
                        inputField.getText().lastIndexOf((inputField.getText().contains(" ") ? ' ' : '.')) + 1));
                inputField.writeText(Arsenic.getArsenic().getCommandManager().getAutoCompletion());
                keyTypedReturn(typedChar, keyCode, ci);
                ci.cancel();
            }
        }
    }

    @Inject(method = "drawScreen", at = @At("RETURN"))
    public void drawScreen(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (inputField.getText().startsWith(".")) {
            mc.fontRendererObj.drawStringWithShadow(trimmedAutoCompletion,
                    inputField.xPosition + mc.fontRendererObj.getStringWidth(inputField.getText()),
                    inputField.yPosition, 0x999999);
        }
    }

}
