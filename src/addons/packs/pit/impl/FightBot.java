
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
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S07PacketRespawn;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;

@ModuleInfo(name = "FightBot", category = ModuleCategory.PLAYER, tier = arsenic.module.ModuleTier.BLATANT)
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
        // Killed, or teleported/respawned away from the fight: stop rather than walk back or fight on.
        if (stopRequested || mc.thePlayer.isDead || mc.thePlayer.getHealth() <= 0
                || mc.currentScreen instanceof GuiGameOver) {
            setEnabled(false);
            return;
        }
        mc.gameSettings.pauseOnLostFocus = false;
        if (mc.currentScreen instanceof GuiIngameMenu) mc.displayGuiScreen(null);

        setKillAura(true);
        KillAura aura = Arsenic.getArsenic().getModuleManager().getModuleByClass(KillAura.class);
        EntityPlayer target = aura != null && aura.target != null ? aura.target : nearestTarget();

        if (target != null)
            chaser.chase(target, CHASE_STOP_DISTANCE);
        else
            chaser.goTo(homeX, homeY, homeZ, HOME_STOP_DISTANCE);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventPacket.Incoming.Pre> onPacket = event -> {
        if (event.getPacket() instanceof S07PacketRespawn) {
            // a respawn (after dying, or a world change) puts us somewhere else entirely
            stopRequested = true;
        } else if (event.getPacket() instanceof S08PacketPlayerPosLook) {
            S08PacketPlayerPosLook tp = (S08PacketPlayerPosLook) event.getPacket();
            // coordinates flagged relative are offsets from where we are now
            double x = tp.getX(), y = tp.getY(), z = tp.getZ();
            if (tp.func_179834_f().contains(S08PacketPlayerPosLook.EnumFlags.X)) x += mc.thePlayer.posX;
            if (tp.func_179834_f().contains(S08PacketPlayerPosLook.EnumFlags.Y)) y += mc.thePlayer.posY;
            if (tp.func_179834_f().contains(S08PacketPlayerPosLook.EnumFlags.Z)) z += mc.thePlayer.posZ;
            double dx = x - mc.thePlayer.posX, dy = y - mc.thePlayer.posY, dz = z - mc.thePlayer.posZ;
            if (dx * dx + dy * dy + dz * dz > MAX_TELEPORT * MAX_TELEPORT)
                stopRequested = true;
        }
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
