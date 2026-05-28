package arsenic.module.impl.client;

import arsenic.event.impl.EventPacket;
import arsenic.main.Arsenic;
import arsenic.module.impl.blatant.KillAura;
import arsenic.module.property.PropertyInfo;
import arsenic.utils.lag.LagManager;
import arsenic.utils.rotations.RotationUtils;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventAttack;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.PlayerUtils;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S1BPacketEntityAttach;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ModuleInfo(name = "Targets", category = ModuleCategory.SETTINGS)
public class TargetManager extends Module {
    public static EnumProperty<SortMode> sortMode = new EnumProperty<>("Sort Mode", SortMode.Distance);
    public static BooleanProperty teams = new BooleanProperty("Target Teammates", true),
            invis = new BooleanProperty("Target Invis", true),
            bots = new BooleanProperty("Target Bots", true),
            unArmoured = new BooleanProperty("Target UnArmoured", true);
    public static DoubleProperty fov = new DoubleProperty("General FOV", new DoubleValue(0, 360, 180, 1)),
            auraFov = new DoubleProperty("Aura FOV", new DoubleValue(0, 360, 360, 1)),
            distance = new DoubleProperty("Distance", new DoubleValue(3, 10, 8, 0.1));
    @PropertyInfo(reliesOn = "Sort Mode", value = "Lock")
    public final DoubleProperty lockDist = new DoubleProperty("Locked Distance", new DoubleValue(3, 10, 5, 0.1));

    private static EntityPlayer lockedTarget;

    // entityId -> estimated server-side hurt time
    private static final Map<Integer, Float> serverHurtTime = new HashMap<>();
    // entityId -> game time when we sent the attack packet
    private static final Map<Integer, Long> attackSentTime = new HashMap<>();

    @Override
    protected void onEnable() {
        this.setEnabled(false);
    }

    private static double getFOV() {
        return Arsenic.getArsenic().getModuleManager().getModuleByClass(KillAura.class).isEnabled()
                ? auraFov.getValue().getInput()
                : fov.getValue().getInput();
    }

    @EventLink
    public Listener<EventAttack> eventAttackListener = e -> {
        lockedTarget = e.getTarget() instanceof EntityPlayer
                && mc.thePlayer.getDistanceToEntity(e.getTarget()) <= lockDist.getValue().getInput()
                ? (EntityPlayer) e.getTarget()
                : lockedTarget;
    };

    @EventLink
    public Listener<EventPacket.OutGoing> eventPacketListener = e -> {
        Packet<?> packet = e.getPacket();

        if (packet instanceof C02PacketUseEntity) {
            C02PacketUseEntity useEntity = (C02PacketUseEntity) packet;
            if (useEntity.getAction() == C02PacketUseEntity.Action.ATTACK) {
                int entityId = useEntity.getEntityFromWorld(mc.theWorld).getEntityId();
                long sentTime = mc.theWorld.getTotalWorldTime();
                attackSentTime.put(entityId, sentTime);
            }
        }

        LagManager.getPingAsTicks();
    };


    public static float getServerHurtTime(EntityPlayer player) {
        int entityId = player.getEntityId();
        Long sentTime = attackSentTime.get(entityId);
        long now = mc.theWorld.getTotalWorldTime();
        long pingTicks = LagManager.getPingAsTicks();

        // If the client hurt time just became non-zero (player was just hit),
        // recalibrate our server estimate from the ground truth
        Float previousHurt = serverHurtTime.get(entityId);
        if (player.hurtTime > 0 && (previousHurt == null || previousHurt == 0f)) {
            // Client just saw the hurt — server hurt time is slightly ahead due to ping/2
            // since the damage confirmation traveled back to us, add ping/2 ticks
            float recalibrated = Math.min(10f, player.hurtTime + (pingTicks / 2f));
            serverHurtTime.put(entityId, recalibrated);
            attackSentTime.remove(entityId); // no longer need this, ground truth takes over
            return recalibrated;
        }

        // If client hurtTime is actively decrementing, sync our estimate to it adjusted for ping
        if (player.hurtTime > 0) {
            float estimated = Math.min(10f, player.hurtTime + (pingTicks / 2f));
            serverHurtTime.put(entityId, estimated);
            return estimated;
        }

        // Client shows no hurt but we expected the player to still be hurt —
        // this means our attack was likely cancelled/missed, retarget
        if (sentTime != null) {
            long hitLandedAt = sentTime + (pingTicks / 2);
            long ticksSinceHit = now - hitLandedAt;
            float expectedServerHurt = Math.max(0f, 10f - ticksSinceHit);

            if (expectedServerHurt > 1f && player.hurtTime == 0) {
                // We expected hurt but client shows none for longer than 1 tick — attack missed
                // Return max value so this player is deprioritized and we switch
                attackSentTime.remove(entityId);
                serverHurtTime.remove(entityId);
                return 10f;
            }

            float estimated = Math.max(0f, expectedServerHurt);
            serverHurtTime.put(entityId, estimated);
            return estimated;
        }

        // No attack recorded, fall back to client hurt time
        return player.hurtTime;
    }

    public static EntityPlayer getTarget() {
        List<EntityPlayer> en = PlayerUtils.getPlayersWithin(distance.getValue().getInput());
        en.removeIf(player -> !isValidTarget(player));
        return en.isEmpty() ? null : en.stream()
                .min(Comparator.comparingDouble(target -> sortMode.getValue().sv.value(target)))
                .get();
    }

    private static boolean isValidTarget(EntityPlayer ep) {
        return (
                (ep != mc.thePlayer)
                        && (bots.getValue() || !AntiBot.isBot(ep))
                        && (teams.getValue() || !PlayerUtils.isEntityTeamSameAsPlayer(ep)
                        && (invis.getValue() || !ep.isInvisible())
                        && (unArmoured.getValue() || !PlayerUtils.isPlayerWearingArmour(ep))
                        && PlayerUtils.withinFov(ep, (float) getFOV())
                ));
    }

    public enum SortMode {
        Distance(player -> mc.thePlayer.getDistanceToEntity(player)),
        HurtSwitch(player -> (float) player.hurtTime),
        // Sorts ascending by predicted server hurt time — player closest to 0 (hittable) is preferred
        SmartSwitch(player -> getServerHurtTime(player)),
        Fov(player -> (float) Math.abs(RotationUtils.fovFromEntity(player))),
        Lock(player -> player == lockedTarget ? 0f : 1f),
        Health(EntityLivingBase::getHealth);

        private final SortValue sv;

        SortMode(SortValue sv) {
            this.sv = sv;
        }
    }

    @FunctionalInterface
    private interface SortValue {
        Float value(EntityPlayer player);
    }
}