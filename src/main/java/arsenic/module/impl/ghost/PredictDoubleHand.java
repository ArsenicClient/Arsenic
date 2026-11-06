package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.impl.client.AntiBot;
import arsenic.module.property.Group;
import arsenic.module.property.PropertyGroup;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.minecraft.HotbarUse;
import arsenic.utils.minecraft.ItemUtils;
import arsenic.utils.timer.MSTimer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Switches your main hand to a hotbar totem when a nearby crystal, charged anchor, obsidian you could be crystalled on,
 * a falling mace or a sword hit at low health would pop you, so you hold a totem in both hands.
 */
@ModuleInfo(name = "PredictDoubleHand", description = "Main-hands a totem when you are about to pop", category = ModuleCategory.COMBAT, tier = ModuleTier.LEGIT)
public class PredictDoubleHand extends Module {

    /** How far around you charged anchors and obsidian are looked for. */
    private static final int SCAN_RADIUS = 5;
    /** Threats further than this from you are not counted (squared, 5 blocks). */
    private static final double THREAT_RANGE_SQ = 25.0;
    /** The nearest enemy must be at least this close for anything to trigger. */
    private static final double ENEMY_RANGE = 10.0;

    // Triggers
    public final BooleanProperty anchors = new BooleanProperty("Anchors", true);
    public final BooleanProperty crystals = new BooleanProperty("Crystals", true);
    public final BooleanProperty obsidian = new BooleanProperty("Obsidian", true);
    public final BooleanProperty swordPop = new BooleanProperty("Sword Pop", true);
    public final BooleanProperty mace = new BooleanProperty("Mace", true);
    public final BooleanProperty pearl = new BooleanProperty("Pearl", false);

    public final RangeProperty reswapDelay = new RangeProperty("Re-swap Delay (ms)", new RangeValue(0, 300, 30, 50, 1));
    public final DoubleProperty sensitivity = new DoubleProperty("Sensitivity", new DoubleValue(0.5, 1.5, 1.0, 0.05));

    // Pause
    @PropertyGroup(Group.ADVANCED)
    public final BooleanProperty pauseAutoCrystal = new BooleanProperty("Pause On AutoCrystal", true);
    @PropertyGroup(Group.ADVANCED)
    public final BooleanProperty pauseAutoHitCrystal = new BooleanProperty("Pause On AutoHitCrystal", true);
    @PropertyGroup(Group.ADVANCED)
    public final BooleanProperty pauseShield = new BooleanProperty("Pause While Shielding", true);

    private final MSTimer swapTimer = new MSTimer();
    private final BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();
    private long rolledDelay;
    private long lastGameTime = Long.MIN_VALUE;
    private volatile boolean pearlThrown;

    @Override
    protected void onEnable() {
        swapTimer.lastMS = 0;
        rolledDelay = 0;
        lastGameTime = Long.MIN_VALUE;
        pearlThrown = false;
    }

    @Override
    protected void onDisable() {
        pearlThrown = false;
    }

    /** Pearl: a pearl just thrown from the main hand is followed by a swap to a totem. */
    @EventLink
    public final Listener<EventPacket.OutGoing> onSend = event -> {
        if (pearl.getValue() && event.getPacket() instanceof ServerboundUseItemPacket && mc.player != null
                && mc.player.getMainHandItem().is(Items.ENDER_PEARL))
            pearlThrown = true;
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (mc.level == null || paused()) {
            pearlThrown = false;
            return;
        }
        if (mc.player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)) {
            pearlThrown = false;
            return;
        }
        int totem = HotbarUse.find(s -> s.is(Items.TOTEM_OF_UNDYING));
        if (totem == -1) {
            pearlThrown = false;
            return;
        }
        if (pearlThrown) {
            pearlThrown = false;
            mc.player.getInventory().setSelectedSlot(totem);
            return;
        }

        if (!swapTimer.hasTimeElapsed(rolledDelay) || mc.gui.screen() != null || !mc.isWindowActive())
            return;
        long time = mc.level.getGameTime();
        if (time == lastGameTime)
            return;
        lastGameTime = time;

        Player enemy = nearestEnemy();
        if (enemy == null || mc.player.distanceTo(enemy) > ENEMY_RANGE)
            return;
        if (pauseShield.getValue() && mc.player.getMainHandItem().is(Items.SHIELD) && mc.player.isUsingItem())
            return;

