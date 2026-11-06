package arsenic.module.impl.blatant;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventSilentRotation;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.impl.client.TargetManager;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.minecraft.HotbarUse;
import arsenic.utils.minecraft.ItemUtils;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.rotations.SilentRotationManager;
import arsenic.utils.timer.MSTimer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * Hit-crystal macro from the crosshair: places obsidian on the block you look at, puts a crystal on it and breaks it,
 * once, twice or over and over, optionally waiting for the enemy's damage ticks so every pop lands. Bind it and it
 * switches itself off when the combo is done.
 */
@ModuleInfo(name = "AutoHitCrystal", description = "Places obsidian and a crystal where you look and breaks it", category = ModuleCategory.COMBAT, tier = ModuleTier.BLATANT)
public class AutoHitCrystal extends Module {

    public enum CrystalMode {
        None, SingleTap, DoubleTap, Infinite
    }

    /** Silent rotations only reach crystals this close to the eyes (box distance), as the original does. */
    private static final double SILENT_REACH = 2.7;
    private static final float AIM_TOLERANCE = 6f;
    private static final double ENEMY_SEARCH = 10;
    private static final Set<Item> DEATH_LOOT = Set.of(
            Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS,
            Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS,
            Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD,
            Items.DIAMOND_AXE, Items.NETHERITE_AXE, Items.TOTEM_OF_UNDYING, Items.GOLDEN_APPLE,
            Items.ENCHANTED_GOLDEN_APPLE, Items.ENDER_PEARL, Items.EXPERIENCE_BOTTLE, Items.END_CRYSTAL,
            Items.RESPAWN_ANCHOR, Items.GLOWSTONE);

    /** True while a combo is running, so other crystal modules can stay out of the way. */
    public static boolean active;

    public final BooleanProperty stopInAir = new BooleanProperty("Stop In Air", false);
    public final BooleanProperty swordOnly = new BooleanProperty("Sword Only", false);
    public final BooleanProperty pauseOnKill = new BooleanProperty("Pause On Kill", false);
    public final RangeProperty delay = new RangeProperty("Delay (ms)", new RangeValue(0, 200, 30, 50, 1));
    public final BooleanProperty perfectTiming = new BooleanProperty("Perfect Timing", false);
    public final EnumProperty<CrystalMode> crystal = new EnumProperty<>("Crystal", CrystalMode.SingleTap);
    public final RangeProperty crystalDelay = new RangeProperty("Crystal Delay (ms)", new RangeValue(0, 200, 50, 150, 1));
    public final BooleanProperty silentRotations = new BooleanProperty("Silent Rotations", false);

    private final MSTimer timer = new MSTimer();
    private double nextDelay, nextCrystalDelay;
    /**
     * 0 look for a block or crystal, 1 place obsidian, 2 take a crystal, 3 place the first crystal, 4 break it,
     * 5 place the next crystal, 6 break that one.
     */
    private int stage;
    private EndCrystal silentTarget;
    private Vec3 aim;

    public AutoHitCrystal() {
        crystalDelay.setVisible(() -> crystal.getValue() == CrystalMode.DoubleTap || crystal.getValue() == CrystalMode.Infinite);
    }

    @Override
    public String getHudInfo() {
        return crystal.getValue().name().toLowerCase();
    }

    @Override
    protected void onEnable() {
        stage = 0;
        roll();
        timer.reset();
        clearSilent();
        active = crystal.getValue() != CrystalMode.None;
    }

    @Override
    protected void onDisable() {
        active = false;
        stage = 0;
        clearSilent();
    }

