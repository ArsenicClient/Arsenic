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
import arsenic.module.impl.client.TargetManager;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.bot.BotDriver;
import arsenic.utils.bot.Chaser;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Remembers where it was enabled, turns KillAura on, chases the nearest target (within Chase
 * Range), and walks back to the starting spot when there is none. Getting there goes through the
 * pathfinder, so walls, drops and gaps no longer stop it (see {@link Chaser}). Keeps running with
 * the window unfocused: the vanilla "pause on lost focus" (which opens the pause menu) is
 * switched off while enabled.
 */
@ModuleInfo(name = "FightBot", category = ModuleCategory.PLAYER)
public class FightBot extends Module {

    /** Targets further than this are ignored and the bot heads home instead. */
    public final DoubleProperty chaseRange = new DoubleProperty("Chase Range", new DoubleValue(1, 30, 7, 0.5));

    /** Close enough to the target that KillAura can hit; walking further in only gets in the way. */
    private static final double CHASE_STOP_DISTANCE = 2.5;
    /** Close enough to the start spot to stop walking. */
    private static final double HOME_STOP_DISTANCE = 1.0;

    private final Chaser chaser = new Chaser();
    private double homeX, homeY, homeZ;
    private boolean savedPause;
    private boolean pauseSaved;
    private boolean enabledKillAura;

    @Override
    protected void onEnable() {
        if (mc.thePlayer != null) {
            homeX = mc.thePlayer.posX;
            homeY = mc.thePlayer.posY;
            homeZ = mc.thePlayer.posZ;
        }
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
        }
        chaser.stop();
        BotDriver.forgetOwnBlocks();
        setKillAura(false);
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        mc.gameSettings.pauseOnLostFocus = false;
        if (mc.currentScreen instanceof GuiIngameMenu) mc.displayGuiScreen(null);

        setKillAura(true);
        // KillAura only picks targets inside its own short aim range, so look for one out to
        // Chase Range here; once we're close, KillAura's pick is the one being hit.
        KillAura aura = Arsenic.getArsenic().getModuleManager().getModuleByClass(KillAura.class);
        EntityPlayer target = aura != null && aura.target != null ? aura.target : nearestTarget();

        if (target != null)
            chaser.chase(target, CHASE_STOP_DISTANCE);
        else
            chaser.goTo(homeX, homeY, homeZ, HOME_STOP_DISTANCE);
    };

    private EntityPlayer nearestTarget() {
        EntityPlayer best = null;
        double bestDist = chaseRange.getValue().getInput();
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            double dist = mc.thePlayer.getDistanceToEntity(player);
            if (dist <= bestDist && TargetManager.isValidTarget(player)) {
                bestDist = dist;
                best = player;
            }
        }
        return best;
    }

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

}
