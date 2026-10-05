package arsenic.module.impl.player;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
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
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(name = "FightBot", category = ModuleCategory.PLAYER)
public class FightBot extends Module {

    public final DoubleProperty chaseRange = new DoubleProperty("Chase Range", new DoubleValue(1, 30, 7, 0.5));

    private static final double CHASE_STOP_DISTANCE = 2.5;
    private static final double HOME_STOP_DISTANCE = 1.0;
    /** A server teleport moving us further than this (blocks) ends the session: we're not where we were fighting. */
    private static final double MAX_TELEPORT = 5.0;

    private final Chaser chaser = new Chaser();
    private double homeX, homeY, homeZ;
    private boolean savedPause;
    private boolean pauseSaved;
    private boolean enabledKillAura;
    /** Set from the network thread when we were teleported away or respawned; acted on next tick. */
    private volatile boolean stopRequested;

    @Override
    protected void onEnable() {
        stopRequested = false;
        if (mc.player != null) {
            homeX = mc.player.getX();
            homeY = mc.player.getY();
            homeZ = mc.player.getZ();
        }
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
        }
        chaser.stop();
        BotDriver.forgetOwnBlocks();
        setKillAura(false);
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        // Killed, or teleported/respawned away from the fight: stop rather than walk back or fight on.
        if (stopRequested || mc.player.isRemoved() || mc.player.getHealth() <= 0
                || mc.gui.screen() instanceof DeathScreen) {
            setEnabled(false);
            return;
        }
        mc.options.pauseOnLostFocus = false;
        if (mc.gui.screen() instanceof PauseScreen) mc.gui.setScreen(null);

        setKillAura(true);
        KillAura aura = Arsenic.getArsenic().getModuleManager().getModuleByClass(KillAura.class);
        Player target = aura != null && aura.target != null ? aura.target : nearestTarget();

        if (target != null)
            chaser.chase(target, CHASE_STOP_DISTANCE);
        else
            chaser.goTo(homeX, homeY, homeZ, HOME_STOP_DISTANCE);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventPacket.Incoming.Pre> onPacket = event -> {
        if (event.getPacket() instanceof ClientboundRespawnPacket) {
            // a respawn (after dying, or a world change) puts us somewhere else entirely
            stopRequested = true;
        } else if (event.getPacket() instanceof ClientboundPlayerPositionPacket tp) {
            // coordinates flagged relative are offsets from where we are now
            Vec3 to = tp.change().position();
            double x = to.x, y = to.y, z = to.z;
            if (tp.relatives().contains(Relative.X)) x += mc.player.getX();
            if (tp.relatives().contains(Relative.Y)) y += mc.player.getY();
            if (tp.relatives().contains(Relative.Z)) z += mc.player.getZ();
            double dx = x - mc.player.getX(), dy = y - mc.player.getY(), dz = z - mc.player.getZ();
            if (dx * dx + dy * dy + dz * dz > MAX_TELEPORT * MAX_TELEPORT)
                stopRequested = true;
        }
    };

    private Player nearestTarget() {
        Player best = null;
        double bestDist = chaseRange.getValue().getInput();
        for (Player player : mc.level.players()) {
            double dist = mc.player.distanceTo(player);
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