    private void clearSilent() {
        silentTarget = null;
        aim = null;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        clearSilent();
        if (mc.level == null || mc.gui.screen() != null || !mc.isWindowActive() || timer.getTime() < nextDelay)
            return;
        if ((stopInAir.getValue() && !mc.player.onGround()) || mc.player.isUsingItem())
            return;
        if (ItemUtils.isSpear(mc.player.getMainHandItem()))
            return;

        Player enemy = nearestEnemy();
        if (stage == 0 && swordOnly.getValue() && !ItemUtils.isSword(mc.player.getMainHandItem())) {
            setEnabled(false);
            return;
        }
        HitResult hit = mc.hitResult;
        if (hit == null)
            return;
        CrystalMode mode = crystal.getValue();
        if (mode != CrystalMode.None)
            active = true;

        switch (stage) {
            case 0 -> {
                if (hit instanceof EntityHitResult eh && eh.getEntity() instanceof EndCrystal endCrystal) {
                    if (mode == CrystalMode.None || (pauseOnKill.getValue() && bodyNear(endCrystal.blockPosition()))) {
                        active = false;
                        return;
                    }
                    if (waitForHurt(enemy))
                        return;
                    attack(endCrystal);
                    roll();
                    timer.reset();
                    active = false;
                    return;
                }
                BlockHitResult bh = blockHit(hit);
                if (bh == null || !canPlaceAgainst(mc.level.getBlockState(bh.getBlockPos()))) {
                    active = false;
                    return;
                }
                if (mc.level.getBlockState(bh.getBlockPos()).is(Blocks.OBSIDIAN)) {
                    stage = 2;
                    return;
                }
                if (!select(Items.OBSIDIAN)) {
                    active = false;
                    return;
                }
                advance();
            }
            case 1 -> {
                BlockHitResult bh = blockHit(hit);
                if (bh == null || !canPlaceAgainst(mc.level.getBlockState(bh.getBlockPos()))) {
                    active = false;
                    return;
                }
                BlockPos pos = bh.getBlockPos();
                if (occupied(pos.relative(bh.getDirection()))) {
                    active = false;
                    return;
                }
                if (mc.level.getBlockState(pos).is(Blocks.OBSIDIAN)) {
                    active = false;
                    advance();
                    return;
                }
                if (occupied(pos)) {
                    active = false;
                    return;
                }
                if (!select(Items.OBSIDIAN))
                    return;
                interact(bh);
                advance();
            }
            case 2 -> {
                if (!select(Items.END_CRYSTAL))
                    return;
                advance();
            }
            case 3, 5 -> {
                if (mode == CrystalMode.None) {
                    setEnabled(false);
                    return;
                }
                if (stage == 5 && timer.getTime() < nextCrystalDelay)
                    return;
                BlockHitResult bh = blockHit(hit);
                if (bh == null)
                    return;
                BlockPos pos = bh.getBlockPos();
                if (!mc.level.getBlockState(pos).is(Blocks.OBSIDIAN) || occupied(pos.above()))
                    return;
                if (!select(Items.END_CRYSTAL))
                    return;
                interact(bh);
                advance();
            }
            case 4, 6 -> {
                if (stage == 6 && timer.getTime() < nextCrystalDelay)
                    return;
                if (waitForHurt(enemy))
                    return;
                if (hit instanceof EntityHitResult eh && eh.getEntity() instanceof EndCrystal endCrystal) {
                    if (pauseOnKill.getValue() && bodyNear(endCrystal.blockPosition()))
                        return;
                    attack(endCrystal);
                    afterBreak();
                    return;
                }
                if (silentRotations.getValue())
                    planSilent(hit, event);
            }
            default -> stage = 0;
        }
    };

    /** Breaks the crystal the silent rotation was aimed at, once the rotation being sent points at it. */
    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onPost = event -> {
        if (aim == null || silentTarget == null)
            return;
        float[] want = RotationUtils.rotationsTo(mc.player.getEyePosition(1f), aim);
        if (Math.abs(RotationUtils.getYawDifference(event.getYaw(), want[0])) > AIM_TOLERANCE
                || Math.abs(event.getPitch() - want[1]) > AIM_TOLERANCE)
            return;
        EndCrystal target = silentTarget;
        clearSilent();
        if (!target.isAlive())
            return;
        attack(target);
        afterBreak();
    };

    /** After a crystal is broken in stage 4 or 6: stop, go on to the next crystal, or loop for Infinite. */
    private void afterBreak() {
        CrystalMode mode = crystal.getValue();
        if (mode == CrystalMode.Infinite) {
            stage = 5;
            roll();
            timer.reset();
        } else if (stage == 4 && mode == CrystalMode.DoubleTap) {
            advance();
        } else {
            setEnabled(false);
        }
    }

