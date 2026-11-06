package arsenic.module.impl.blatant;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventPlayerJoinWorld;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventSilentRotation;
import arsenic.gui.themes.ThemeManager;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.ShowWhen;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.minecraft.HotbarUse;
import arsenic.utils.minecraft.ItemUtils;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.rotations.SilentRotationManager;
import arsenic.utils.timer.MSTimer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Puts a glowstone or obsidian block between you and a respawn anchor so its blast does not hurt you. Legit covers the
 * anchor you just placed when your crosshair is on a good spot; Blatant aims at and covers nearby anchors by itself.
 */
@ModuleInfo(name = "SafeAnchor", description = "Covers respawn anchors so they do not damage you", category = ModuleCategory.COMBAT, tier = ModuleTier.BLATANT)
public class SafeAnchor extends Module {

    private static final float ANCHOR_POWER = 5f;
    private static final EquipmentSlot[] ARMOUR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final int SCAN_RADIUS = 10;
    private static final int COVER_RADIUS = 5;
    private static final float AIM_TOLERANCE = 6f;
    private static final long SAME_BLOCK_MS = 300;

    public enum Mode { Legit, Blatant }

    public enum When { BeforeCharge, AfterCharge }

    public enum SwapMode {
        /** Only covers with the block already in your hand. */
        None,
        /** Switches the hotbar to the block and stays there. */
        Normal,
        /** Switches for the cover and goes back the next tick. */
        Silent
    }

    public enum CoverBlock { Glowstone, Obsidian }

    public final EnumProperty<Mode> mode = new EnumProperty<>("Mode", Mode.Legit);
    @ShowWhen(property = "Mode", is = "Blatant")
    public final BooleanProperty onlyOwn = new BooleanProperty("Only Own", true);
    public final DoubleProperty minDamage = new DoubleProperty("Min Damage", new DoubleValue(0, 30, 5, 0.5));
    public final DoubleProperty maxDamage = new DoubleProperty("Max Damage", new DoubleValue(0, 30, 5, 0.5));
    @ShowWhen(property = "Mode", is = "Legit")
    public final EnumProperty<When> when = new EnumProperty<>("When", When.BeforeCharge);
    public final RangeProperty delay = new RangeProperty("Delay (ms)", new RangeValue(0, 500, 20, 100, 1));
    public final EnumProperty<SwapMode> swapMode = new EnumProperty<>("Swap", SwapMode.None);
    public final EnumProperty<CoverBlock> coverBlock = new EnumProperty<>("Block", CoverBlock.Glowstone);
    public final DoubleProperty limit = new DoubleProperty("Limit", new DoubleValue(1, 5, 1, 1));
    @ShowWhen(property = "Mode", is = "Blatant")
    public final DoubleProperty fov = new DoubleProperty("FOV", new DoubleValue(0, 180, 180, 1));
    public final BooleanProperty render = new BooleanProperty("Render", true);

    /** True while this is about to cover an anchor, so other anchor modules can hold off. */
    private static boolean covering;

    private final HotbarUse swap = new HotbarUse();
    private final MSTimer timer = new MSTimer();
    private final BlockPos.MutableBlockPos scan = new BlockPos.MutableBlockPos();
    /** Covers already placed per anchor. */
    private final Map<BlockPos, Integer> placed = new HashMap<>();
    /** Blatant: when each anchor was first seen and the reaction time drawn for it. */
    private final Map<BlockPos, long[]> noticed = new HashMap<>();
    /** Anchors we placed ourselves. */
    private final Set<BlockPos> own = new HashSet<>();
    private BlockPos legitAnchor;
    private int lastCharge;
    private double nextDelay;
    private BlockPos lastPos;
    private long lastTime;

    // Blatant plan for this tick
    private BlockPos aimAnchor;
    private BlockPos aimSupport;
    private Vec3 aim;

    // Render
    private BlockPos shownCover;
    private float coverFade;
    private long lastFrame;

    /** Whether SafeAnchor is about to put a cover block down this tick. */
    public static boolean isCovering() {
        return covering;
    }

