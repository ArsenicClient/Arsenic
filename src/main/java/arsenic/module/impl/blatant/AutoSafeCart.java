package arsenic.module.impl.blatant;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventSilentRotation;
import arsenic.gui.themes.ThemeManager;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.Group;
import arsenic.module.property.PropertyGroup;
import arsenic.module.property.ShowWhen;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.minecraft.HotbarUse;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.rotations.SilentRotationManager;
import arsenic.utils.timer.MSTimer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Puts a block between you and a TNT minecart that would blow up near you, picking the spot that cuts the most damage.
 * Silent mode finds and aims at the spot itself; Legit mode only places where you are already looking.
 */
@ModuleInfo(name = "AutoSafeCart", description = "Covers threatening TNT minecarts with blocks", category = ModuleCategory.COMBAT, tier = ModuleTier.BLATANT)
public class AutoSafeCart extends Module {

    /** A cart you placed counts as yours for this long. */
    private static final long OWN_CART_MS = 5000;
    /** A block just placed is treated as already there for this long, until the server confirms it. */
    private static final long PENDING_MS = 1000;
    private static final float AIM_TOLERANCE = 6f;
    private static final Direction[] FACES = {Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP};

    public enum Mode {
        /** Scans for threatening carts and aims at the best cover spot itself. */
        Silent,
        /** Only places on the block under the crosshair when it would cut the damage. */
        Legit
    }

    public enum Protect {
        Both,
        OnlySelf,
        OnlyEnemy
    }

    public enum SwapMode {
        /** Only uses the block already in your hand. */
        None,
        /** Switches the hotbar to the block and stays there. */
        Normal,
        /** Switches to the block for one tick and back. */
        Silent
    }

    public final EnumProperty<Mode> mode = new EnumProperty<>("Mode", Mode.Silent);
    public final EnumProperty<Protect> protect = new EnumProperty<>("Protect", Protect.Both);
    public final DoubleProperty minDamage = new DoubleProperty("Min Damage", new DoubleValue(0, 30, 5, 0.5));
    public final DoubleProperty maxDamage = new DoubleProperty("Max Damage", new DoubleValue(0, 30, 5, 0.5));
    public final DoubleProperty maxBlocks = new DoubleProperty("Max Blocks", new DoubleValue(1, 4, 2, 1));
    public final RangeProperty delay = new RangeProperty("Delay (ms)", new RangeValue(0, 500, 80, 160, 1));
    public final EnumProperty<SwapMode> swapMode = new EnumProperty<>("Swap", SwapMode.Normal);
    public final DoubleProperty range = new DoubleProperty("Range", new DoubleValue(3, 15, 10, 0.5));
    @ShowWhen(property = "Mode", is = "Silent")
    public final DoubleProperty fov = new DoubleProperty("FOV", new DoubleValue(0, 180, 180, 1));

    public final BooleanProperty useLogs = new BooleanProperty("Logs", true);
    public final BooleanProperty usePlanks = new BooleanProperty("Planks", true);
    public final BooleanProperty useCobblestone = new BooleanProperty("Cobblestone", true);
    public final BooleanProperty useObsidian = new BooleanProperty("Obsidian", true);

    @PropertyGroup(Group.VISUAL)
    public final BooleanProperty render = new BooleanProperty("Render", true);

    private final HotbarUse swap = new HotbarUse();
    private final MSTimer timer = new MSTimer();
    private double nextDelay;
    /** Blocks already placed against each cart. */
    private final Map<UUID, Integer> placedPerCart = new HashMap<>();
    /** Where you used a TNT minecart, and when, to tell your carts from others'. */
    private final List<OwnCart> ownCarts = new ArrayList<>();
    private final List<Threat> threats = new ArrayList<>();
    private final Set<UUID> liveCarts = new HashSet<>();
    private boolean threatened;
    private BlockPos pendingPos;
    private long pendingTime;
    private Level lastLevel;

    // Silent aim plan for this tick
    private Cover plan;
    private Threat planThreat;
    private Block planBlock;

    // Render
    private BlockPos renderPos;
    private long renderTime;

    private record OwnCart(BlockPos pos, long time) {}

    private record Threat(MinecartTNT cart, Vec3 pos, double speed, double damage) {}

