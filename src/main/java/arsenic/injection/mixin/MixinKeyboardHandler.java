package arsenic.injection.mixin;

import arsenic.event.impl.EventKey;
import arsenic.main.Arsenic;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class MixinKeyboardHandler {

    /** Fires {@link EventKey} with the GLFW key code for presses made in-game (no screen open). */
    @Inject(method = "keyPress", at = @At("HEAD"))
    private void arsenic$keyPress(long handle, int action, KeyEvent event, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (action != 1 || handle != mc.getWindow().handle() || mc.gui.screen() != null)
            return;
        Arsenic.getInstance().getEventManager().post(new EventKey(event.key()));
    }
}