    @Override
    public boolean isSwappingHotbar() {
        return swap.active();
    }

    @Override
    protected void onEnable() {
        reset();
    }

    @Override
    protected void onDisable() {
        swap.release();
        reset();
    }

    private void reset() {
        placed.clear();
        noticed.clear();
        own.clear();
        legitAnchor = null;
        lastCharge = 0;
        nextDelay = 0;
        lastPos = null;
        aimAnchor = null;
        aimSupport = null;
        aim = null;
        shownCover = null;
        coverFade = 0;
        covering = false;
    }

    @EventLink
    public final Listener<EventPlayerJoinWorld> onJoin = event -> reset();

    /** Remembers anchors we place: they are the Legit target and the only ones Only Own covers. */
    @RequiresPlayer
    @EventLink
    public final Listener<EventPacket.OutGoing> onPacket = event -> {
        if (!(event.getPacket() instanceof ServerboundUseItemOnPacket packet) || mc.level == null)
            return;
        ItemStack held = packet.hand() == InteractionHand.MAIN_HAND ? mc.player.getMainHandItem() : mc.player.getOffhandItem();
        if (!held.is(Items.RESPAWN_ANCHOR))
            return;
        BlockHitResult hit = packet.hitResult();
        BlockState state = mc.level.getBlockState(hit.getBlockPos());
        BlockPos pos = state.is(Blocks.RESPAWN_ANCHOR) || inPlace(state) ? hit.getBlockPos() : hit.getBlockPos().relative(hit.getDirection());
        pos = pos.immutable();
        own.add(pos);
        if (mode.getValue() == Mode.Legit) {
            legitAnchor = pos;
            lastCharge = 0;
            timer.reset();
            nextDelay = delay.getValue().getRandomInRange();
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        swap.tick();
        aimAnchor = null;
        aimSupport = null;
        aim = null;
        covering = false;
        if (mc.level == null)
            return;

        own.removeIf(p -> !isAnchor(p));
        placed.keySet().removeIf(p -> !isAnchor(p));
        noticed.keySet().removeIf(p -> !isAnchor(p));

        if (mc.gui.screen() != null || mc.player.isUsingItem())
            return;
        if (mode.getValue() == Mode.Legit) {
            legit();
            return;
        }
        if (!timer.hasTimeElapsed(nextDelay) || !hasCoverBlock())
            return;
        planBlatant();
        if (aim == null)
            return;
        covering = true;
        float[] rots = RotationUtils.rotationsTo(mc.player.getEyePosition(1f), aim);
        event.setYaw(rots[0]);
        event.setPitch(rots[1]);
        event.setSpeed(180f);
        event.setPreventDuplicateLook(true);
        event.setMovementFix(SilentRotationManager.MovementFix.SILENT);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onPost = event -> {
        if (aim == null || aimSupport == null || aimAnchor == null)
            return;
        float[] want = RotationUtils.rotationsTo(mc.player.getEyePosition(1f), aim);
        boolean onTarget = Math.abs(RotationUtils.getYawDifference(event.getYaw(), want[0])) <= AIM_TOLERANCE
                && Math.abs(event.getPitch() - want[1]) <= AIM_TOLERANCE;
        BlockPos support = aimSupport;
        BlockPos anchor = aimAnchor;
        aim = null;
        aimSupport = null;
        aimAnchor = null;
        if (!onTarget)
            return;
        BlockHitResult hit = event.getRayTrace();
        if (hit == null || hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(support)
                || hit.getDirection() != Direction.UP || recentlyTouched(support))
            return;
        if (place(hit, support.above())) {
            placed.merge(anchor, 1, Integer::sum);
            timer.reset();
            nextDelay = delay.getValue().getRandomInRange();
        }
    };

    @EventLink
    public final Listener<EventRenderWorldLast> onRender = event -> {
        long now = System.currentTimeMillis();
        float step = lastFrame == 0 ? 0 : Math.min(1f, (now - lastFrame) / 400f);
        lastFrame = now;
        if (!render.getValue() || shownCover == null)
            return;
        coverFade = Math.max(0, coverFade - step);
        if (coverFade <= 0.01f) {
            shownCover = null;
            return;
        }
        float pulse = (float) (0.5 + 0.5 * Math.sin(now / 160.0));
        int alpha = Math.max(1, Math.round(255 * coverFade * (0.6f + 0.4f * pulse)));
        AABB box = new AABB(shownCover).inflate(-(1 - coverFade) * 0.45);
        RenderUtils.renderBox(box, RenderUtils.withAlpha(ThemeManager.getGradientColor(), alpha), true, true);
    };

    // ---- Legit ----

    /** Covers the anchor we just placed through whatever block the crosshair is on, once the delay has passed. */
    private void legit() {
        BlockPos anchor = legitAnchor;
        if (anchor == null)
            return;
        if (!isAnchor(anchor) || mc.player.getEyePosition(1f).distanceTo(Vec3.atCenterOf(anchor)) > mc.player.blockInteractionRange()) {
            legitAnchor = null;
            return;
        }
        if (mc.player.getY() < anchor.getY() || placed.getOrDefault(anchor, 0) >= (int) limit.getValue().getInput()) {
            lastCharge = 0;
            return;
        }
        // A fresh charge restarts the reaction delay.
        int charge = charges(anchor);
        if (charge > 0 && lastCharge == 0) {
            timer.reset();
            nextDelay = delay.getValue().getRandomInRange();
        }
        lastCharge = charge;
        if (when.getValue() == When.AfterCharge && charge == 0)
            return;
        if (!hasCoverBlock())
            return;
        Vec3 boom = Vec3.atCenterOf(anchor);
        if (explosionDamage(mc.player, boom, anchor, null) < minDamage.getValue().getInput())
            return;
        covering = true;
        if (!mc.player.onGround() || !timer.hasTimeElapsed(nextDelay))
            return;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK)
            return;
        BlockPos target = hit.getBlockPos();
        if (target.equals(anchor) || recentlyTouched(target))
            return;
        boolean replace = inPlace(mc.level.getBlockState(target));
        // The cover has to land level with the anchor.
        if (replace ? target.getY() > anchor.getY() : target.getY() >= anchor.getY())
            return;
        if (target.getY() < anchor.getY() - 1)
            return;
        BlockPos cover = replace ? target : target.relative(hit.getDirection());
        if (cover.equals(anchor) || occupied(cover))
            return;
        if (explosionDamage(mc.player, boom, anchor, cover) > maxDamage.getValue().getInput())
            return;
        if (place(hit, cover))
            placed.merge(anchor, 1, Integer::sum);
    }

    // ---- Blatant ----

    /** The cover spot around a nearby anchor that leaves the least damage, inside the FOV and actually hittable. */
    private void planBlatant() {
        Vec3 eyes = mc.player.getEyePosition(1f);
        double reach = mc.player.blockInteractionRange();
        float yaw = mc.player.getYRot();
        double maxFov = fov.getValue().getInput();
        double maxDmg = maxDamage.getValue().getInput();
        int cap = (int) limit.getValue().getInput();
        long now = System.currentTimeMillis();
        BlockPos origin = mc.player.blockPosition();
        double best = Double.MAX_VALUE;

        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dy = -SCAN_RADIUS; dy <= SCAN_RADIUS; dy++) {
                for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                    scan.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!isAnchor(scan))
                        continue;
                    Vec3 boom = Vec3.atCenterOf(scan);
                    if (eyes.distanceTo(boom) > SCAN_RADIUS)
                        continue;
                    BlockPos anchor = scan.immutable();
                    long[] seen = noticed.computeIfAbsent(anchor, p -> new long[]{now, (long) delay.getValue().getRandomInRange()});
                    if (now - seen[0] < seen[1])
                        continue;
                    if (onlyOwn.getValue() && !own.contains(anchor))
                        continue;
                    if (placed.getOrDefault(anchor, 0) >= cap)
                        continue;
                    if (eyes.distanceTo(boom) > reach)
                        continue;
                    if (explosionDamage(mc.player, boom, anchor, null) < minDamage.getValue().getInput())
                        continue;
                    for (int cx = -COVER_RADIUS; cx <= COVER_RADIUS; cx++) {
                        for (int cz = -COVER_RADIUS; cz <= COVER_RADIUS; cz++) {
                            if (cx == 0 && cz == 0)
                                continue;
                            BlockPos support = anchor.offset(cx, -1, cz);
                            BlockPos cover = support.above();
                            if (!solid(support) || !mc.level.getBlockState(cover).canBeReplaced() || occupied(cover))
                                continue;
                            Vec3 top = new Vec3(support.getX() + 0.5, support.getY() + 1, support.getZ() + 0.5);
                            if (eyes.y <= top.y || eyes.distanceTo(top) > reach)
                                continue;
                            float[] rots = RotationUtils.rotationsTo(eyes, top);
                            if (Math.abs(RotationUtils.getYawDifference(rots[0], yaw)) > maxFov)
                                continue;
                            double dmg = explosionDamage(mc.player, boom, anchor, cover);
                            if (dmg > maxDmg || dmg >= best)
                                continue;
                            BlockHitResult hit = mc.level.clip(new ClipContext(eyes,
                                    eyes.add(Entity.calculateViewVector(rots[1], rots[0]).scale(reach + 0.5)),
                                    ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
                            if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(support)
                                    || hit.getDirection() != Direction.UP || hit.getLocation().distanceTo(top) > 0.5)
                                continue;
                            best = dmg;
                            aimAnchor = anchor;
                            aimSupport = support;
                            aim = top;
                        }
                    }
                }
            }
        }
    }

    // ---- placing ----

    /** Gets the cover block in hand per the Swap setting and places it on the hit. */
    private boolean place(BlockHitResult hit, BlockPos cover) {
        Item item = coverItem();
        if (!mc.player.getMainHandItem().is(item)) {
            if (swapMode.getValue() == SwapMode.None)
                return false;
            int slot = HotbarUse.find(s -> s.is(item));
            if (slot == -1)
                return false;
            if (swapMode.getValue() == SwapMode.Silent)
                swap.switchOnce(slot);
            else
                mc.player.getInventory().setSelectedSlot(slot);
        }
        if (mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit).consumesAction())
            PlayerUtils.swingItem();
        lastPos = hit.getBlockPos().immutable();
        lastTime = System.currentTimeMillis();
        shownCover = cover.immutable();
        coverFade = 1f;
        return true;
    }

    private Item coverItem() {
        return coverBlock.getValue() == CoverBlock.Glowstone ? Items.GLOWSTONE : Items.OBSIDIAN;
    }

    private boolean hasCoverBlock() {
        Item item = coverItem();
        if (swapMode.getValue() == SwapMode.None)
            return mc.player.getMainHandItem().is(item);
        return mc.player.getMainHandItem().is(item) || HotbarUse.find(s -> s.is(item)) != -1;
    }

    private boolean recentlyTouched(BlockPos pos) {
        return pos.equals(lastPos) && System.currentTimeMillis() - lastTime < SAME_BLOCK_MS;
    }

    // ---- world ----

    private static boolean isAnchor(BlockPos pos) {
        return mc.level.getBlockState(pos).is(Blocks.RESPAWN_ANCHOR);
    }

    private static int charges(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        return state.is(Blocks.RESPAWN_ANCHOR) ? state.getValue(RespawnAnchorBlock.CHARGE) : 0;
    }

    /** Fire and short grass are replaced by a block placed on them rather than built against. */
    private static boolean inPlace(BlockState state) {
        return state.is(Blocks.FIRE) || state.is(Blocks.SHORT_GRASS);
    }

    /** A block a cover can be placed on top of (counting fire, short grass and single snow layers). */
    private static boolean solid(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        return !state.canBeReplaced() || inPlace(state)
                || (state.is(Blocks.SNOW) && state.getValue(BlockStateProperties.LAYERS) == 1);
    }

    private static boolean occupied(BlockPos pos) {
        return !mc.level.getEntities((Entity) null, new AABB(pos), e -> !(e instanceof ItemEntity)).isEmpty();
    }

    // ---- damage ----

    /**
     * Vanilla anchor blast damage (power 5) to the entity, with the anchor itself gone (it is removed before it
     * explodes) and optionally a cover block that is not there yet treated as solid. Armour, toughness, resistance and
     * protection enchantments are applied.
     */
    private static double explosionDamage(LivingEntity e, Vec3 boom, BlockPos ignore, BlockPos cover) {
        double d = e.position().distanceTo(boom) / (ANCHOR_POWER * 2);
        if (d > 1)
            return 0;
        double impact = (1 - d) * exposure(boom, e.getBoundingBox(), ignore, cover);
        double dmg = (impact * impact + impact) / 2 * 7 * ANCHOR_POWER * 2 + 1;
        if (e instanceof Player) {
            Difficulty difficulty = mc.level.getDifficulty();
            if (difficulty == Difficulty.PEACEFUL)
                return 0;
            if (difficulty == Difficulty.EASY)
                dmg = Math.min(dmg / 2 + 1, dmg);
            else if (difficulty == Difficulty.HARD)
                dmg *= 1.5;
        }
        float armour = e.getArmorValue();
        float toughness = (float) e.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        float f = 2f + toughness / 4f;
        float effective = Mth.clamp(armour - (float) dmg / f, armour * 0.2f, 20f);
        dmg *= 1 - effective / 25f;
        MobEffectInstance resistance = e.getEffect(MobEffects.RESISTANCE);
        if (resistance != null)
            dmg *= Math.max(0, 1 - (resistance.getAmplifier() + 1) * 0.2);
        int epf = 0;
        for (EquipmentSlot slot : ARMOUR_SLOTS) {
            ItemStack piece = e.getItemBySlot(slot);
            if (piece.isEmpty())
                continue;
            epf += ItemUtils.enchantLevel(Enchantments.PROTECTION, piece)
                    + 2 * ItemUtils.enchantLevel(Enchantments.BLAST_PROTECTION, piece);
        }
        dmg *= 1 - Math.min(20, epf) / 25.0;
        return Math.max(0, dmg);
    }

    /** The vanilla sample grid over the box: the share of points with a clear line to the blast. */
    private static double exposure(Vec3 boom, AABB box, BlockPos ignore, BlockPos cover) {
        double sx = 1 / ((box.maxX - box.minX) * 2 + 1);
        double sy = 1 / ((box.maxY - box.minY) * 2 + 1);
        double sz = 1 / ((box.maxZ - box.minZ) * 2 + 1);
        double ox = (1 - Math.floor(1 / sx) * sx) / 2;
        double oz = (1 - Math.floor(1 / sz) * sz) / 2;
        int seen = 0, total = 0;
        for (double x = 0; x <= 1; x += sx) {
            for (double y = 0; y <= 1; y += sy) {
                for (double z = 0; z <= 1; z += sz) {
                    Vec3 point = new Vec3(Mth.lerp(x, box.minX, box.maxX) + ox, Mth.lerp(y, box.minY, box.maxY),
                            Mth.lerp(z, box.minZ, box.maxZ) + oz);
                    if (!blocked(point, boom, ignore, cover))
                        seen++;
                    total++;
                }
            }
        }
        return total == 0 ? 0 : (double) seen / total;
    }

    private static boolean blocked(Vec3 from, Vec3 to, BlockPos ignore, BlockPos cover) {
        return BlockGetter.traverseBlocks(from, to, null, (ctx, pos) -> {
            if (pos.equals(ignore))
                return null;
            if (pos.equals(cover))
                return Boolean.TRUE;
            BlockState state = mc.level.getBlockState(pos);
            if (state.isAir())
                return null;
            VoxelShape shape = state.getCollisionShape(mc.level, pos);
            return !shape.isEmpty() && shape.clip(from, to, pos) != null ? Boolean.TRUE : null;
        }, ctx -> Boolean.FALSE);
    }
}