    /** A spot to fill: where the block goes and the face it is placed against. */
    private record Cover(BlockPos pos, BlockHitResult hit, double damage) {}

    @Override
    public boolean isSwappingHotbar() {
        return swap.active();
    }

    @Override
    protected void onEnable() {
        reset();
        nextDelay = 0;
    }

    @Override
    protected void onDisable() {
        swap.release();
        reset();
    }

    private void reset() {
        placedPerCart.clear();
        ownCarts.clear();
        threats.clear();
        threatened = false;
        pendingPos = null;
        plan = null;
        planThreat = null;
        planBlock = null;
        renderPos = null;
    }

    @EventLink
    public final Listener<EventPacket.OutGoing> onPacket = event -> {
        if (mc.player == null || !(event.getPacket() instanceof ServerboundUseItemOnPacket packet))
            return;
        ItemStack stack = packet.hand() == InteractionHand.MAIN_HAND ? mc.player.getMainHandItem() : mc.player.getOffhandItem();
        if (!stack.is(Items.TNT_MINECART))
            return;
        BlockHitResult hit = packet.hitResult();
        long now = System.currentTimeMillis();
        ownCarts.add(new OwnCart(hit.getBlockPos().immutable(), now));
        ownCarts.add(new OwnCart(hit.getBlockPos().relative(hit.getDirection()).immutable(), now));
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        swap.tick();
        plan = null;
        planThreat = null;
        planBlock = null;
        if (mc.level != lastLevel) {
            reset();
            lastLevel = mc.level;
        }
        cleanup();
        if (mc.player.isUsingItem())
            return;
        Block block = findBlock();
        if (block == null)
            return;
        collectThreats();
        boolean any = !threats.isEmpty();
        if (any && !threatened)
            resetDelay();
        threatened = any;
        if (!any || !timer.hasTimeElapsed(nextDelay))
            return;

        if (mode.getValue() == Mode.Legit) {
            legit(block);
            return;
        }

        float limit = (float) fov.getValue().getInput();
        Vec3 eyes = mc.player.getEyePosition(1f);
        for (Threat threat : threats) {
            Cover cover = bestCover(threat, block);
            if (cover == null)
                continue;
            float[] rots = RotationUtils.rotationsTo(eyes, cover.hit().getLocation());
            if (Math.abs(Mth.wrapDegrees(rots[0] - mc.player.getYRot())) > limit)
                continue;
            plan = cover;
            planThreat = threat;
            planBlock = block;
            event.setYaw(rots[0]);
            event.setPitch(rots[1]);
            event.setSpeed(180f);
            event.setPreventDuplicateLook(true);
            event.setMovementFix(SilentRotationManager.MovementFix.SILENT);
            return;
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onPost = event -> {
        if (plan == null)
            return;
        Cover cover = plan;
        Threat threat = planThreat;
        Block block = planBlock;
        plan = null;
        planThreat = null;
        planBlock = null;
        float[] want = RotationUtils.rotationsTo(mc.player.getEyePosition(1f), cover.hit().getLocation());
        if (Math.abs(RotationUtils.getYawDifference(event.getYaw(), want[0])) > AIM_TOLERANCE
                || Math.abs(event.getPitch() - want[1]) > AIM_TOLERANCE)
            return;
        BlockHitResult hit = event.getRayTrace();
        if (hit == null || hit.getType() != HitResult.Type.BLOCK || hit.getLocation().distanceTo(cover.hit().getLocation()) > 0.5
                || !hit.getBlockPos().relative(hit.getDirection()).equals(cover.pos()))
            return;
        place(block, hit, cover.pos(), threat);
    };

    @EventLink
    public final Listener<EventRenderWorldLast> onRender = event -> {
        if (!render.getValue() || renderPos == null || mc.level == null)
            return;
        float life = 1f - (System.currentTimeMillis() - renderTime) / 600f;
        if (life <= 0f) {
            renderPos = null;
            return;
        }
        RenderUtils.renderBox(new AABB(renderPos), RenderUtils.withAlpha(ThemeManager.getMainColor(), Math.max(1, (int) (life * 255))), true, true);
    };

    /** Places on the crosshair block if that covers one of the carts enough. */
    private void legit(Block block) {
        if (!(mc.hitResult instanceof BlockHitResult crosshair) || crosshair.getType() != HitResult.Type.BLOCK)
            return;
        BlockState aimed = mc.level.getBlockState(crosshair.getBlockPos());
        BlockPos pos = aimed.is(Blocks.FIRE) || aimed.is(Blocks.SHORT_GRASS)
                ? crosshair.getBlockPos() : crosshair.getBlockPos().relative(crosshair.getDirection());
        if (!freeSpot(pos, block))
            return;
        BlockPos pending = pending();
        for (Threat threat : threats) {
            double after = damage(threat, pending, pos, block.defaultBlockState());
            if (after >= threat.damage() - 0.5)
                continue;
            if (place(block, crosshair, pos, threat))
                return;
        }
    }

    /** The cover spot around you or the cart that leaves the least damage, if it cuts at least half a heart. */
    private Cover bestCover(Threat threat, Block block) {
        BlockPos pending = pending();
        BlockState state = block.defaultBlockState();
        Cover best = null;
        for (BlockPos pos : candidates(threat.cart())) {
            BlockHitResult hit = support(pos, block);
            if (hit == null)
                continue;
            double after = damage(threat, pending, pos, state);
            if (after < threat.damage() - 0.5 && (best == null || after < best.damage()))
                best = new Cover(pos, hit, after);
        }
        return best;
    }

    /** The eight blocks around your feet, your head, the cart and above the cart. */
    private List<BlockPos> candidates(MinecartTNT cart) {
        LinkedHashSet<BlockPos> out = new LinkedHashSet<>();
        BlockPos self = mc.player.blockPosition();
        BlockPos at = cart.blockPosition();
        for (BlockPos base : new BlockPos[]{self, self.above(), at, at.above()}) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dz != 0)
                        out.add(base.offset(dx, 0, dz));
                }
            }
        }
        return new ArrayList<>(out);
    }

    /** A visible face in reach that a block at this spot could be placed against. */
    private BlockHitResult support(BlockPos pos, Block block) {
        if (!freeSpot(pos, block) || pos.equals(pending()))
            return null;
        Vec3 eyes = mc.player.getEyePosition(1f);
        double reach = mc.player.blockInteractionRange();
        for (Direction dir : FACES) {
            BlockPos against = pos.relative(dir);
            if (mc.level.getBlockState(against).canBeReplaced())
                continue;
            Direction face = dir.getOpposite();
            Vec3 point = Vec3.atCenterOf(against).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
            if (eyes.distanceTo(point) > reach)
                continue;
            BlockHitResult ray = mc.level.clip(new ClipContext(eyes, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (ray != null && ray.getLocation().distanceTo(point) <= 0.1)
                return new BlockHitResult(point, face, against, false);
        }
        return null;
    }

    private boolean freeSpot(BlockPos pos, Block block) {
        return mc.level.getBlockState(pos).canBeReplaced()
                && mc.level.isUnobstructed(block.defaultBlockState(), pos, CollisionContext.empty());
    }

    private boolean place(Block block, BlockHitResult hit, BlockPos pos, Threat threat) {
        int slot = slotFor(block);
        if (slot == -1)
            return false;
        if (mc.player.getInventory().getSelectedSlot() != slot) {
            if (swapMode.getValue() == SwapMode.Silent)
                swap.switchOnce(slot);
            else if (swapMode.getValue() == SwapMode.Normal)
                mc.player.getInventory().setSelectedSlot(slot);
            else
                return false;
        }
        if (!mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit).consumesAction())
            return false;
        PlayerUtils.swingItem();
        placedPerCart.merge(threat.cart().getUUID(), 1, Integer::sum);
        pendingPos = pos.immutable();
        pendingTime = System.currentTimeMillis();
        renderPos = pendingPos;
        renderTime = pendingTime;
        resetDelay();
        return true;
    }

    private void resetDelay() {
        timer.reset();
        nextDelay = delay.getValue().getRandomInRange();
    }

    /** TNT carts in range that would hurt you enough and have not had their share of blocks yet, worst first. */
    private void collectThreats() {
        threats.clear();
        Vec3 eyes = mc.player.getEyePosition(1f);
        BlockPos pending = pending();
        double maxRange = range.getValue().getInput();
        int cap = (int) maxBlocks.getValue().getInput();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof MinecartTNT cart))
                continue;
            Vec3 pos = cart.position();
            if (eyes.distanceTo(pos) > maxRange || !protects(pos) || placedPerCart.getOrDefault(cart.getUUID(), 0) >= cap)
                continue;
            double speed = Math.hypot(cart.getDeltaMovement().x, cart.getDeltaMovement().z);
            Threat probe = new Threat(cart, pos, speed, 0);
            double dmg = damage(probe, pending, null, null);
            if (dmg < minDamage.getValue().getInput() || dmg <= maxDamage.getValue().getInput())
                continue;
            threats.add(new Threat(cart, pos, speed, dmg));
        }
        threats.sort((a, b) -> Double.compare(b.damage(), a.damage()));
    }

    private boolean protects(Vec3 cartPos) {
        if (protect.getValue() == Protect.Both)
            return true;
        boolean own = false;
        long now = System.currentTimeMillis();
        for (OwnCart c : ownCarts) {
            if (now - c.time() <= OWN_CART_MS && cartPos.closerThan(Vec3.atCenterOf(c.pos()), 1.5)) {
                own = true;
                break;
            }
        }
        return protect.getValue() == Protect.OnlySelf ? own : !own;
    }

    /** The block just placed, while it may not have shown up yet. */
    private BlockPos pending() {
        if (pendingPos == null)
            return null;
        if (System.currentTimeMillis() - pendingTime <= PENDING_MS && mc.level.getBlockState(pendingPos).canBeReplaced())
            return pendingPos;
        pendingPos = null;
        return null;
    }

    private void cleanup() {
        if (!placedPerCart.isEmpty()) {
            liveCarts.clear();
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity instanceof MinecartTNT)
                    liveCarts.add(entity.getUUID());
            }
            placedPerCart.keySet().removeIf(id -> !liveCarts.contains(id));
        }
        long now = System.currentTimeMillis();
        ownCarts.removeIf(c -> now - c.time() > OWN_CART_MS);
    }

    // ---------------------------------------------------------------
    //  Blocks
    // ---------------------------------------------------------------

    /** The first enabled cover block you have (in hand only with Swap None). */
    private Block findBlock() {
        boolean handOnly = swapMode.getValue() == SwapMode.None;
        if (useLogs.getValue()) {
            Block b = findTagged(true, handOnly);
            if (b != null)
                return b;
        }
        if (usePlanks.getValue()) {
            Block b = findTagged(false, handOnly);
            if (b != null)
                return b;
        }
        if (useCobblestone.getValue() && has(Blocks.COBBLESTONE, handOnly))
            return Blocks.COBBLESTONE;
        if (useObsidian.getValue() && has(Blocks.OBSIDIAN, handOnly))
            return Blocks.OBSIDIAN;
        return null;
    }

    private Block findTagged(boolean logs, boolean handOnly) {
        if (handOnly)
            return taggedBlock(mc.player.getMainHandItem(), logs);
        for (int i = 0; i < 9; i++) {
            Block b = taggedBlock(mc.player.getInventory().getItem(i), logs);
            if (b != null)
                return b;
        }
        return null;
    }

    private static Block taggedBlock(ItemStack stack, boolean logs) {
        if (stack.isEmpty() || !stack.is(logs ? ItemTags.LOGS : ItemTags.PLANKS) || !(stack.getItem() instanceof BlockItem item))
            return null;
        return item.getBlock();
    }

    private boolean has(Block block, boolean handOnly) {
        if (handOnly)
            return mc.player.getMainHandItem().is(block.asItem());
        return HotbarUse.find(s -> s.is(block.asItem())) != -1;
    }

    private int slotFor(Block block) {
        if (mc.player.getMainHandItem().is(block.asItem()))
            return mc.player.getInventory().getSelectedSlot();
        if (swapMode.getValue() == SwapMode.None)
            return -1;
        return HotbarUse.find(s -> s.is(block.asItem()));
    }

    // ---------------------------------------------------------------
    //  Damage
    // ---------------------------------------------------------------

    /**
     * Explosion damage to you from the cart, as vanilla works it out at the largest power its speed allows, with
     * {@code pending} counted as a solid block and {@code placed} filled with {@code placedState}.
     */
    private double damage(Threat threat, BlockPos pending, BlockPos placed, BlockState placedState) {
        Player self = mc.player;
        if (self.isCreative() || self.isSpectator())
            return 0;
        double power = 4.0 + 1.5 * Math.min(threat.speed(), 5.0);
        double diameter = power * 2.0;
        double dist = Math.sqrt(self.distanceToSqr(threat.pos()));
        if (dist > diameter)
            return 0;
        double impact = (1.0 - dist / diameter) * seenPercent(threat.pos(), self.getBoundingBox(), pending, placed, placedState);
        double dmg = (impact * impact + impact) / 2.0 * 7.0 * diameter + 1.0;
        Difficulty difficulty = mc.level.getDifficulty();
        if (difficulty == Difficulty.PEACEFUL)
            return 0;
        if (difficulty == Difficulty.EASY)
            dmg = Math.min(dmg / 2.0 + 1.0, dmg);
        else if (difficulty == Difficulty.HARD)
            dmg = dmg * 3.0 / 2.0;
        MobEffectInstance resistance = self.getEffect(MobEffects.RESISTANCE);
        if (resistance != null)
            dmg *= Math.max(0.0, 1.0 - (resistance.getAmplifier() + 1) * 0.2);
        dmg = net.minecraft.world.damagesource.CombatRules.getDamageAfterAbsorb(self, (float) dmg,
                mc.level.damageSources().explosion(null), self.getArmorValue(), (float) self.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        dmg = net.minecraft.world.damagesource.CombatRules.getDamageAfterMagicAbsorb((float) dmg, protection(self));
        return Math.max(0, dmg);
    }

    /** Protection and Blast Protection levels on your armour, counted the way vanilla does for explosions. */
    private static float protection(Player self) {
        Holder<Enchantment> prot = enchantment(Enchantments.PROTECTION);
        Holder<Enchantment> blast = enchantment(Enchantments.BLAST_PROTECTION);
        int total = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = self.getItemBySlot(slot);
            if (stack.isEmpty())
                continue;
            if (prot != null)
                total += EnchantmentHelper.getItemEnchantmentLevel(prot, stack);
            if (blast != null)
                total += 2 * EnchantmentHelper.getItemEnchantmentLevel(blast, stack);
        }
        return total;
    }

    private static Holder<Enchantment> enchantment(ResourceKey<Enchantment> key) {
        return mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key).orElse(null);
    }

    /** Vanilla's share of rays from the box to the explosion that nothing blocks, with the block overrides applied. */
    private double seenPercent(Vec3 center, AABB box, BlockPos pending, BlockPos placed, BlockState placedState) {
        double sx = 1.0 / ((box.maxX - box.minX) * 2.0 + 1.0);
        double sy = 1.0 / ((box.maxY - box.minY) * 2.0 + 1.0);
        double sz = 1.0 / ((box.maxZ - box.minZ) * 2.0 + 1.0);
        double ox = (1.0 - Math.floor(1.0 / sx) * sx) / 2.0;
        double oz = (1.0 - Math.floor(1.0 / sz) * sz) / 2.0;
        if (sx < 0 || sy < 0 || sz < 0)
            return 0;
        int seen = 0, total = 0;
        for (double x = 0; x <= 1; x += sx) {
            for (double y = 0; y <= 1; y += sy) {
                for (double z = 0; z <= 1; z += sz) {
                    Vec3 from = new Vec3(Mth.lerp(x, box.minX, box.maxX) + ox, Mth.lerp(y, box.minY, box.maxY), Mth.lerp(z, box.minZ, box.maxZ) + oz);
                    if (!blocked(from, center, pending, placed, placedState))
                        seen++;
                    total++;
                }
            }
        }
        return total == 0 ? 0 : (double) seen / total;
    }

    private boolean blocked(Vec3 from, Vec3 to, BlockPos pending, BlockPos placed, BlockState placedState) {
        Level level = mc.level;
        Boolean hit = BlockGetter.traverseBlocks(from, to, level, (BlockGetter getter, BlockPos pos) -> {
            BlockState state;
            if (placed != null && pos.equals(placed))
                state = placedState;
            else if (pending != null && pos.equals(pending))
                state = Blocks.OBSIDIAN.defaultBlockState();
            else
                state = getter.getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(getter, pos);
            return shape.isEmpty() || shape.clip(from, to, pos) == null ? null : Boolean.TRUE;
        }, getter -> Boolean.FALSE);
        return hit != null && hit;
    }
}
