package arsenic.injection.mixin;

import arsenic.event.impl.EventDisplayGuiScreen;
import arsenic.event.impl.EventKey;
import arsenic.main.Arsenic;
import arsenic.main.MinecraftAPI;
import arsenic.module.ModuleManager;
import arsenic.module.impl.world.ChestStealer;
import arsenic.module.impl.world.FastPlace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiChest;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(priority = 1111, value = Minecraft.class)
public class MixinMinecraft {

    @Shadow
    private static Minecraft theMinecraft;
    @Shadow
    private int rightClickDelayTimer;

    @ModifyArg(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/settings/KeyBinding;setKeyBindState(IZ)V"), index = 0)
    public int getKeybind(int p_setKeyBindState_0_) {
        MinecraftAPI.KEY_CODE = p_setKeyBindState_0_;
        return p_setKeyBindState_0_;
    }

    @Inject(method = "runTick", at = @At(value = "HEAD"))
    public void runTick(CallbackInfo ci) {
        MinecraftAPI.KEY_CODE = null;
    }

    @Redirect(method = "runTick", at = @At(value = "INVOKE", target = "Lorg/lwjgl/input/Keyboard;getEventKeyState()Z", ordinal = 2))
    public boolean redirectGetKeyState() {
        boolean state = Keyboard.getEventKeyState();
        if (state && MinecraftAPI.KEY_CODE != null && theMinecraft.currentScreen == null) {
            EventKey event = new EventKey(MinecraftAPI.KEY_CODE);
            Arsenic.getInstance().getEventManager().post(event);
            MinecraftAPI.KEY_CODE = null;
        }
        return state;
    }



    @Inject(method = "displayGuiScreen", at = @At(value = "RETURN"), cancellable = true)
    public void displayGuiScreen(GuiScreen guiScreenIn, CallbackInfo ci) {
        EventDisplayGuiScreen event = new EventDisplayGuiScreen(guiScreenIn);
        Arsenic.getArsenic().getEventManager().post(event);
        if(event.isCancelled())
            ci.cancel();
    }

    @Inject(method = "rightClickMouse", at = @At("RETURN"))
    public void rightClickMouse(CallbackInfo ci) {
        FastPlace fastPlace = (FastPlace) ModuleManager.Modules.FASTPLACE.getModule();
        if(!fastPlace.isEnabled())
            return;
        rightClickDelayTimer = fastPlace.getTickDelay();
    }

}
