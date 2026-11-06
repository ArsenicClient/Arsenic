package arsenic.module.impl.blatant;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventSilentRotation;
import arsenic.gui.themes.ThemeManager;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.impl.client.TargetManager;
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
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * End crystal PvP. Auto mode breaks a crystal that would hurt the nearest player enough without hurting you much, and
 * otherwise places one on obsidian or bedrock where it would; the aim is silent and the action goes out only once the
 * rotation being sent points at it. Crosshair mode only works on what you look at: it places on the obsidian or bedrock
 * under your crosshair and breaks the crystal under it, with a random delay between actions.
 */
@ModuleInfo(name = "AutoCrystal", description = "Places and breaks end crystals around the nearest player", category = ModuleCategory.COMBAT, tier = ModuleTier.BLATANT)
public class AutoCrystal extends Module {

    private static final double ENEMY_SEARCH = 10;
    private static final float AIM_TOLERANCE = 6f;
    /** Crosshair mode: how long Always Break Last keeps going before it gives up and turns off. */
    private static final int FINISH_TICKS = 20;
    /** Head Bob only reaches crystals this close to the eyes, and the aimed ray must hit within the second value. */
    private static final double HEAD_BOB_REACH = 2.7, HEAD_BOB_HIT = 2.9;

    public enum Mode {
        /** Picks the best place and break spots around the nearest player and aims at them silently. */
        Auto,
        /** Places and breaks only what the crosshair is on, like a fast hand. */
        Crosshair
    }

    public final EnumProperty<Mode> mode = new EnumProperty<>("Mode", Mode.Auto);

    @ShowWhen(property = "Mode", is = "Auto")
    public final DoubleProperty range = new DoubleProperty("Range", new DoubleValue(3, 6, 4.5, 0.5));
    @ShowWhen(property = "Mode", is = "Auto")
    public final DoubleProperty minDamage = new DoubleProperty("Min Damage", new DoubleValue(1, 20, 5, 0.5));
    @ShowWhen(property = "Mode", is = "Auto")
    public final DoubleProperty maxSelfDamage = new DoubleProperty("Max Self Damage", new DoubleValue(1, 20, 6, 0.5));
    @ShowWhen(property = "Mode", is = "Auto")
    public final DoubleProperty delay = new DoubleProperty("Delay (ticks)", new DoubleValue(0, 10, 2, 1));
    @ShowWhen(property = "Mode", is = "Auto")
    public final BooleanProperty place = new BooleanProperty("Place", true);
    @ShowWhen(property = "Mode", is = "Auto")
    public final BooleanProperty breakCrystals = new BooleanProperty("Break", true);

    @ShowWhen(property = "Mode", is = "Crosshair")
    public final RangeProperty clickDelay = new RangeProperty("Click Delay (ms)", new RangeValue(0, 200, 30, 130, 1));
    @ShowWhen(property = "Mode", is = "Crosshair")
    public final BooleanProperty inAir = new BooleanProperty("In Air", false);
    @ShowWhen(property = "Mode", is = "Crosshair")
    public final BooleanProperty damageTick = new BooleanProperty("Damage Tick", false);
    @ShowWhen(property = "Mode", is = "Crosshair")
    public final BooleanProperty pauseOnKill = new BooleanProperty("Pause On Kill", false);
    @ShowWhen(property = "Mode", is = "Crosshair")
    public final BooleanProperty autoSwitch = new BooleanProperty("Switch", false);
    @ShowWhen(property = "Mode", is = "Crosshair")
    public final BooleanProperty headBob = new BooleanProperty("Head Bob", false);
    @ShowWhen(property = "Mode", is = "Crosshair")
    public final BooleanProperty alwaysBreakLast = new BooleanProperty("Always Break Last", true);

    @PropertyGroup(Group.VISUAL)
    public final BooleanProperty render = new BooleanProperty("Render", true);

