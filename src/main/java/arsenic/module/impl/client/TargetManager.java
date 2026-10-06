package arsenic.module.impl.client;

import arsenic.config.FriendManager;
import arsenic.event.impl.EventMouse;
import arsenic.event.impl.EventPacket;
import net.minecraft.util.MovingObjectPosition;
import arsenic.main.Arsenic;
import arsenic.module.impl.blatant.KillAura;
import arsenic.utils.lag.LagManager;
import arsenic.utils.rotations.RotationUtils;
import net.minecraft.entity.player.EntityPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.PlayerUtils;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

import net.minecraft.client.multiplayer.WorldClient;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ModuleInfo(name = "Targets", category = ModuleCategory.CLIENT, hidden = true, enabled = true)
public class TargetManager extends Module {
    public static EnumProperty<SortMode> sortMode = new EnumProperty<>("Sort Mode", SortMode.SmartSwitch);
    public static BooleanProperty teams = new BooleanProperty("Target Teammates", true),
            invis = new BooleanProperty("Target Invis", true),
            bots = new BooleanProperty("Target Bots", true),
            unArmoured = new BooleanProperty("Target UnArmoured", true);
    public static DoubleProperty fov = new DoubleProperty("General FOV", new DoubleValue(0, 360, 180, 1)),
            auraFov = new DoubleProperty("Aura FOV", new DoubleValue(0, 360, 360, 1)),
            distance = new DoubleProperty("Distance", new DoubleValue(3, 10, 8, 0.1));

    private static final Map<Integer, Float> serverHurtTime = new HashMap<>();
    private static final Map<Integer, Long> attackSentTime = new HashMap<>();
    private static Map<EntityPlayer, Float> lastSortValues = new HashMap<>();

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
    public Listener<EventPacket.OutGoing> eventPacketListener = e -> {
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

    @EventLink
    public Listener<EventMouse.Down> middleClickFriendListener = e -> {
        if (e.button != 2 || mc.currentScreen != null || mc.objectMouseOver == null
                || mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY
                || !(mc.objectMouseOver.entityHit instanceof EntityPlayer))
            return;

        String name = mc.objectMouseOver.entityHit.getName();
        FriendManager friends = Arsenic.getArsenic().getFriendManager();
        if (friends.isFriend(name)) {
            friends.remove(name);
            PlayerUtils.addWaterMarkedMessageToChat("§c" + name + "§r is no longer a friend");
        } else {
            friends.add(name);
            PlayerUtils.addWaterMarkedMessageToChat("§a" + name + "§r is now a friend");
        }
        Arsenic.getArsenic().getConfigManager().saveClientConfig();
    };

    public static float getTimeSinceLastClientSidedHit(EntityPlayer player) {
        WorldClient world = mc.theWorld;
        if (world == null || player == null)
            return Float.MAX_VALUE;

        Long sentTick = attackSentTime.get(player.getEntityId());
        if (sentTick == null)
            return Float.MAX_VALUE;

        return world.getTotalWorldTime() - sentTick;
    }

    public static float getServerHurtTimeOnPacketArrival(EntityPlayer player) {
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
        List<EntityPlayer> en = getTargets();
        return en.isEmpty() ? null : en.get(0);
    }

    public static List<EntityPlayer> getTargets() {
        List<EntityPlayer> en = PlayerUtils.getPlayersWithin(distance.getValue().getInput() + 1);
        en.removeIf(player -> !isValidTarget(player));
        en.removeIf(player -> !(RotationUtils.getDistanceToEntityBox(player) < distance.getValue().getInput()));
        Map<EntityPlayer, Float> values = new HashMap<>();
        for (EntityPlayer player : en)
            values.put(player, sortMode.getValue().sv.value(player));
        en.sort(Comparator.comparingDouble(values::get));
        lastSortValues = values;
        return en;
    }

    public static float sortValue(EntityPlayer player) {
        Float v = lastSortValues.get(player);
        return v != null ? v : sortMode.getValue().sv.value(player);
    }

    /** Modules (e.g. addons) may restrict who can be targeted while they are enabled. */
    private static boolean allowedByModules(EntityPlayer ep) {
        for (Module m : Arsenic.getArsenic().getModuleManager().getModules())
            if (m.isEnabled() && !m.allowsTarget(ep))
                return false;
        return true;
    }

    public static boolean isValidTarget(EntityPlayer ep) {
        return (ep != mc.thePlayer)
                && !Arsenic.getArsenic().getFriendManager().isFriend(ep)
                && allowedByModules(ep)
                && (bots.getValue()      || !AntiBot.isBot(ep))
                && (teams.getValue()      || !PlayerUtils.isEntityTeamSameAsPlayer(ep))
                && (invis.getValue()      || !ep.isInvisible())
                && (unArmoured.getValue() || !PlayerUtils.isPlayerWearingArmour(ep))
                && PlayerUtils.withinFov(ep, (float) getFOV());
    }

    public enum SortMode {
        SmartSwitch(TargetManager::getServerHurtTimeOnPacketArrival),
        Fov(player -> (float) Math.abs(RotationUtils.fovFromEntity(player))),
        Health(TargetManager::getEffectiveHealth);

        private final SortValue sv;

        SortMode(SortValue sv) {
            this.sv = sv;
        }
    }

    private static float getEffectiveHealth(EntityPlayer player) {
        float armourReduction = Math.min(0.8f, player.getTotalArmorValue() * 0.04f);

        float resistanceReduction = 0f;
        PotionEffect resistance = player.getActivePotionEffect(Potion.resistance);
        if (resistance != null)
            resistanceReduction = Math.min(0.8f, (resistance.getAmplifier() + 1) * 0.2f);

        float damageMultiplier = (1f - armourReduction) * (1f - resistanceReduction);
        return player.getHealth() / damageMultiplier;
    }

    @FunctionalInterface
    private interface SortValue {
        Float value(EntityPlayer player);
    }
}