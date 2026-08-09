package arsenic.module.impl.client;

import arsenic.event.impl.EventPacket;
import arsenic.main.Arsenic;
import arsenic.module.impl.blatant.KillAura;
import arsenic.module.property.PropertyInfo;
import arsenic.utils.lag.LagManager;
import arsenic.utils.rotations.RotationUtils;
import net.minecraft.entity.Entity;
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

import net.minecraft.client.multiplayer.WorldClient;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ModuleInfo(name = "Targets", category = ModuleCategory.SETTINGS, hidden = true, enabled = true)
public class TargetManager extends Module {
    public static EnumProperty<SortMode> sortMode = new EnumProperty<>("Sort Mode", SortMode.SmartSwitch);
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
    private static final Map<Integer, Float> serverHurtTime = new HashMap<>();
    private static final Map<Integer, Long> attackSentTime = new HashMap<>();

    // Intentionally always-on: this hidden SETTINGS module backs the aura/target logic,
    // so the incoming 'enabled' flag is deliberately ignored.
    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(true);
    }

    @Override
    public void setEnabledSilently(boolean enabled) {
        super.setEnabledSilently(true);
    }

    private static double getFOV() {
        return Arsenic.getArsenic().getModuleManager().getModuleByClass(KillAura.class).isEnabled()
                ? auraFov.getValue().getInput()
                : fov.getValue().getInput();
    }

    @EventLink
    public Listener<EventAttack> eventAttackListener = e -> {
        lockedTarget = e.getTarget() instanceof EntityPlayer
                && RotationUtils.getDistanceToEntityBox(e.getTarget()) <= lockDist.getValue().getInput()
                ? (EntityPlayer) e.getTarget()
                : lockedTarget;
    };

    @EventLink
    public Listener<EventPacket.OutGoing> eventPacketListener = e -> {
        // Outgoing packets fire on the netty thread and can arrive before, during and after a world
        // swap, so the world is read once into a local rather than re-checked between uses - it can
        // become null between the guard and the last line. Every other hop here is nullable too:
        // the packet itself, the action enum on a server-constructed packet, and the entity lookup,
        // which returns null whenever the target has already been despawned client side.
        WorldClient world = mc.theWorld;
        if (world == null)
            return;

        Optional.ofNullable(e.getPacket())
                .filter(C02PacketUseEntity.class::isInstance)
                .map(C02PacketUseEntity.class::cast)
                .filter(use -> use.getAction() == C02PacketUseEntity.Action.ATTACK)
                .map(use -> use.getEntityFromWorld(world))
                .filter(EntityPlayer.class::isInstance)
                .map(EntityPlayer.class::cast)
                .filter(player -> getServerHurtTimeOnPacketArrival(player) <= 0)
                .ifPresent(player -> attackSentTime.put(player.getEntityId(), world.getTotalWorldTime()));
    };

    public static float getTimeSinceLastClientSidedHit(EntityPlayer player) {
        // attackSentTime stores world-tick timestamps, so this must be measured in world ticks too
        // (the old version subtracted world ticks from System.currentTimeMillis(), which was garbage).
        WorldClient world = mc.theWorld;
        if (world == null || player == null)
            return Float.MAX_VALUE;

        Long sentTick = attackSentTime.get(player.getEntityId());
        if (sentTick == null)
            return Float.MAX_VALUE;

        return world.getTotalWorldTime() - sentTick;
    }

    public static float getServerHurtTimeOnPacketArrival(EntityPlayer player) {
        // read once: this runs off the netty thread via the outgoing packet listener, so the world
        // can go null between the guard below and any later use of it
        WorldClient world = mc.theWorld;
        if (world == null || player == null)
            return Float.MAX_VALUE;

        int entityId = player.getEntityId();
        Long sentTime = attackSentTime.get(entityId);
        long now = world.getTotalWorldTime();
        long pingTicks = LagManager.getPingAsTicks();

        Float previousHurt = serverHurtTime.get(entityId);
        if (player.hurtTime > 0 && (previousHurt == null || previousHurt == 0f)) {
            float recalibrated = Math.min(10f, player.hurtTime + (pingTicks / 2f));
            serverHurtTime.put(entityId, recalibrated);
            attackSentTime.remove(entityId);
            return Math.max(0f, recalibrated - pingTicks);
        }

        if (player.hurtTime > 0) {
            float estimated = Math.min(10f, player.hurtTime + (pingTicks / 2f));
            serverHurtTime.put(entityId, estimated);
            return Math.max(0f, estimated - pingTicks);
        }

        if (sentTime != null) {
            long hitLandedAt = sentTime + (pingTicks / 2);
            long ticksSinceHit = now - hitLandedAt;
            float expectedServerHurt = Math.max(0f, 10f - ticksSinceHit);

            if (expectedServerHurt > 1f && player.hurtTime == 0) {
                attackSentTime.remove(entityId);
                serverHurtTime.remove(entityId);
                return 10f;
            }

            float estimated = Math.max(0f, expectedServerHurt);
            serverHurtTime.put(entityId, estimated);
            return Math.max(0f, estimated - pingTicks);
        }

        return Math.max(0f, player.hurtTime - pingTicks);
    }

    public static EntityPlayer getTarget() {
        List<EntityPlayer> en = PlayerUtils.getPlayersWithin(distance.getValue().getInput() + 1);
        en.removeIf(player -> !isValidTarget(player));
        en.removeIf(player -> !(RotationUtils.getDistanceToEntityBox(player) < distance.getValue().getInput()));
        return en.isEmpty() ? null : en.stream()
                .min(Comparator.comparingDouble(target -> sortMode.getValue().sv.value(target)))
                .get();
    }

    private static boolean isValidTarget(EntityPlayer ep) {
        return (ep != mc.thePlayer)
                && (bots.getValue()       || !AntiBot.isBot(ep))
                && (teams.getValue()      || !PlayerUtils.isEntityTeamSameAsPlayer(ep))
                && (invis.getValue()      || !ep.isInvisible())
                && (unArmoured.getValue() || !PlayerUtils.isPlayerWearingArmour(ep))
                && PlayerUtils.withinFov(ep, (float) getFOV());
    }

    public enum SortMode {
        Distance(player -> (float) RotationUtils.getDistanceToEntityBox(player)),
        HurtSwitch(player -> (float) player.hurtTime),
        SmartSwitch(TargetManager::getServerHurtTimeOnPacketArrival),
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