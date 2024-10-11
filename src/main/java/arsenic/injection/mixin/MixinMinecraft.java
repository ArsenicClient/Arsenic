package arsenic.injection.mixin;

import arsenic.event.impl.EventDisplayGuiScreen;
import arsenic.event.impl.EventGameLoop;
import arsenic.event.impl.EventKey;
import arsenic.event.impl.EventRunTick;
import arsenic.main.Arsenic;
import arsenic.main.MinecraftAPI;
import arsenic.module.impl.player.FastPlace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.GameSettings;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(priority = 1111, value = Minecraft.class)
public abstract class MixinMinecraft {

    @Shadow
    protected abstract void clickMouse();
    @Shadow
    private static Minecraft theMinecraft;
    @Shadow
    private int rightClickDelayTimer;

    @Shadow
    public GameSettings gameSettings;
    
    @Shadow 
    private int leftClickCounter;


    @ModifyArg(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/settings/KeyBinding;setKeyBindState(IZ)V"), index = 0)
    public int getKeybind(int p_setKeyBindState_0_) {
        MinecraftAPI.KEY_CODE = p_setKeyBindState_0_;
        return p_setKeyBindState_0_;
    }

    @Inject(method = "runTick", at = @At(value = "HEAD"))
    public void runTick(CallbackInfo ci) {
        Arsenic.getInstance().getEventManager().post(new EventGameLoop());
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



    @Inject(method = "displayGuiScreen", at = @At(value = "RETURN"))
    public void displayGuiScreen(GuiScreen guiScreenIn, CallbackInfo ci) {
        EventDisplayGuiScreen event = new EventDisplayGuiScreen(guiScreenIn);
        Arsenic.getArsenic().getEventManager().post(event);
    }

    @Inject(method = "runTick", at = @At("HEAD"))
    public void onRunTick(CallbackInfo ci) {
        Arsenic.getInstance().getEventManager().post(new EventRunTick());
    }

    @Inject(method = "rightClickMouse", at = @At("RETURN"))
    public void rightClickMouse(CallbackInfo ci) {
        FastPlace fastPlace = Arsenic.getArsenic().getModuleManager().getModuleByClass(FastPlace.class);
         if(!fastPlace.isEnabled() ) return;

        rightClickDelayTimer = fastPlace.getTickDelay();

    }

    @Inject(method = "clickMouse", at = @At("HEAD"))
    public void clickMoose(CallbackInfo ci) { //better hitreg.
        this.leftClickCounter = 0;
    }

}