    private final HotbarUse swap = new HotbarUse();
    private int cooldown;
    private EndCrystal breakTarget;
    private BlockHitResult placeHit;
    private Vec3 aim;

    // Crosshair mode
    private final MSTimer clickTimer = new MSTimer();
    private double nextClickDelay;
    private Entity pendingAttack;
    private BlockPos pendingPlace;
    private InteractionHand pendingHand;
    private Player pendingEnemy;
    /** Head Bob's aimed pitch this tick, or NaN when it is not bobbing. */
    private float bobPitch = Float.NaN;
    /** The last block a crystal was placed on, which Head Bob looks above first. */
    private BlockPos lastPlaced;
    /** Damage the last broken crystal did to the enemy while it is still in its hurt time (Damage Tick). */
    private double lastDamage;
    private boolean finishing;
    private int finishTicks;

    // Render
    private BlockPos renderPos;
    private AABB renderBox;
    private float renderAlpha;
    private long lastFrame, lastAction;

    @Override
    public boolean isSwappingHotbar() {
        return swap.active();
    }

    @Override
    protected void onEnable() {
        finishing = false;
        finishTicks = 0;
        nextClickDelay = 0;
        lastDamage = 0;
        lastPlaced = null;
        resetRender();
    }

    @Override
    protected void onDisable() {
        swap.release();
        clearPlan();
        finishing = false;
        lastPlaced = null;
        resetRender();
    }

    /** Crosshair mode with Always Break Last keeps the module on until the crystal under the crosshair is broken. */
    @Override
    public void setEnabled(boolean enabled) {
        if (!enabled && isEnabled() && !finishing && mode.getValue() == Mode.Crosshair && alwaysBreakLast.getValue()
                && mc.player != null && mc.level != null && crosshairCrystal() != null) {
            finishing = true;
            finishTicks = 0;
            return;
        }
        super.setEnabled(enabled);
    }

    private void clearPlan() {
        breakTarget = null;
        placeHit = null;
        aim = null;
        pendingAttack = null;
        pendingPlace = null;
        pendingHand = null;
        pendingEnemy = null;
        bobPitch = Float.NaN;
    }

