package arsenic.module.impl.player;

import arsenic.utils.java.MathUtils;
import arsenic.utils.rotations.RotationUtils;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.impl.blatant.KillAura;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;

@ModuleInfo(name = "AutoGrinder", category = ModuleCategory.PLAYER, tier = ModuleTier.DEV)
public class AutoGrinder extends Module {

    public final DoubleProperty fightHeight = new DoubleProperty("Fight Height", new DoubleValue(0, 256, 80, 1));
    public final DoubleProperty radius = new DoubleProperty("Radius", new DoubleValue(0, 20, 3, 0.5));

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
        mc.gameSettings.pauseOnLostFocus = false;
        if (mc.currentScreen instanceof GuiIngameMenu) mc.displayGuiScreen(null);

        boolean fighting = mc.thePlayer.posY < fightHeight.getValue().getInput();
        setKillAura(fighting);

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

        if (MathUtils.horizontalDistance(dx, dz) <= stopAt) {
            releaseKeys();
            return;
        }

        mc.thePlayer.rotationYaw = RotationUtils.yawTo(dx, dz);
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
