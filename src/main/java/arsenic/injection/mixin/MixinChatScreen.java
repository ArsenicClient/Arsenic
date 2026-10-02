package arsenic.injection.mixin;

import arsenic.main.Arsenic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import com.mojang.blaze3d.platform.InputConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client commands typed with a {@code .} prefix: they are run locally instead of being sent, the
 * input is tinted with the theme colour, and the best completion is drawn as ghost text that Tab
 * accepts.
 */
@Mixin(ChatScreen.class)
public abstract class MixinChatScreen {

    @Shadow
    protected EditBox input;

    @Unique private String arsenic$trimmedCompletion = "";
    @Unique private String arsenic$lastArg = "";
    @Unique private boolean arsenic$lastArgValid;
    @Unique private String arsenic$lastValue;

    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void arsenic$command(String msg, boolean addToRecent, CallbackInfo ci) {
        String trimmed = msg.trim();
        if (!trimmed.startsWith("."))
            return;
        Arsenic.getInstance().getCommandManager().executeCommand(trimmed);
        Minecraft.getInstance().gui.hud.getChat().addRecentChat(trimmed);
        ci.cancel();
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void arsenic$tabComplete(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        String text = input.getValue();
        if (!text.startsWith(".") || event.key() != InputConstants.KEY_TAB)
            return;
        input.setValue(text.substring(0, text.lastIndexOf(text.contains(" ") ? ' ' : '.') + 1));
        input.insertText(Arsenic.getArsenic().getCommandManager().getAutoCompletion());
        arsenic$lastValue = null;
        cir.setReturnValue(true);
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void arsenic$render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        String text = input.getValue();
        if (!text.startsWith(".")) {
            input.setTextColor(0xFFE0E0E0);
            arsenic$lastValue = null;
            return;
        }
        input.setTextColor(0xFF000000 | Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor());

        if (!text.equals(arsenic$lastValue)) {
            arsenic$lastValue = text;
            Arsenic.getArsenic().getCommandManager().updateAutoCompletions(text);
            String completion = Arsenic.getArsenic().getCommandManager().getAutoCompletionWithoutRotation();
            arsenic$lastArg = text.substring(text.lastIndexOf(text.contains(" ") ? ' ' : '.') + 1);
            arsenic$trimmedCompletion = completion.toLowerCase().replaceFirst(java.util.regex.Pattern.quote(arsenic$lastArg.toLowerCase()), "");
            arsenic$lastArgValid = (arsenic$trimmedCompletion.length() == completion.length() || completion.length() < arsenic$lastArg.length())
                    && !arsenic$lastArg.isEmpty();
        }

        var font = Minecraft.getInstance().font;
        int textX = input.getX() + 4;
        int textY = input.getY() + (input.getHeight() - 8) / 2;
        if (arsenic$lastArgValid) {
            int x = textX + font.width(text.substring(0, text.length() - arsenic$lastArg.length()));
            graphics.text(font, arsenic$trimmedCompletion, x, textY - (int) (font.lineHeight * 1.2f), 0xFF999999, true);
        } else {
            graphics.text(font, arsenic$trimmedCompletion, textX + font.width(text), textY, 0xFF999999, true);
        }
    }
}
