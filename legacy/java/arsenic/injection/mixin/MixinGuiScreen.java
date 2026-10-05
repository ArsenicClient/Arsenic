package arsenic.injection.mixin;

import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;
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
    public Minecraft mc;

    @Shadow
    public int width;

    @Shadow
    public int height;

    // the dirt tile and the plain dark veil are both replaced by the ocean theme
    @Inject(method = "drawWorldBackground", at = @At("HEAD"), cancellable = true)
    private void arsenic$worldBackground(int tint, CallbackInfo ci) {
        arsenic.module.impl.visual.custommainmenu.MenuTheme.drawBackground(width, height);
        ci.cancel();
    }

    @Inject(method = "drawBackground", at = @At("HEAD"), cancellable = true)
    private void arsenic$dirtBackground(int tint, CallbackInfo ci) {
        arsenic.module.impl.visual.custommainmenu.MenuTheme.drawBackground(width, height);
        ci.cancel();
    }

    @Inject(method = "sendChatMessage(Ljava/lang/String;Z)V", at = @At(value = "HEAD"), cancellable = true)
    public void sendChatMessage(String msg, boolean addToChat, CallbackInfo ci) {
        if (msg.startsWith(".")) {
            Arsenic.getInstance().getCommandManager().executeCommand(msg);
            mc.ingameGUI.getChatGUI().addToSentMessages(msg);
            ci.cancel();
        }
    }

}
