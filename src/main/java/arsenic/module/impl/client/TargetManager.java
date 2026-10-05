package arsenic.module.impl.client;

import arsenic.config.FriendManager;
import arsenic.event.impl.EventMouse;
import net.minecraft.world.effect.MobEffectInstance;
import arsenic.event.impl.EventPacket;
import net.minecraft.world.phys.HitResult;
import arsenic.main.Arsenic;
import arsenic.module.impl.blatant.KillAura;
import arsenic.module.impl.player.AutoHunt;
import arsenic.utils.lag.LagManager;
import arsenic.utils.rotations.RotationUtils;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.world.effect.MobEffects;

import net.minecraft.client.multiplayer.ClientLevel;

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
    private static Map<Player, Float> lastSortValues = new HashMap<>();

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
        ClientLevel world = mc.level;
        if (world == null)
            return;

        Optional.ofNullable(e.getPacket())
                .filter(ServerboundAttackPacket.class::isInstance)
                .map(ServerboundAttackPacket.class::cast)
                .map(attack -> world.getEntity(attack.entityId()))
                .filter(Player.class::isInstance)
                .map(Player.class::cast)
                .filter(player -> getServerHurtTimeOnPacketArrival(player) <= 0)
                .ifPresent(player -> attackSentTime.put(player.getId(), world.getGameTime()));
    };

    @EventLink
    public Listener<EventMouse.Down> middleClickFriendListener = e -> {
        if (e.button != 2 || mc.gui.screen() != null || mc.hitResult == null
                || mc.hitResult.getType() != HitResult.Type.ENTITY
                || !(((net.minecraft.world.phys.EntityHitResult) mc.hitResult).getEntity() instanceof Player))
            return;

        String name = ((net.minecraft.world.phys.EntityHitResult) mc.hitResult).getEntity().getName().getString();
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

    public static float getTimeSinceLastClientSidedHit(Player player) {
        ClientLevel world = mc.level;
        if (world == null || player == null)
            return Float.MAX_VALUE;

        Long sentTick = attackSentTime.get(player.getId());
        if (sentTick == null)
            return Float.MAX_VALUE;

        return world.getGameTime() - sentTick;
    }

    public static float getServerHurtTimeOnPacketArrival(Player player) {
        ClientLevel world = mc.level;
        if (world == null || player == null)
            return Float.MAX_VALUE;

        int entityId = player.getId();
        Long sentTime = attackSentTime.get(entityId);
        long now = world.getGameTime();
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

    public static Player getTarget() {
        List<Player> en = getTargets();
        return en.isEmpty() ? null : en.get(0);
    }

    public static List<Player> getTargets() {
        List<Player> en = PlayerUtils.getPlayersWithin(distance.getValue().getInput() + 1);
        en.removeIf(player -> !isValidTarget(player));
        en.removeIf(player -> !(RotationUtils.getDistanceToEntityBox(player) < distance.getValue().getInput()));
        Map<Player, Float> values = new HashMap<>();
        for (Player player : en)
            values.put(player, sortMode.getValue().sv.value(player));
        en.sort(Comparator.comparingDouble(values::get));
        lastSortValues = values;
        return en;
    }

    public static float sortValue(Player player) {
        Float v = lastSortValues.get(player);
        return v != null ? v : sortMode.getValue().sv.value(player);
    }

    public static boolean isValidTarget(Player ep) {
        return (ep != mc.player)
                && !Arsenic.getArsenic().getFriendManager().isFriend(ep)
                && AutoHunt.allowsTarget(ep)
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

    private static float getEffectiveHealth(Player player) {
        float armourReduction = Math.min(0.8f, player.getArmorValue() * 0.04f);

        float resistanceReduction = 0f;
        MobEffectInstance resistance = player.getEffect(MobEffects.RESISTANCE);
        if (resistance != null)
            resistanceReduction = Math.min(0.8f, (resistance.getAmplifier() + 1) * 0.2f);

        float damageMultiplier = (1f - armourReduction) * (1f - resistanceReduction);
        return player.getHealth() / damageMultiplier;
    }

    @FunctionalInterface
    private interface SortValue {
        Float value(Player player);
    }
}