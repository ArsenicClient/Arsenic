package arsenic.module.impl.player;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.blatant.KillAura;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;

/**
 * Walks to the middle of the map (0, 0) and turns KillAura on once the player is below the
 * configured height. Keeps running with the window unfocused: the vanilla "pause on lost focus"
 * (which opens the pause menu) is switched off while enabled.
 */
@ModuleInfo(name = "AutoGrinder", category = ModuleCategory.PLAYER)
public class AutoGrinder extends Module {

    /** Below this Y the player counts as being in the fight area and KillAura is turned on. */
    public final DoubleProperty fightHeight = new DoubleProperty("Fight Height", new DoubleValue(0, 256, 80, 1));
    /** Horizontal distance from (0, 0) at which the player stops walking. */
    public final DoubleProperty radius = new DoubleProperty("Radius", new DoubleValue(0, 20, 3, 0.5));

    /** Close enough to the target that KillAura can hit; walking further in only gets in the way. */
    private static final double CHASE_STOP_DISTANCE = 2.5;

    private boolean savedPause;
    private boolean pauseSaved;
    private boolean enabledKillAura;

    @Override
    protected void onEnable() {
        if (mc.gameSettings != null) {
            savedPause = mc.gameSettings.pauseOnLostFocus;
            pauseSaved = true;
            mc.gameSettings.pauseOnLostFocus = false;
        }
    }

    @Override
    protected void onDisable() {
        if (mc.gameSettings != null) {
            if (pauseSaved) mc.gameSettings.pauseOnLostFocus = savedPause;
            pauseSaved = false;
            releaseKeys();
        }
        setKillAura(false);
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        // Something else (or a focus loss before this module was enabled) may have flipped it back.
        mc.gameSettings.pauseOnLostFocus = false;
        if (mc.currentScreen instanceof GuiIngameMenu) mc.displayGuiScreen(null);

        boolean fighting = mc.thePlayer.posY < fightHeight.getValue().getInput();
        setKillAura(fighting);

        // Chase the aura's target when it has one, otherwise head for the middle.
        KillAura aura = Arsenic.getArsenic().getModuleManager().getModuleByClass(KillAura.class);
        EntityPlayer target = fighting && aura != null ? aura.target : null;

        double dx, dz, stopAt;
        if (target != null) {
            dx = target.posX - mc.thePlayer.posX;
            dz = target.posZ - mc.thePlayer.posZ;
            stopAt = CHASE_STOP_DISTANCE;
        } else {
            dx = -mc.thePlayer.posX;
            dz = -mc.thePlayer.posZ;
            stopAt = radius.getValue().getInput();
        }

        if (Math.sqrt(dx * dx + dz * dz) <= stopAt) {
            releaseKeys();
            return;
        }

        mc.thePlayer.rotationYaw = (float) (MathHelper.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        mc.thePlayer.rotationPitch = 0;
        setKey(mc.gameSettings.keyBindForward, true);
        setKey(mc.gameSettings.keyBindSprint, true);
        setKey(mc.gameSettings.keyBindJump, mc.thePlayer.isCollidedHorizontally);
    };

    private void setKillAura(boolean on) {
        KillAura aura = Arsenic.getArsenic().getModuleManager().getModuleByClass(KillAura.class);
        if (aura == null) return;
        if (on) {
            if (!aura.isEnabled()) {
                aura.setEnabled(true);
                enabledKillAura = true;
            }
        } else if (enabledKillAura) {
            aura.setEnabled(false);
            enabledKillAura = false;
        }
    }

    private void releaseKeys() {
        setKey(mc.gameSettings.keyBindForward, false);
        setKey(mc.gameSettings.keyBindSprint, false);
        setKey(mc.gameSettings.keyBindJump, false);
    }

    private static void setKey(KeyBinding key, boolean down) {
        KeyBinding.setKeyBindState(key.getKeyCode(), down);
    }
}
