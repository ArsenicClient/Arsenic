package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.impl.client.TargetManager;
import arsenic.module.property.ShowWhen;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.io.Keys;
import arsenic.utils.minecraft.ItemUtils;
import arsenic.utils.minecraft.PlayerUtils;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

/** Attacks the moment your crosshair is on a target, once the weapon has recharged. */
@ModuleInfo(name = "TriggerBot", description = "Attacks when your crosshair is on a player", category = ModuleCategory.COMBAT, tier = ModuleTier.LEGIT)
public class TriggerBot extends Module {

    public enum Targets { Players, Crystals, Any }

    public final BooleanProperty cooldown = new BooleanProperty("Attack Cooldown", true);
    public final BooleanProperty weaponOnly = new BooleanProperty("Weapon Only", true);
    public final EnumProperty<Targets> targets = new EnumProperty<>("Targets", Targets.Players);
    @ShowWhen(property = "Attack Cooldown", is = "true")
    public final RangeProperty cooldownWindow = new RangeProperty("Cooldown", new RangeValue(0, 100, 95, 95, 1));
    @ShowWhen(property = "Attack Cooldown", is = "true")
    public final BooleanProperty tpsSync = new BooleanProperty("TPS Sync", true);
    @ShowWhen(property = "Attack Cooldown", is = "false")
    public final RangeProperty customCooldown = new RangeProperty("Custom Cooldown", new RangeValue(0, 1000, 0, 0, 10));
    public final RangeProperty delay = new RangeProperty("Delay", new RangeValue(0, 500, 0, 0, 5));
    public final DoubleProperty missChance = new DoubleProperty("Miss Chance", new DoubleValue(0, 25, 0, 1));
    public final BooleanProperty critTiming = new BooleanProperty("Criticals Timing", false);
    public final BooleanProperty critOnly = new BooleanProperty("Crit Only", false);
    public final BooleanProperty skipShielding = new BooleanProperty("Skip Shielding", true);
    public final BooleanProperty sticky = new BooleanProperty("Sticky Target", false);
    @ShowWhen(property = "Sticky Target", is = "true")
    public final DoubleProperty stickyRange = new DoubleProperty("Sticky Range", new DoubleValue(3, 8, 5, 0.1));
    @ShowWhen(property = "Targets", not = "Players")
    public final BooleanProperty crystalOnly = new BooleanProperty("Crystal Only", true);
    @ShowWhen(property = "Targets", not = "Players")
    public final BooleanProperty crystalOnGround = new BooleanProperty("On Ground Only", true);
    @ShowWhen(property = "Targets", not = "Players")
    public final BooleanProperty pauseOnKill = new BooleanProperty("Pause On Kill", true);
    public final BooleanProperty requireClick = new BooleanProperty("Require Left Click", false);
    public final BooleanProperty pauseInScreen = new BooleanProperty("Pause In Screen", true);

    /** Locked sticky target id, -1 while none. */
    private int stickyId = -1;
    /** The entity the crosshair is on and whether its acquisition was rolled as a miss. */
    private int seenId = -1;
    private boolean missed;
    private long seenSince;
    private double delayMs = -1;
    private long lastAttack;
    private double customMs = -1;

    private long lastTimePacket;
    private volatile double serverTps = 20.0;
    private volatile boolean reset;

    @Override
    protected void onDisable() {
        stickyId = -1;
        clearSeen();
    }