    /**
     * The crosshair is on the obsidian rather than the crystal (it sits in the way of the top face): aims silently at the
     * crystal on top of it. Looking at anything else ends the combo.
     */
    private void planSilent(HitResult hit, EventSilentRotation event) {
        BlockHitResult bh = blockHit(hit);
        if (bh == null || !mc.level.getBlockState(bh.getBlockPos()).is(Blocks.OBSIDIAN)) {
            setEnabled(false);
            return;
        }
        Vec3 spot = Vec3.atCenterOf(bh.getBlockPos().above());
        Vec3 eyes = mc.player.getEyePosition(1f);
        for (Entity e : mc.level.getEntities((Entity) null, new AABB(bh.getBlockPos().above()).inflate(1), x -> x instanceof EndCrystal)) {
            Vec3 pos = e.position();
            if (pos.distanceTo(spot) >= 1)
                continue;
            if (boxDistance(eyes, e.getBoundingBox()) > SILENT_REACH)
                continue;
            if (pauseOnKill.getValue() && bodyNear(e.blockPosition()))
                continue;
            silentTarget = (EndCrystal) e;
            aim = new Vec3(pos.x, pos.y + 0.45, pos.z);
            float[] rots = RotationUtils.rotationsTo(eyes, aim);
            event.setYaw(rots[0]);
            event.setPitch(rots[1]);
            event.setSpeed(180f);
            event.setPreventDuplicateLook(true);
            event.setMovementFix(SilentRotationManager.MovementFix.SILENT);
            return;
        }
    }

    private static double boxDistance(Vec3 eyes, AABB box) {
        double dx = Math.max(box.minX - eyes.x, Math.max(0, eyes.x - box.maxX));
        double dy = Math.max(box.minY - eyes.y, Math.max(0, eyes.y - box.maxY));
        double dz = Math.max(box.minZ - eyes.z, Math.max(0, eyes.z - box.maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Perfect Timing: hold the break while the nearest enemy is still in hurt ticks. */
    private boolean waitForHurt(Player enemy) {
        return perfectTiming.getValue() && enemy != null && enemy.hurtTime != 0;
    }

    private void advance() {
        roll();
        stage++;
        timer.reset();
    }

    private void roll() {
        nextDelay = delay.getValue().getRandomInRange();
        nextCrystalDelay = crystalDelay.getValue().getRandomInRange();
    }

    /** Attack first, then the swing, the order a 1.9+ client sends them in. */
    private static void attack(Entity entity) {
        mc.gameMode.attack(mc.player, entity);
        PlayerUtils.swingItem();
    }

    private static void interact(BlockHitResult hit) {
        InteractionResult result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        if (result.consumesAction())
            PlayerUtils.swingItem();
    }

    /** Holds the item from the hotbar (the combo keeps it, like the original). False when there is none. */
    private static boolean select(Item item) {
        if (mc.player.getMainHandItem().is(item))
            return true;
        int slot = HotbarUse.find(s -> s.is(item));
        if (slot == -1)
            return false;
        mc.player.getInventory().setSelectedSlot(slot);
        return true;
    }

    private static BlockHitResult blockHit(HitResult hit) {
        return hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK ? bh : null;
    }

    /** A block something can be placed on or into: solid, or fire, short grass or a single snow layer. */
    private static boolean canPlaceAgainst(BlockState state) {
        return !state.canBeReplaced()
                || state.is(Blocks.FIRE)
                || state.is(Blocks.SHORT_GRASS)
                || (state.is(Blocks.SNOW) && state.getValue(SnowLayerBlock.LAYERS) == 1);
    }

    /** Any entity other than a dropped item inside the block space. */
    private static boolean occupied(BlockPos pos) {
        return !mc.level.getEntities((Entity) null, new AABB(pos), e -> !(e instanceof ItemEntity)).isEmpty();
    }

    /** Pause On Kill: more than three pieces of dropped gear near the spot means someone just died. */
    private static boolean bodyNear(BlockPos pos) {
        Vec3 centre = Vec3.atCenterOf(pos);
        int count = 0;
        for (Entity e : mc.level.getEntities((Entity) null, new AABB(pos).inflate(15), x -> x instanceof ItemEntity)) {
            ItemEntity item = (ItemEntity) e;
            if (item.getY() < pos.getY() || item.position().distanceTo(centre) > 15)
                continue;
            if (isDeathLoot(item.getItem()) && ++count > 3)
                return true;
        }
        return false;
    }

    /** Gear a dead crystal PvPer drops: diamond or netherite armour and tools, totems, gapples, pearls and the like. */
    private static boolean isDeathLoot(ItemStack stack) {
        return DEATH_LOOT.contains(stack.getItem());
    }

    private static Player nearestEnemy() {
        Player best = null;
        for (Player p : mc.level.players()) {
            if (!TargetManager.isValidTarget(p) || mc.player.distanceTo(p) > ENEMY_SEARCH)
                continue;
            if (best == null || mc.player.distanceTo(p) < mc.player.distanceTo(best))
                best = p;
        }
        return best;
    }
}