    private void resetRender() {
        renderPos = null;
        renderBox = null;
        renderAlpha = 0;
        lastFrame = System.currentTimeMillis();
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        swap.tick();
        clearPlan();
        if (mode.getValue() == Mode.Crosshair) {
            crosshairPre(event);
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (mc.gui.screen() != null || mc.player.isUsingItem())
            return;
        Player enemy = nearestEnemy();
        if (enemy == null)
            return;
        if (breakCrystals.getValue())
            planBreak(enemy);
        if (aim == null && place.getValue() && crystalHand() != null)
            planPlace(enemy);
        if (aim == null)
            return;
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
        if (mode.getValue() == Mode.Crosshair) {
            crosshairPost(event);
            return;
        }
        if (aim == null)
            return;
        float[] want = RotationUtils.rotationsTo(mc.player.getEyePosition(1f), aim);
        if (Math.abs(RotationUtils.getYawDifference(event.getYaw(), want[0])) > AIM_TOLERANCE
                || Math.abs(event.getPitch() - want[1]) > AIM_TOLERANCE)
            return;
        if (breakTarget != null) {
            mc.gameMode.attack(mc.player, breakTarget);
            PlayerUtils.swingItem();
            markRender(breakTarget.blockPosition().below());
        } else if (placeHit != null) {
            placeCrystal();
            markRender(placeHit.getBlockPos());
        }
        cooldown = (int) delay.getValue().getInput();
        clearPlan();
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRender = event -> {
        long now = System.currentTimeMillis();
        float step = (now - lastFrame) * 0.005f;
        lastFrame = now;
        if (!render.getValue() || renderPos == null || step > 1f)
            return;
        boolean show = mode.getValue() == Mode.Crosshair
                ? heldCrystalHand() != null && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK
                : now - lastAction < 600;
        renderAlpha += ((show ? 1f : 0f) - renderAlpha) * Math.min(1f, step);
        if (renderAlpha <= 0.01f) {
            renderPos = null;
            renderBox = null;
            return;
        }
        if (renderBox == null)
            renderBox = new AABB(renderPos);
        // glide towards the newest block rather than jumping
        double glide = 1 - Math.pow(0.95, step / 0.08);
        renderBox = renderBox.move((renderPos.getX() - renderBox.minX) * glide, (renderPos.getY() - renderBox.minY) * glide,
                (renderPos.getZ() - renderBox.minZ) * glide);
        RenderUtils.renderBox(renderBox, RenderUtils.withAlpha(ThemeManager.getMainColor(), Math.max(1, (int) (renderAlpha * 255))), true, true);
    };

    private void markRender(BlockPos pos) {
        renderPos = pos;
        lastAction = System.currentTimeMillis();
    }

    // ---------------------------------------------------------------
    //  Crosshair mode
    // ---------------------------------------------------------------

    private void crosshairPre(EventSilentRotation event) {
        if (finishing) {
            Entity crystal = crosshairCrystal();
            if (crystal == null || ++finishTicks > FINISH_TICKS) {
                finishing = false;
                super.setEnabled(false);
                return;
            }
            if (canAct())
                pendingAttack = crystal;
            return;
        }
        if (!canAct() || (!mc.player.onGround() && !inAir.getValue()))
            return;
        if (!clickTimer.hasTimeElapsed((long) nextClickDelay) || mc.hitResult == null)
            return;
        InteractionHand hand = heldCrystalHand();
        if (hand == null) {
            if (autoSwitch.getValue()) {
                int slot = HotbarUse.find(s -> s.is(Items.END_CRYSTAL));
                if (slot != -1) {
                    mc.player.getInventory().setSelectedSlot(slot);
                    rollClickDelay();
                }
            }
            return;
        }

        Player enemy = nearestEnemy();
        if (damageTickAllows(enemy)) {
            if (mc.hitResult instanceof EntityHitResult entityHit) {
                Entity e = entityHit.getEntity();
                if (isBreakable(e)) {
                    if (pauseOnKill.getValue() && lootNearby(e.blockPosition()))
                        return;
                    pendingAttack = e;
                    pendingEnemy = enemy;
                    return;
                }
            } else if (headBob.getValue() && planHeadBob(event, enemy)) {
                return;
            }
        }

        if (mc.hitResult instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = blockHit.getBlockPos();
            if (!canPlaceOn(pos))
                return;
            pendingPlace = pos;
            pendingHand = hand;
        }
    }

    private void crosshairPost(EventSilentRotation.Post event) {
        if (pendingAttack != null) {
            if (!Float.isNaN(bobPitch) && Math.abs(event.getPitch() - bobPitch) > AIM_TOLERANCE) {
                clearPlan();
                return;
            }
            // the rotation that went out must actually be on the crystal
            if (pendingAttack.isAlive() && event.getRayTraceEntity() instanceof EntityHitResult hit && hit.getEntity() == pendingAttack) {
                mc.gameMode.attack(mc.player, pendingAttack);
                PlayerUtils.swingItem();
                if (pendingEnemy != null)
                    lastDamage = damage(pendingAttack.position(), pendingEnemy);
                lastPlaced = null;
                markRender(pendingAttack.blockPosition().below());
                rollClickDelay();
            }
        } else if (pendingPlace != null && pendingHand != null) {
            BlockHitResult hit = event.getRayTrace();
            if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pendingPlace)
                    && mc.player.getItemInHand(pendingHand).is(Items.END_CRYSTAL)) {
                mc.gameMode.useItemOn(mc.player, pendingHand, hit);
                PlayerUtils.swingItem();
                lastPlaced = pendingPlace;
                markRender(pendingPlace);
                rollClickDelay();
            }
        }
        clearPlan();
    }