    @EventLink
    public final Listener<EventPacket.Incoming.Pre> onIncoming = event -> {
        if (event.getPacket() instanceof ClientboundRespawnPacket) {
            reset = true;
            lastTimePacket = 0;
        } else if (event.getPacket() instanceof ClientboundSetTimePacket) {
            // the server sends the time once a second (20 ticks), so the gap between two is a TPS estimate
            long now = System.nanoTime();
            if (lastTimePacket != 0) {
                double secs = (now - lastTimePacket) / 1.0E9;
                if (secs > 0)
                    serverTps = Math.min(20.0, 1.0 / secs);
            }
            lastTimePacket = now;
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (reset) {
            reset = false;
            stickyId = -1;
            clearSeen();
        }
        if (stickyId != -1) {
            Entity locked = mc.level.getEntity(stickyId);
            if (locked == null || !locked.isAlive() || mc.player.distanceTo(locked) > stickyRange.getValue().getInput())
                stickyId = -1;
        }

        if ((pauseInScreen.getValue() && mc.gui.screen() != null) || !mc.isWindowActive() || mc.player.isUsingItem())
            return;
        if (requireClick.getValue() && !Keys.isMouseDown(0)) {
            clearSeen();
            return;
        }
        if (!(mc.hitResult instanceof EntityHitResult hit)) {
            clearSeen();
            return;
        }

        Entity target = hit.getEntity();
        if (target.getId() != seenId) {
            seenId = target.getId();
            missed = false;
            seenSince = System.currentTimeMillis();
            delayMs = delay.getValue().getRandomInRange();
        }
        if (missed || !isValid(target))
            return;
        if (System.currentTimeMillis() - seenSince < delayMs)
            return;

        boolean crystal = target instanceof EndCrystal;
        ItemStack held = mc.player.getMainHandItem();
        boolean weapon = ItemUtils.isWeapon(held);

        if (crystal) {
            if (crystalOnly.getValue() && !held.is(Items.END_CRYSTAL))
                return;
            if (crystalOnGround.getValue() && !mc.player.onGround())
                return;
            if (pauseOnKill.getValue() && lootNearby(target))
                return;
        } else {
            if (weaponOnly.getValue() && !weapon)
                return;
            if (cooldown.getValue()) {
                float threshold = (float) (cooldownWindow.getValue().getRandomInRange() / 100.0);
                if (tpsSync.getValue())
                    threshold = tpsAdjusted(threshold);
                if (mc.player.getAttackStrengthScale(0.5f) < threshold)
                    return;
            } else {
                if (customMs < 0)
                    customMs = customCooldown.getValue().getRandomInRange();
                if (System.currentTimeMillis() - lastAttack < customMs)
                    return;
            }
        }

        if (target instanceof Player player) {
            if (skipShielding.getValue() && isShielding(player) && !ItemUtils.isAxe(held))
                return;
            if (weapon && !mc.player.onGround()) {
                boolean falling = mc.player.fallDistance > 0 && mc.player.getDeltaMovement().y < 0;
                // wait out the rising half of a jump, and with Crit Only never swing outside of a fall
                if ((critTiming.getValue() || critOnly.getValue()) && !falling)
                    return;
            } else if (weapon && critOnly.getValue()) {
                return;
            }

            if (sticky.getValue()) {
                if (stickyId == -1)
                    stickyId = player.getId();
                else if (stickyId != player.getId())
                    return;
            }

            double chance = missChance.getValue().getInput();
            if (chance > 0 && ThreadLocalRandom.current().nextDouble(100.0) < chance) {
                missed = true;
                return;
            }
        }

        mc.gameMode.attack(mc.player, target);
        PlayerUtils.swingItem();
        lastAttack = System.currentTimeMillis();
        customMs = -1;
        seenSince = lastAttack;
        delayMs = delay.getValue().getRandomInRange();
    };

    private void clearSeen() {
        seenId = -1;
        missed = false;
    }

    private boolean isValid(Entity entity) {
        if (entity == mc.player || !entity.isAlive())
            return false;
        Targets mode = targets.getValue();
        if (entity instanceof Player player) {
            if (mode == Targets.Crystals || player.isSpectator() || player.getAbilities().invulnerable)
                return false;
            return TargetManager.isValidTarget(player);
        }
        if (entity instanceof EndCrystal)
            return mode != Targets.Players;
        return mode == Targets.Any && entity instanceof LivingEntity && (TargetManager.invis.getValue() || !entity.isInvisible());
    }

    /** Raises the cooldown threshold toward 100% as the server falls behind, so a lagging server never sees a double swing. */
    private float tpsAdjusted(float threshold) {
        double tps = Math.clamp(serverTps, 10.0, 20.0);
        return (float) (threshold + (1.0f - threshold) * ((20.0 - tps) / 10.0));
    }

    /** A raised shield only blocks hits from the front. */
    private boolean isShielding(Player player) {
        if (!player.isUsingItem() || !player.getUseItem().is(Items.SHIELD))
            return false;
        Vec3 toUs = mc.player.getEyePosition().subtract(player.getEyePosition()).normalize();
        return player.getLookAngle().dot(toUs) >= 0;
    }

    /** More than three diamond armour drops by the crystal mean a kill just happened; breaking it would burn the loot. */
    private boolean lootNearby(Entity crystal) {
        Vec3 centre = Vec3.atCenterOf(crystal.blockPosition());
        int count = 0;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof ItemEntity item && item.getY() >= crystal.blockPosition().getY()
                    && isDiamondArmour(item.getItem().getItem()) && item.distanceToSqr(centre) <= 225 && ++count > 3)
                return true;
        }
        return false;
    }

    private static boolean isDiamondArmour(Item item) {
        return item == Items.DIAMOND_HELMET || item == Items.DIAMOND_CHESTPLATE
                || item == Items.DIAMOND_LEGGINGS || item == Items.DIAMOND_BOOTS;
    }
}
