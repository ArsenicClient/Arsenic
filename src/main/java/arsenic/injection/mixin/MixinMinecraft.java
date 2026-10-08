package arsenic.injection.mixin;

import arsenic.runtime.hooks.MinecraftHooks;
import arsenic.main.MinecraftAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.shader.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hook logic lives in {@link MinecraftHooks}, shared with the injected client. */
@Mixin(priority = 1111, value = Minecraft.class)
public abstract class MixinMinecraft {

    // lets RenderTargets point mc.getFramebuffer() at a redirect target (recorder overlay, SilentView)
    @Inject(method = "getFramebuffer", at = @At("HEAD"), cancellable = true)
    private void arsenic$redirectFramebuffer(CallbackInfoReturnable<Framebuffer> cir) {
        Framebuffer redirect = MinecraftHooks.getFramebuffer((Minecraft) (Object) this);
        if (redirect != null)
            cir.setReturnValue(redirect);
    }

    @ModifyArg(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/settings/KeyBinding;setKeyBindState(IZ)V"), index = 0)
    public int getKeybind(int p_setKeyBindState_0_) {
        MinecraftAPI.KEY_CODE = p_setKeyBindState_0_;
        return p_setKeyBindState_0_;
    }

    @Inject(method = "runTick", at = @At(value = "HEAD"))
    public void runTick(CallbackInfo ci) {
        MinecraftHooks.runTickHead((Minecraft) (Object) this);
    }

    @Redirect(method = "runTick", at = @At(value = "INVOKE", target = "Lorg/lwjgl/input/Keyboard;getEventKeyState()Z", ordinal = 2))
    public boolean redirectGetKeyState() {
        return MinecraftHooks.getEventKeyState();
    }

    @Redirect(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/settings/KeyBinding;isPressed()Z"))
    public boolean redirectIsPressed(KeyBinding keyBinding) {
        return MinecraftHooks.isPressed(keyBinding);
    }

    @Redirect(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/settings/KeyBinding;isKeyDown()Z"))
    public boolean redirectIsKeyDown(KeyBinding keyBinding) {
        return MinecraftHooks.isKeyDown(keyBinding);
    }

    @Inject(method = "displayGuiScreen", at = @At(value = "HEAD"))
    public void arsenic$captureForTransition(GuiScreen guiScreenIn, CallbackInfo ci) {
        MinecraftHooks.displayGuiScreenHead((Minecraft) (Object) this, guiScreenIn);
    }

    @Inject(method = "displayGuiScreen", at = @At(value = "RETURN"))
    public void displayGuiScreen(GuiScreen guiScreenIn, CallbackInfo ci) {
        MinecraftHooks.displayGuiScreenReturn((Minecraft) (Object) this, guiScreenIn);
    }

    @Inject(method = "rightClickMouse", at = @At("RETURN"))
    public void rightClickMouse(CallbackInfo ci) {
        MinecraftHooks.rightClickMouseReturn((Minecraft) (Object) this);
    }

    @Inject(method = "clickMouse", at = @At("HEAD"))
    public void clickMoose(CallbackInfo ci) {
        MinecraftHooks.clickMouseHead((Minecraft) (Object) this);
    }

    @Inject(method = "clickMouse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/EntityPlayerSP;swingItem()V"))
    public void onSwingItem(CallbackInfo ci) {
        MinecraftHooks.clickMouseBeforeSwing((Minecraft) (Object) this);
    }
}