    private boolean canAct() {
        return mc.gui.screen() == null && mc.isWindowActive() && !mc.player.isUsingItem();
    }

    private void rollClickDelay() {
        nextClickDelay = clickDelay.getValue().getRandomInRange();
        clickTimer.reset();
    }

    /**
     * Damage Tick: while the enemy is still in hurt time a new crystal only lands if it does more than the last one did,
     * so weaker crystals are held back.
     */
    private boolean damageTickAllows(Player enemy) {
        if (!damageTick.getValue() || enemy == null)
            return true;
        if (enemy.hurtTime == 0)
            lastDamage = 0;
        Vec3 crystal;
        if (mc.hitResult instanceof EntityHitResult entityHit)
            crystal = entityHit.getEntity().position();
        else if (mc.hitResult instanceof BlockHitResult blockHit)
            crystal = blockHit.getLocation().add(0, 1, 0);
        else
            return true;
        return damage(crystal, enemy) >= lastDamage;
    }

    /**
     * Head Bob: when a crystal sits above the block you look at (or the one you last placed on) but the crosshair is not
     * on it, keeps your yaw and turns only the pitch sent to the server so the hit lands.
     */
    private boolean planHeadBob(EventSilentRotation event, Player enemy) {
        BlockPos base = lastPlaced;
        if (base == null && mc.hitResult instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK)
            base = blockHit.getBlockPos();
        if (base == null)
            return false;
        Vec3 slot = Vec3.atCenterOf(base.above());
        Vec3 eyes = mc.player.getEyePosition(1f);
        float yaw = event.getYaw();
        for (Entity e : mc.level.getEntities((Entity) null, new AABB(base.above()).inflate(1), x -> x instanceof EndCrystal)) {
            if (!e.isAlive() || e.position().distanceTo(slot) >= 1)
                continue;
            AABB box = e.getBoundingBox();
            if (distanceToBox(eyes, box) > HEAD_BOB_REACH)
                continue;
            if (pauseOnKill.getValue() && lootNearby(e.blockPosition()))
                continue;
            Vec3 nearest = nearestPoint(box, eyes);
            Vec3 point = nearest.add(box.getCenter().subtract(nearest).scale(0.3));
            float pitch = Mth.clamp(RotationUtils.rotationsTo(eyes, point)[1], -89.9f, 89.9f);
            Vec3 look = Entity.calculateViewVector(pitch, yaw);
            Optional<Vec3> hit = box.inflate(0.1).clip(eyes, eyes.add(look.scale(3)));
            if (hit.isEmpty() || eyes.distanceTo(hit.get()) > HEAD_BOB_HIT)
                continue;
            event.setYaw(yaw);
            event.setPitch(pitch);
            event.setSpeed(180f);
            event.setMovementFix(SilentRotationManager.MovementFix.SILENT);
            bobPitch = pitch;
            pendingAttack = e;
            pendingEnemy = enemy;
            return true;
        }
        return false;
    }

