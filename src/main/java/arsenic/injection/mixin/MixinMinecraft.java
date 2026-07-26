package arsenic.injection.mixin;

import arsenic.event.impl.EventDisplayGuiScreen;
import arsenic.event.impl.EventGameLoop;
import arsenic.event.impl.EventKey;
import arsenic.event.impl.EventRunTick;
import arsenic.main.Arsenic;
import arsenic.main.MinecraftAPI;
import arsenic.module.impl.ghost.Clicker;
import arsenic.module.impl.ghost.Hitflick;
import arsenic.module.impl.ghost.NoHitDelay;
import arsenic.module.impl.player.FastPlace;
import arsenic.module.impl.visual.custommainmenu.CustomMenu;
import arsenic.utils.minecraft.PlayerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
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



    /**
     * Swallows the player's own attack/use presses while a silent rotation asked for input to be
     * blocked. The original {@code isPressed()} is still called so the queued press count is
     * drained — otherwise every press held back would fire in a burst once blocking ends.
     * Other keybinds (inventory, drop, chat, ...) pass through untouched, and direct client-side
     * calls to {@code clickMouse()}/{@code rightClickMouse()} are unaffected.
     */
    @Redirect(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/settings/KeyBinding;isPressed()Z"))
    public boolean redirectIsPressed(KeyBinding keyBinding) {
        if (!shouldBlockInput(keyBinding))
            return keyBinding.isPressed();
        // drain the whole queue, not just one press
        while (keyBinding.isPressed()) { }
        return false;
    }

    /**
     * Same as {@link #redirectIsPressed(KeyBinding)} but for the held-down reads — continuous block
     * breaking ({@code sendClickBlockToController}) and item-use repeat.
     */
    @Redirect(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/settings/KeyBinding;isKeyDown()Z"))
    public boolean redirectIsKeyDown(KeyBinding keyBinding) {
        return keyBinding.isKeyDown() && !shouldBlockInput(keyBinding);
    }

    private boolean shouldBlockInput(KeyBinding keyBinding) {
        if (keyBinding != gameSettings.keyBindAttack && keyBinding != gameSettings.keyBindUseItem)
            return false;
        return Arsenic.getArsenic().getSilentRotationManager().isBlockingUserInput();
    }

    @Inject(method = "displayGuiScreen", at = @At(value = "RETURN"))
    public void displayGuiScreen(GuiScreen guiScreenIn, CallbackInfo ci) {
        CustomMenu customMenu = Arsenic.getArsenic().getModuleManager().getModuleByClass(CustomMenu.class);
        if(guiScreenIn instanceof GuiMainMenu && customMenu.isEnabled()) {
            customMenu.display();
        }
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
        if(Arsenic.getArsenic().getModuleManager().getModuleByClass(NoHitDelay.class).isEnabled() || Arsenic.getArsenic().getModuleManager().getModuleByClass(Clicker.class).isEnabled())
            this.leftClickCounter = 0;
    }

    @Inject(method = "clickMouse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/EntityPlayerSP;swingItem()V"))
    public void onSwingItem(CallbackInfo ci) {
        Hitflick hitflick = Arsenic.getArsenic().getModuleManager().getModuleByClass(Hitflick.class);
        if (!hitflick.isEnabled()) return;
        Minecraft mc = (Minecraft) (Object) this;
        if (mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY) return;
        Entity target = mc.objectMouseOver.entityHit;
        if (hitflick.shouldFlick()) {
            mc.objectMouseOver.typeOfHit = MovingObjectPosition.MovingObjectType.MISS;
            hitflick.armFlick(target); // pass it through
        }
    }

}
