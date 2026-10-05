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
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;

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
        if (mc.options != null) {
            savedPause = mc.options.pauseOnLostFocus;
            pauseSaved = true;
            mc.options.pauseOnLostFocus = false;
        }
    }

    @Override
    protected void onDisable() {
        if (mc.options != null) {
            if (pauseSaved) mc.options.pauseOnLostFocus = savedPause;
            pauseSaved = false;
            releaseKeys();
        }
        setKillAura(false);
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        mc.options.pauseOnLostFocus = false;
        if (mc.gui.screen() instanceof PauseScreen) mc.gui.setScreen(null);

        boolean fighting = mc.player.getY() < fightHeight.getValue().getInput();
        setKillAura(fighting);

        KillAura aura = Arsenic.getArsenic().getModuleManager().getModuleByClass(KillAura.class);
        Player target = fighting && aura != null ? aura.target : null;

        double dx, dz, stopAt;
        if (target != null) {
            dx = target.getX() - mc.player.getX();
            dz = target.getZ() - mc.player.getZ();
            stopAt = CHASE_STOP_DISTANCE;
        } else {
            dx = -mc.player.getX();
            dz = -mc.player.getZ();
            stopAt = radius.getValue().getInput();
        }

        if (MathUtils.horizontalDistance(dx, dz) <= stopAt) {
            releaseKeys();
            return;
        }

        mc.player.setYRot(RotationUtils.yawTo(dx, dz));
        mc.player.setXRot(0);
        setKey(mc.options.keyUp, true);
        setKey(mc.options.keySprint, true);
        setKey(mc.options.keyJump, mc.player.horizontalCollision);
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
        setKey(mc.options.keyUp, false);
        setKey(mc.options.keySprint, false);
        setKey(mc.options.keyJump, false);
    }

    private static void setKey(KeyMapping key, boolean down) {
        key.setDown(down);
    }
}