    private static double distanceToBox(Vec3 p, AABB box) {
        double dx = Math.max(box.minX - p.x, Math.max(0, p.x - box.maxX));
        double dy = Math.max(box.minY - p.y, Math.max(0, p.y - box.maxY));
        double dz = Math.max(box.minZ - p.z, Math.max(0, p.z - box.maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** The point of the box closest to p, or its centre when p is inside it. */
    private static Vec3 nearestPoint(AABB box, Vec3 p) {
        Vec3 clamped = new Vec3(Mth.clamp(p.x, box.minX, box.maxX), Mth.clamp(p.y, box.minY, box.maxY), Mth.clamp(p.z, box.minZ, box.maxZ));
        return clamped.equals(p) ? box.getCenter() : clamped;
    }

    /** End crystals, plus slimes and magma cubes, which servers often use as crystal stand-ins for practice. */
    private static boolean isBreakable(Entity e) {
        return e != null && e.isAlive() && (e instanceof EndCrystal || e instanceof Slime);
    }

    private static Entity crosshairCrystal() {
        if (mc.hitResult instanceof EntityHitResult hit && isBreakable(hit.getEntity()))
            return hit.getEntity();
        return null;
    }

    /** Obsidian or bedrock with air above it and nothing but dropped items in the way. */
    private static boolean canPlaceOn(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        if (!state.is(Blocks.OBSIDIAN) && !state.is(Blocks.BEDROCK))
            return false;
        if (!mc.level.getBlockState(pos.above()).isAir())
            return false;
        return mc.level.getEntities((Entity) null, new AABB(pos.above()), x -> !(x instanceof ItemEntity) && !x.isSpectator()).isEmpty();
    }

    /** Pause On Kill: more than three valuable drops near the crystal mean someone just died there. */
    private static boolean lootNearby(BlockPos pos) {
        Vec3 centre = Vec3.atCenterOf(pos);
        int count = 0;
        for (Entity e : mc.level.getEntities((Entity) null, new AABB(pos).inflate(15), x -> x instanceof ItemEntity)) {
            ItemEntity item = (ItemEntity) e;
            if (item.getY() < pos.getY() || item.position().distanceTo(centre) > 15 || !isLoot(item.getItem()))
                continue;
            if (++count > 3)
                return true;
        }
        return false;
    }

    private static Item[] loot;

    private static boolean isLoot(ItemStack stack) {
        if (loot == null)
            loot = new Item[]{
                    Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS,
                    Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS,
                    Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD,
                    Items.DIAMOND_AXE, Items.NETHERITE_AXE, Items.TOTEM_OF_UNDYING, Items.GOLDEN_APPLE,
                    Items.ENCHANTED_GOLDEN_APPLE, Items.ENDER_PEARL, Items.EXPERIENCE_BOTTLE, Items.END_CRYSTAL,
                    Items.RESPAWN_ANCHOR, Items.GLOWSTONE};
        for (Item item : loot)
            if (stack.is(item))
                return true;
        return false;
    }

    /** The hand already holding a crystal (main hand first), or null. */
    private static InteractionHand heldCrystalHand() {
        if (mc.player == null)
            return null;
        if (mc.player.getMainHandItem().is(Items.END_CRYSTAL))
            return InteractionHand.MAIN_HAND;
        if (mc.player.getOffhandItem().is(Items.END_CRYSTAL))
            return InteractionHand.OFF_HAND;
        return null;
    }

    // ---------------------------------------------------------------
    //  Auto mode
    // ---------------------------------------------------------------

    private void placeCrystal() {
        InteractionHand hand = crystalHand();
        if (hand == null)
            return;
        if (hand == InteractionHand.MAIN_HAND) {
            if (!mc.player.getMainHandItem().is(Items.END_CRYSTAL))
                swap.switchOnce(HotbarUse.find(s -> s.is(Items.END_CRYSTAL)));
        }
        mc.gameMode.useItemOn(mc.player, hand, placeHit);
        PlayerUtils.swingItem();
    }

    /** The hand a crystal can be placed from (offhand first, so the hotbar stays put), or null. */
    private static InteractionHand crystalHand() {
        if (mc.player.getOffhandItem().is(Items.END_CRYSTAL))
            return InteractionHand.OFF_HAND;
        return HotbarUse.find(s -> s.is(Items.END_CRYSTAL)) != -1 ? InteractionHand.MAIN_HAND : null;
    }

    private void planBreak(Player enemy) {
        double reach = range.getValue().getInput();
        Vec3 eyes = mc.player.getEyePosition(1f);
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Entity e : mc.level.getEntities((Entity) null, mc.player.getBoundingBox().inflate(reach + 1), x -> x instanceof EndCrystal)) {
            Vec3 centre = e.position().add(0, 0.5, 0);
            if (eyes.distanceTo(centre) > reach)
                continue;
            double score = score(e.position(), enemy);
            if (score > bestScore) {
                bestScore = score;
                breakTarget = (EndCrystal) e;
                aim = centre;
            }
        }
    }

    private void planPlace(Player enemy) {
        double reach = range.getValue().getInput();
        Vec3 eyes = mc.player.getEyePosition(1f);
        int r = (int) Math.ceil(reach);
        BlockPos origin = BlockPos.containing(eyes);
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    var state = mc.level.getBlockState(pos);
                    if (!state.is(Blocks.OBSIDIAN) && !state.is(Blocks.BEDROCK))
                        continue;
                    Vec3 top = new Vec3(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
                    if (eyes.y <= top.y || eyes.distanceTo(top) > reach
                            || !mc.level.getBlockState(pos.above()).isAir() || !mc.level.getBlockState(pos.above(2)).isAir()
                            || !mc.level.getEntities((Entity) null, new AABB(pos.above()), x -> !x.isSpectator()).isEmpty())
                        continue;
                    BlockHitResult hit = mc.level.clip(new ClipContext(eyes, top, ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE, mc.player));
                    if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(pos) || hit.getDirection() != Direction.UP)
                        continue;
                    double score = score(top, enemy);
                    if (score > bestScore) {
                        bestScore = score;
                        placeHit = hit;
                        aim = hit.getLocation();
                    }
                }
            }
        }
    }

    /** Damage to the enemy less damage to us, or negative infinity when the crystal is not worth it. */
    private double score(Vec3 crystal, Player enemy) {
        double self = damage(crystal, mc.player);
        double target = damage(crystal, enemy);
        if (target < minDamage.getValue().getInput() || self > maxSelfDamage.getValue().getInput()
                || self >= mc.player.getHealth() + mc.player.getAbsorptionAmount() - 1)
            return Double.NEGATIVE_INFINITY;
        return target - self;
    }

    private Player nearestEnemy() {
        Player best = null;
        for (Player p : mc.level.players()) {
            if (!TargetManager.isValidTarget(p) || mc.player.distanceTo(p) > ENEMY_SEARCH)
                continue;
            if (best == null || mc.player.distanceTo(p) < mc.player.distanceTo(best))
                best = p;
        }
        return best;
    }

    /** Vanilla explosion damage (power 6) from a crystal at this point, with armour and difficulty roughly applied. */
    private static double damage(Vec3 crystal, LivingEntity e) {
        double d = Math.sqrt(e.distanceToSqr(crystal)) / 12.0;
        if (d > 1)
            return 0;
        double impact = (1 - d) * exposure(crystal, e);
        double dmg = (impact * impact + impact) / 2 * 7 * 12 + 1;
        Difficulty difficulty = mc.level.getDifficulty();
        if (difficulty == Difficulty.PEACEFUL)
            return 0;
        if (difficulty == Difficulty.EASY)
            dmg = Math.min(dmg / 2 + 1, dmg);
        else if (difficulty == Difficulty.HARD)
            dmg *= 1.5;
        return dmg * (1 - Math.min(20, e.getArmorValue()) / 25.0);
    }

    /** The share of the entity's box corners and centre the crystal can see. */
    private static double exposure(Vec3 from, LivingEntity e) {
        AABB box = e.getBoundingBox();
        double[] xs = {box.minX, box.maxX}, ys = {box.minY, box.maxY}, zs = {box.minZ, box.maxZ};
        int seen = 0, total = 0;
        for (double x : xs) {
            for (double y : ys) {
                for (double z : zs) {
                    total++;
                    if (clear(from, new Vec3(x, y, z), e))
                        seen++;
                }
            }
        }
        total++;
        if (clear(from, box.getCenter(), e))
            seen++;
        return seen / (double) total;
    }

    private static boolean clear(Vec3 a, Vec3 b, LivingEntity e) {
        return mc.level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e))
                .getType() == HitResult.Type.MISS;
    }
}
