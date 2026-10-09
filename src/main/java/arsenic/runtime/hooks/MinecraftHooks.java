package arsenic.runtime.hooks;

import arsenic.event.impl.EventDisplayGuiScreen;
import arsenic.event.impl.EventGameLoop;
import arsenic.event.impl.EventKey;
import arsenic.event.impl.EventRunTick;
import arsenic.runtime.Access;
import arsenic.main.Arsenic;
import arsenic.main.MinecraftAPI;
import arsenic.module.impl.ghost.Clicker;
import arsenic.module.impl.ghost.Hitflick;
import arsenic.module.impl.ghost.NoHitDelay;
import arsenic.module.impl.player.FastPlace;
import arsenic.module.impl.visual.custommainmenu.CustomMenu;
import arsenic.module.impl.visual.custommainmenu.ScreenTransition;
import arsenic.module.impl.world.BridgeAssist;
import arsenic.utils.render.capture.RenderTargets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.input.Keyboard;

/** {@link Minecraft} hooks, called from the injected bytecode. */
public final class MinecraftHooks {

    private static final Access.FieldRef RIGHT_CLICK_DELAY = Access.field(Minecraft.class, "rightClickDelayTimer");
    private static final Access.FieldRef LEFT_CLICK_COUNTER = Access.field(Minecraft.class, "leftClickCounter");

    private MinecraftHooks() {}

    /** getFramebuffer HEAD: lets RenderTargets point mc.getFramebuffer() at a redirect target. Null keeps vanilla. */
    public static Framebuffer getFramebuffer(Minecraft self) {
        return RenderTargets.getRedirect();
    }

    /** runTick: every KeyBinding.setKeyBindState call. */
    public static void setKeyBindState(int keyCode, boolean pressed) {
        MinecraftAPI.KEY_CODE = keyCode;
        KeyBinding.setKeyBindState(keyCode, pressed);
    }

    /** runTick HEAD. */
    public static void runTickHead(Minecraft self) {
        Arsenic.getInstance().getEventManager().post(new EventGameLoop());
        MinecraftAPI.KEY_CODE = null;
        Arsenic.getInstance().getEventManager().post(new EventRunTick());
    }

    /** runTick: the third Keyboard.getEventKeyState call (the in-game key press branch). */
    public static boolean getEventKeyState() {
        boolean state = Keyboard.getEventKeyState();
        if (state && MinecraftAPI.KEY_CODE != null && Minecraft.getMinecraft().currentScreen == null) {
            EventKey event = new EventKey(MinecraftAPI.KEY_CODE);
            Arsenic.getInstance().getEventManager().post(event);
            MinecraftAPI.KEY_CODE = null;
        }
        return state;
    }

    /** runTick: every KeyBinding.isPressed call. */
    public static boolean isPressed(KeyBinding keyBinding) {
        if (!shouldBlockInput(keyBinding))
            return keyBinding.isPressed();
        while (keyBinding.isPressed()) { }
        return false;
    }

    /** runTick: every KeyBinding.isKeyDown call. */
    public static boolean isKeyDown(KeyBinding keyBinding) {
        return keyBinding.isKeyDown() && !shouldBlockInput(keyBinding);
    }

    private static boolean shouldBlockInput(KeyBinding keyBinding) {
        GameSettings gameSettings = Minecraft.getMinecraft().gameSettings;
        if (keyBinding != gameSettings.keyBindAttack && keyBinding != gameSettings.keyBindUseItem)
            return false;
        return Arsenic.getArsenic().getSilentRotationManager().isBlockingUserInput();
    }

    /** displayGuiScreen HEAD. */
    public static void displayGuiScreenHead(Minecraft self, GuiScreen guiScreenIn) {
        if (guiScreenIn != self.currentScreen && arsenic.gui.click.GuiStyle.customMenus())
            ScreenTransition.capture(self);
    }

    /** displayGuiScreen RETURN. */
    public static void displayGuiScreenReturn(Minecraft self, GuiScreen guiScreenIn) {
        if (guiScreenIn instanceof GuiMainMenu && !CustomMenu.consumeVanillaRequest()
                && arsenic.gui.click.GuiStyle.customMenus()) {
            CustomMenu.display();
        }
        EventDisplayGuiScreen event = new EventDisplayGuiScreen(guiScreenIn);
        Arsenic.getArsenic().getEventManager().post(event);
    }

    /** rightClickMouse RETURN. */
    public static void rightClickMouseReturn(Minecraft self) {
        BridgeAssist bridgeAssist = Arsenic.getArsenic().getModuleManager().getModuleByClass(BridgeAssist.class);
        if (bridgeAssist != null && bridgeAssist.isEnabled()) bridgeAssist.onPlace();
        if (bridgeAssist != null && bridgeAssist.isEnabled() && bridgeAssist.isBridging()) {
            RIGHT_CLICK_DELAY.setInt(self, bridgeAssist.getPlaceDelay());
            return;
        }
        FastPlace fastPlace = Arsenic.getArsenic().getModuleManager().getModuleByClass(FastPlace.class);
        if (!fastPlace.isEnabled())
            return;
        RIGHT_CLICK_DELAY.setInt(self, fastPlace.getTickDelay());
    }

    /** clickMouse HEAD. */
    public static void clickMouseHead(Minecraft self) {
        if (Arsenic.getArsenic().getModuleManager().getModuleByClass(NoHitDelay.class).isEnabled() || Arsenic.getArsenic().getModuleManager().getModuleByClass(Clicker.class).isEnabled())
            LEFT_CLICK_COUNTER.setInt(self, 0);
    }

    /** clickMouse, before EntityPlayerSP.swingItem. */
    public static void clickMouseBeforeSwing(Minecraft self) {
        Hitflick hitflick = Arsenic.getArsenic().getModuleManager().getModuleByClass(Hitflick.class);
        if (!hitflick.isEnabled()) return;
        if (self.objectMouseOver == null || self.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY) return;
        Entity target = self.objectMouseOver.entityHit;
        if (hitflick.shouldFlick() && hitflick.armFlick(target)) {
            self.objectMouseOver.typeOfHit = MovingObjectPosition.MovingObjectType.MISS;
        }
    }
}