        float health = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        if (strongestThreat(enemy) >= health) {
            swapTo(totem);
            return;
        }
        if (swordPop.getValue() && health < 1.0F) {
            ItemStack held = enemy.getMainHandItem();
            if (ItemUtils.isSword(held) || ItemUtils.isAxe(held))
                swapTo(totem);
        }
    };

    private boolean paused() {
        return (pauseAutoCrystal.getValue() && enabled("AutoCrystal"))
                || (pauseAutoHitCrystal.getValue() && enabled("AutoHitCrystal"));
    }

    private static boolean enabled(String name) {
        Module m = Arsenic.getArsenic().getModuleManager().getModuleByName(name);
        return m != null && m.isEnabled();
    }

    private void swapTo(int slot) {
        if (mc.player.isUsingItem())
            return;
        mc.player.getInventory().setSelectedSlot(slot);
        rolledDelay = (long) reswapDelay.getValue().getRandomInRange();
        swapTimer.reset();
    }

    private Player nearestEnemy() {
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : mc.level.players()) {
            if (p == mc.player || !p.isAlive() || AntiBot.isBot(p) || Arsenic.getArsenic().getFriendManager().isFriend(p))
                continue;
            double d = mc.player.distanceToSqr(p);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    /** The biggest weighted damage any enabled threat could do to us right now. */
    private float strongestThreat(Player enemy) {
        double scale = sensitivity.getValue().getInput();
        double best = 0;
        if (crystals.getValue()) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof EndCrystal && mc.player.distanceToSqr(e) < THREAT_RANGE_SQ)
                    best = Math.max(best, explosionDamage(Vec3.atCenterOf(e.blockPosition()), 6, mc.player) * scale);
            }
        }
        boolean scanAnchors = anchors.getValue();
        boolean scanObby = obsidian.getValue() && !mc.player.onGround();
        if (scanAnchors || scanObby) {
            BlockPos base = mc.player.blockPosition();
            for (int x = -SCAN_RADIUS; x <= SCAN_RADIUS; x++) {
                for (int y = -SCAN_RADIUS; y <= SCAN_RADIUS; y++) {
                    for (int z = -SCAN_RADIUS; z <= SCAN_RADIUS; z++) {
                        scanPos.set(base.getX() + x, base.getY() + y, base.getZ() + z);
                        if (enemy.distanceToSqr(scanPos.getX() + 0.5, scanPos.getY() + 0.5, scanPos.getZ() + 0.5) >= THREAT_RANGE_SQ)
                            continue;
                        BlockState state = mc.level.getBlockState(scanPos);
                        if (scanAnchors && state.is(Blocks.RESPAWN_ANCHOR)
                                && state.getValue(BlockStateProperties.RESPAWN_ANCHOR_CHARGES) != 0) {
                            best = Math.max(best, explosionDamage(Vec3.atCenterOf(scanPos), 5, mc.player) * scale);
                        } else if (scanObby && state.is(Blocks.OBSIDIAN) && scanPos.getY() < mc.player.getY()
                                && mc.level.getBlockState(scanPos.above()).isAir()) {
                            // obsidian is half as likely to be crystalled on as a crystal already standing
                            best = Math.max(best, explosionDamage(Vec3.atCenterOf(scanPos.above()), 6, mc.player) * 0.5 * scale);
                        }
                    }
                }
            }
        }
        if (mace.getValue() && ItemUtils.isMace(enemy.getMainHandItem()) && enemy.fallDistance > 1.5
                && enemy.getY() > mc.player.getY())
            best = Math.max(best, (6.0 + enemy.fallDistance * 5.0) * scale);
        return (float) best;
    }

    /** Vanilla explosion damage of this power at this point, after difficulty, armour, toughness and Resistance. */
    private static double explosionDamage(Vec3 at, float power, LivingEntity e) {
        double radius = power * 2.0;
        double d = Math.sqrt(e.distanceToSqr(at)) / radius;
        if (d > 1)
            return 0;
        double impact = (1 - d) * exposure(at, e);
        double dmg = (impact * impact + impact) / 2 * 7 * radius + 1;
        Difficulty difficulty = mc.level.getDifficulty();
        if (difficulty == Difficulty.PEACEFUL)
            return 0;
        if (difficulty == Difficulty.EASY)
            dmg = Math.min(dmg / 2 + 1, dmg);
        else if (difficulty == Difficulty.HARD)
            dmg *= 1.5;
        double armor = e.getArmorValue();
        double toughness = e.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        double effective = Math.max(0, Math.min(20, Math.max(armor - dmg / (2 + toughness / 4), armor * 0.2)));
        dmg *= 1 - effective / 25.0;
        MobEffectInstance resistance = e.getEffect(MobEffects.RESISTANCE);
        if (resistance != null)
            dmg *= Math.max(0, 1 - (resistance.getAmplifier() + 1) * 0.2);
        return dmg;
    }

    /** The share of the entity's box corners and centre the explosion can see. */
    private static double exposure(Vec3 from, LivingEntity e) {
        AABB box = e.getBoundingBox();
        int seen = 0;
        for (int i = 0; i < 8; i++) {
            Vec3 corner = new Vec3((i & 1) == 0 ? box.minX : box.maxX, (i & 2) == 0 ? box.minY : box.maxY,
                    (i & 4) == 0 ? box.minZ : box.maxZ);
            if (clear(from, corner, e))
                seen++;
        }
        if (clear(from, box.getCenter(), e))
            seen++;
        return seen / 9.0;
    }

    private static boolean clear(Vec3 a, Vec3 b, LivingEntity e) {
        return mc.level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e))
                .getType() == HitResult.Type.MISS;
    }
}
