package arsenic.module.impl.blatant;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPlayerJoinWorld;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventSilentRotation;
import arsenic.gui.themes.ThemeManager;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.impl.client.AntiBot;
import arsenic.module.impl.client.TargetManager;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.notifications.Notification;
import arsenic.notifications.NotificationManager;
import arsenic.notifications.NotificationType;
import arsenic.utils.minecraft.HotbarUse;
import arsenic.utils.minecraft.ItemUtils;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.render.TargetFx;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.rotations.SilentRotationManager;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Full respawn anchor PvP: picks the spot next to the nearest player that hurts them most and you least, places an
 * anchor there (or uses one already standing), puts a cover block between you and it when that saves enough damage,
 * charges it with glowstone and blows it up, with a random delay between each step. Aims silently and only clicks
 * once the rotation the server gets is on the block.
 */
@ModuleInfo(name = "AutoAnchor", description = "Places, covers, charges and detonates respawn anchors on the nearest player", category = ModuleCategory.COMBAT, tier = ModuleTier.BLATANT)
public class AutoAnchor extends Module {

    public enum SafeBlock { Glowstone, Obsidian }

    private enum State { IDLE, PLACE_ANCHOR, SAFE_COVER, CHARGE_ANCHOR, DETONATE }

    private static final double SCAN_REACH_SQ = 4.6 * 4.6;
    private static final double AIM_REACH = 4.4;
    private static final double SUPPORT_REACH_SQ = 4.25 * 4.25;
    private static final double KEEP_TARGET_RANGE = 12;
    private static final float MAX_YAW_OFF = 60f;
    private static final float AIM_TOLERANCE = 6f;
    private static final long STEP_TIMEOUT_MS = 200;
    private static final int MAX_FAILS = 3;
    private static final int MAX_HURT_WAIT = 12;
    private static final int BLACKLIST_TICKS = 40;
    private static final int MAX_FULL_CHECKS = 32;
    private static final float ANCHOR_POWER = 5f;
    private static final EquipmentSlot[] ARMOUR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final int[][] COVER_OFFSETS = {{-1, 0, 0}, {1, 0, 0}, {0, 0, -1}, {0, 0, 1},
            {-1, 0, -1}, {-1, 0, 1}, {1, 0, -1}, {1, 0, 1}, {0, 1, 0}};

    /** True while a sequence is running, so other modules can stay out of the way. */
    public static boolean active;

    public final DoubleProperty targetRange = new DoubleProperty("Target Range", new DoubleValue(1, 16, 10, 0.5));
    public final DoubleProperty minDamage = new DoubleProperty("Min Damage", new DoubleValue(0, 36, 6, 0.5));
    public final DoubleProperty maxSelfDamage = new DoubleProperty("Max Self Damage", new DoubleValue(0, 36, 10, 0.5));
    public final BooleanProperty antiSuicide = new BooleanProperty("Anti Suicide", true);
    public final BooleanProperty safeAnchor = new BooleanProperty("Safe Anchor", true);
    public final EnumProperty<SafeBlock> safeBlock = new EnumProperty<>("Safe Block", SafeBlock.Glowstone);
    public final BooleanProperty rotateSilently = new BooleanProperty("Rotate Silently", true);
    public final BooleanProperty swapBack = new BooleanProperty("Swap Back", true);
    public final RangeProperty placeDelay = new RangeProperty("Place Delay (ms)", new RangeValue(0, 500, 0, 40, 1));
    public final RangeProperty chargeDelay = new RangeProperty("Charge Delay (ms)", new RangeValue(0, 500, 20, 60, 1));
    public final RangeProperty detonateDelay = new RangeProperty("Detonate Delay (ms)", new RangeValue(0, 500, 20, 60, 1));
    public final BooleanProperty onlyExisting = new BooleanProperty("Only Existing", false);
    public final BooleanProperty render = new BooleanProperty("Render", true);

    private State state = State.IDLE;
    private Player target;
    private BlockPos targetOrigin;
    private BlockPos anchorPos, supportPos, coverPos, coverSupport;
    private Vec3 supportAim, coverAim;
    private Vec3 aim;

    private long stateSince, stateDelay, lastProgress, lastWarn;
    private int fails, hurtWait, ticks;
    private int originalSlot = -1;
    private final Map<BlockPos, Integer> blacklist = new HashMap<>();
    private final List<Candidate> candidates = new ArrayList<>();

    // render fades
    private float targetFade, anchorFade, coverFade;
    private Player shownTarget;
    private BlockPos shownAnchor, shownCover;
    private long lastFrame;
    private final TargetFx.Target fx = new TargetFx.Target();

    public AutoAnchor() {
        safeBlock.setVisible(safeAnchor::getValue);
    }

    @Override
    public boolean isSwappingHotbar() {
        return originalSlot != -1;
    }

    @Override
    protected void onEnable() {
        reset();
        blacklist.clear();
    }

    @Override
    protected void onDisable() {
        restoreSlot();
        reset();
        blacklist.clear();
        targetFade = anchorFade = coverFade = 0;
        shownTarget = null;
        shownAnchor = shownCover = null;
    }

    @EventLink
    public final Listener<EventPlayerJoinWorld> onJoinWorld = event -> {
        originalSlot = -1;
        reset();
        blacklist.clear();
    };

    /** Runs before the movement packet: pick or advance the sequence and aim at the next block to click. */
    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        aim = null;
        ticks++;
        if (!blacklist.isEmpty())
            blacklist.values().removeIf(t -> ticks - t > BLACKLIST_TICKS);

        if (mc.gui.screen() != null || !mc.isWindowActive() || mc.player.isUsingItem() || mc.gameMode == null) {
            active = false;
            return;
        }

        if (state != State.IDLE && (!targetStillValid() || targetMoved()))
            abort();
        if (state != State.IDLE && System.currentTimeMillis() - lastProgress > STEP_TIMEOUT_MS + stateDelay)
            abort();

        if (state == State.IDLE) {
            restoreSlot();
            search();
        }

        if (state != State.IDLE && System.currentTimeMillis() - stateSince >= stateDelay) {
            Vec3 point = aimPoint();
            Vec3 eyes = mc.player.getEyePosition(1f);
            if (point == null) {
                fail();
            } else if (eyes.distanceTo(point) > AIM_REACH) {
                abort();
            } else {
                float[] rots = RotationUtils.rotationsTo(eyes, point);
                if (rotateSilently.getValue() && Math.abs(RotationUtils.getYawDifference(rots[0], mc.player.getYRot())) >= MAX_YAW_OFF) {
                    abort();
                } else {
                    aim = point;
                    event.setYaw(rots[0]);
                    event.setPitch(rots[1]);
                    event.setSpeed(180f);
                    event.setPreventDuplicateLook(true);
                    event.setMovementFix(SilentRotationManager.MovementFix.SILENT);
                }
            }
        }
        active = state != State.IDLE;
    };

    /** The rotation has settled: click if what the server sees is the block this step needs. */
    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onPost = event -> {
        if (aim == null || state == State.IDLE)
            return;
        float[] want = RotationUtils.rotationsTo(mc.player.getEyePosition(1f), aim);
        aim = null;
        if (Math.abs(RotationUtils.getYawDifference(event.getYaw(), want[0])) > AIM_TOLERANCE
                || Math.abs(event.getPitch() - want[1]) > AIM_TOLERANCE)
            return;
        if (!rotateSilently.getValue()) {
            mc.player.setYRot(event.getYaw());
            mc.player.setXRot(event.getPitch());
        }
        BlockHitResult hit = event.getRayTrace();
        switch (state) {
            case PLACE_ANCHOR -> doPlace(hit);
            case SAFE_COVER -> doCover(hit);
            case CHARGE_ANCHOR -> doCharge(hit);
            case DETONATE -> doDetonate(hit);
            default -> {
            }
        }
        active = state != State.IDLE;
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRender = event -> {
        long now = System.currentTimeMillis();
        float step = lastFrame == 0 ? 0 : Math.min(1f, (now - lastFrame) / 180f);
        lastFrame = now;
        if (!render.getValue())
            return;

        if (target != null)
            shownTarget = target;
        if (anchorPos != null)
            shownAnchor = anchorPos;
        boolean covering = coverPos != null && state == State.SAFE_COVER;
        if (covering)
            shownCover = coverPos;
        targetFade = approach(targetFade, target != null ? 1 : 0, step);
        anchorFade = approach(anchorFade, anchorPos != null ? 1 : 0, step);
        coverFade = approach(coverFade, covering ? 1 : 0, step);

        float pulse = 0.5f + 0.5f * (float) Math.sin(now / 900.0 * Math.PI * 2);
        if (targetFade > 0.01f && shownTarget != null && shownTarget.isAlive()) {
            Vec3 feet = RenderUtils.interpolatedPosition(shownTarget);
            fx.x = feet.x;
            fx.y = feet.y;
            fx.z = feet.z;
            fx.height = shownTarget.getBbHeight();
            fx.radius = shownTarget.getBbWidth() * 0.5 + 0.2;
            fx.color = ThemeManager.getMainColor() & 0xFFFFFF;
            fx.alpha = targetFade * (0.6f + 0.4f * pulse);
            TargetFx.render(TargetFx.Style.CLASSIC, fx, now / 1000.0);
        }
        if (anchorFade > 0.01f && shownAnchor != null)
            drawBlock(shownAnchor, ThemeManager.getGradientColor(), anchorFade, pulse);
        if (coverFade > 0.01f && shownCover != null)
            drawBlock(shownCover, ThemeManager.getDarkerColor(), coverFade, pulse);
    };

    // ---- steps ----

    private void doPlace(BlockHitResult hit) {
        if (isAnchor(anchorPos)) {
            afterPlace();
            return;
        }
        if (!mc.level.getBlockState(anchorPos).canBeReplaced()) {
            abort();
            return;
        }
        int slot = HotbarUse.find(s -> s.is(Items.RESPAWN_ANCHOR));
        if (slot == -1) {
            missing("No respawn anchor in hotbar");
            return;
        }
        if (!clickedFace(hit, supportPos, anchorPos)) {
            fail();
            return;
        }
        click(slot, hit);
        afterPlace();
    }

    private void afterPlace() {
        enter(safeAnchor.getValue() && coverPos != null ? State.SAFE_COVER : State.CHARGE_ANCHOR);
    }

    private void doCover(BlockHitResult hit) {
        if (coverPos == null || coverSupport == null || !mc.level.getBlockState(coverPos).canBeReplaced()) {
            enter(State.CHARGE_ANCHOR);
            return;
        }
        Item block = coverItem();
        int slot = HotbarUse.find(s -> s.is(block));
        if (slot == -1) {
            enter(State.CHARGE_ANCHOR);
            return;
        }
        if (!clickedFace(hit, coverSupport, coverPos)) {
            fail();
            return;
        }
        click(slot, hit);
        enter(State.CHARGE_ANCHOR);
    }

    private void doCharge(BlockHitResult hit) {
        if (!isAnchor(anchorPos)) {
            fail();
            return;
        }
        if (charges(anchorPos) > 0) {
            enter(State.DETONATE);
            return;
        }
        int slot = HotbarUse.find(s -> s.is(Items.GLOWSTONE));
        if (slot == -1) {
            missing("No glowstone in hotbar");
            return;
        }
        if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(anchorPos)) {
            fail();
            return;
        }
        click(slot, hit);
        enter(State.DETONATE);
    }

    private void doDetonate(BlockHitResult hit) {
        if (!isAnchor(anchorPos)) {
            fail();
            return;
        }
        if (charges(anchorPos) <= 0) {
            enter(State.CHARGE_ANCHOR);
            return;
        }
        // a blast during their hurt time does nothing, so hold it for a few ticks
        if (target != null && target.hurtTime > 2 && ++hurtWait <= MAX_HURT_WAIT) {
            lastProgress = System.currentTimeMillis();
            return;
        }
        hurtWait = 0;
        double self = explosionDamage(mc.player, Vec3.ZERO, Vec3.atCenterOf(anchorPos), anchorPos, null);
        float health = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        if (self > maxSelfDamage.getValue().getInput()) {
            blacklist.put(anchorPos.immutable(), ticks);
            abort();
            return;
        }
        if (antiSuicide.getValue() && self >= health - 1) {
            abort();
            return;
        }
        int slot = mc.player.getInventory().getSelectedSlot();
        if (mc.player.getInventory().getItem(slot).is(Items.GLOWSTONE)) {
            // glowstone in hand would add a charge instead, so blow it up with something else
            slot = originalSlot != -1 && !mc.player.getInventory().getItem(originalSlot).is(Items.GLOWSTONE)
                    ? originalSlot : nonGlowstoneSlot();
            if (slot == -1) {
                missing("No non-glowstone slot to detonate with");
                return;
            }
        }
        if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(anchorPos)) {
            fail();
            return;
        }
        click(slot, hit);
        abort();
    }

    /** Switches to the slot if needed and right-clicks through the hit, swing after the click. */
    private void click(int slot, BlockHitResult hit) {
        int current = mc.player.getInventory().getSelectedSlot();
        if (slot != current) {
            if (originalSlot == -1)
                originalSlot = current;
            mc.player.getInventory().setSelectedSlot(slot);
        }
        if (mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit).consumesAction())
            PlayerUtils.swingItem();
        fails = 0;
        lastProgress = System.currentTimeMillis();
    }

    private static boolean clickedFace(BlockHitResult hit, BlockPos support, BlockPos place) {
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(support)
                && hit.getBlockPos().relative(hit.getDirection()).equals(place);
    }

    private Vec3 aimPoint() {
        return switch (state) {
            case PLACE_ANCHOR -> supportAim;
            case SAFE_COVER -> coverAim;
            case CHARGE_ANCHOR, DETONATE -> visiblePoint(mc.player.getEyePosition(1f), anchorPos);
            default -> null;
        };
    }

    // ---- state ----

    private void enter(State next) {
        state = next;
        fails = 0;
        hurtWait = 0;
        long now = System.currentTimeMillis();
        stateSince = now;
        lastProgress = now;
        RangeProperty delay = switch (next) {
            case SAFE_COVER -> placeDelay;
            case CHARGE_ANCHOR -> chargeDelay;
            case DETONATE -> detonateDelay;
            default -> null;
        };
        stateDelay = delay == null ? 0 : (long) delay.getValue().getRandomInRange();
    }

    private void fail() {
        if (++fails >= MAX_FAILS)
            abort();
    }

    private void missing(String what) {
        long now = System.currentTimeMillis();
        if (now - lastWarn > 3000) {
            NotificationManager.show(new Notification(NotificationType.ERROR, getName(), what, 2));
            lastWarn = now;
        }
        abort();
    }

    /** Ends the sequence. The slot goes back on the next idle tick, not this one, so the click keeps its item. */
    private void abort() {
        reset();
    }

    private void reset() {
        state = State.IDLE;
        target = null;
        targetOrigin = null;
        anchorPos = supportPos = coverPos = coverSupport = null;
        supportAim = coverAim = aim = null;
        fails = 0;
        hurtWait = 0;
        stateDelay = 0;
        active = false;
    }

    private void restoreSlot() {
        if (originalSlot != -1 && mc.player != null && swapBack.getValue())
            mc.player.getInventory().setSelectedSlot(originalSlot);
        originalSlot = -1;
    }

    private boolean targetStillValid() {
        return target != null && target.isAlive() && !target.isRemoved()
                && mc.player.distanceTo(target) <= KEEP_TARGET_RANGE
                && !Arsenic.getArsenic().getFriendManager().isFriend(target) && !AntiBot.isBot(target);
    }

    private boolean targetMoved() {
        if (targetOrigin == null || target == null)
            return false;
        BlockPos now = target.blockPosition();
        return Math.max(Math.abs(now.getX() - targetOrigin.getX()),
                Math.max(Math.abs(now.getY() - targetOrigin.getY()), Math.abs(now.getZ() - targetOrigin.getZ()))) > 1;
    }

    // ---- search ----

    private record Support(BlockPos block, Vec3 face) {
    }

    private record Candidate(BlockPos pos, Support support, boolean existing, int charges, double distance,
                             boolean sameY, boolean adjacent, double heuristic, int index) {
    }

    private void search() {
        Player enemy = nearestEnemy();
        if (enemy == null || nonGlowstoneSlot() == -1)
            return;

        Vec3 eyes = mc.player.getEyePosition(1f);
        Vec3 feet = enemy.position();
        Vec3 shift = predictionShift(enemy);
        Vec3 predicted = feet.add(shift);
        float health = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        boolean hasAnchor = HotbarUse.find(s -> s.is(Items.RESPAWN_ANCHOR)) != -1;
        boolean hasGlow = HotbarUse.find(s -> s.is(Items.GLOWSTONE)) != -1;
        Item coverItem = coverItem();
        boolean hasCover = HotbarUse.find(s -> s.is(coverItem)) != -1;
        double min = minDamage.getValue().getInput();
        boolean silent = rotateSilently.getValue();

        candidates.clear();
        BlockPos origin = enemy.blockPosition();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int index = 0;
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -5; dz <= 5; dz++, index++) {
                    cursor.setWithOffset(origin, dx, dy, dz);
                    Vec3 centre = Vec3.atCenterOf(cursor);
                    double distSq = eyes.distanceToSqr(centre);
                    if (distSq > SCAN_REACH_SQ || blacklist.containsKey(cursor))
                        continue;
                    boolean existing = isAnchor(cursor);
                    Support support = null;
                    if (!existing) {
                        if (!hasAnchor || !hasGlow || onlyExisting.getValue() || !placeable(cursor))
                            continue;
                        support = findSupport(cursor, eyes);
                        if (support == null)
                            continue;
                    }
                    int charge = existing ? charges(cursor) : 0;
                    if (existing && charge == 0 && !hasGlow)
                        continue;
                    double raw = Math.max(rawDamage(feet, centre), rawDamage(predicted, centre));
                    if (raw < min)
                        continue;
                    double dist = Math.sqrt(distSq);
                    boolean sameY = cursor.getY() == origin.getY();
                    boolean adjacent = sameY && Math.max(Math.abs(dx), Math.abs(dz)) == 1;
                    candidates.add(new Candidate(cursor.immutable(), support, existing, charge, dist, sameY, adjacent,
                            score(raw, 0, dist, sameY, adjacent, existing, charge), index));
                }
            }
        }
        candidates.sort((a, b) -> {
            int c = Double.compare(b.heuristic, a.heuristic);
            return c != 0 ? c : Integer.compare(a.index, b.index);
        });

        Candidate best = null;
        BlockPos bestCover = null;
        Support bestCoverSupport = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        int checked = 0;
        for (Candidate c : candidates) {
            // the heuristic assumes no self damage, so nothing further down can beat the best any more
            if (best != null && c.heuristic <= bestScore)
                break;
            if (++checked > MAX_FULL_CHECKS)
                break;
            Vec3 centre = Vec3.atCenterOf(c.pos);
            Vec3 point = c.existing ? visiblePoint(eyes, c.pos) : c.support.face;
            if (point == null || eyes.distanceTo(point) > AIM_REACH)
                continue;
            if (silent && Math.abs(RotationUtils.getYawDifference(RotationUtils.rotationsTo(eyes, point)[0], mc.player.getYRot())) >= MAX_YAW_OFF)
                continue;

            double dealt = explosionDamage(enemy, Vec3.ZERO, centre, c.pos, null);
            if (shift.lengthSqr() > 1.0E-6)
                dealt = Math.max(dealt, explosionDamage(enemy, shift, centre, c.pos, null));
            if (dealt < min)
                continue;
            if (best != null && score(dealt, 0, c.distance, c.sameY, c.adjacent, c.existing, c.charges) <= bestScore)
                continue;

            double self = explosionDamage(mc.player, Vec3.ZERO, centre, c.pos, null);
            if (antiSuicide.getValue() && self >= health * 2)
                continue;

            BlockPos cover = null;
            Support coverSup = null;
            double finalSelf = self;
            if (safeAnchor.getValue() && hasCover && self > 6) {
                double lowest = self;
                for (int[] o : COVER_OFFSETS) {
                    BlockPos cp = c.pos.offset(o[0], o[1], o[2]);
                    if (!placeable(cp))
                        continue;
                    Support sup = findSupport(cp, eyes);
                    if (sup == null)
                        continue;
                    double covered = explosionDamage(mc.player, Vec3.ZERO, centre, c.pos, cp);
                    if (covered < lowest && self - covered >= 2) {
                        lowest = covered;
                        cover = cp;
                        coverSup = sup;
                    }
                }
                if (cover != null)
                    finalSelf = lowest;
            }
            if (finalSelf > maxSelfDamage.getValue().getInput() || (antiSuicide.getValue() && finalSelf >= health - 1))
                continue;
            double total = score(dealt, finalSelf, c.distance, c.sameY, c.adjacent, c.existing, c.charges);
            if (total > bestScore) {
                bestScore = total;
                best = c;
                bestCover = cover;
                bestCoverSupport = coverSup;
            }
        }
        candidates.clear();
        if (best == null)
            return;

        target = enemy;
        targetOrigin = enemy.blockPosition();
        anchorPos = best.pos;
        if (best.support != null) {
            supportPos = best.support.block;
            supportAim = best.support.face;
        }
        coverPos = bestCover;
        if (bestCoverSupport != null) {
            coverSupport = bestCoverSupport.block;
            coverAim = bestCoverSupport.face;
        }
        if (!best.existing)
            enter(State.PLACE_ANCHOR);
        else if (safeAnchor.getValue() && coverPos != null)
            enter(State.SAFE_COVER);
        else
            enter(best.charges > 0 ? State.DETONATE : State.CHARGE_ANCHOR);
    }

    /** Damage first, then closer, same level, right beside them, already standing and already charged. */
    private static double score(double dealt, double self, double distance, boolean sameY, boolean adjacent,
                                boolean existing, int charges) {
        double s = dealt - 0.6 * self - 0.15 * distance - 0.15 * distance * distance;
        if (sameY)
            s += 4;
        if (adjacent)
            s += 3.5;
        if (existing) {
            double near = Math.max(0, 1 - distance / AIM_REACH);
            s += 5 * near;
            if (charges > 0)
                s += 6 * near;
        }
        return s;
    }

    private Player nearestEnemy() {
        double range = targetRange.getValue().getInput();
        Player best = null;
        double bestDist = range;
        for (Player p : mc.level.players()) {
            if (!p.isAlive() || !TargetManager.isValidTarget(p))
                continue;
            double d = mc.player.distanceTo(p);
            if (d <= bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    /** Where they will be in a few ticks plus their ping, horizontally, capped at three blocks. */
    private static Vec3 predictionShift(Player p) {
        double vx = p.getX() - p.xo, vz = p.getZ() - p.zo;
        if (vx * vx + vz * vz < 1.0E-6)
            return Vec3.ZERO;
        int ping = 0;
        if (mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(p.getUUID());
            if (info != null)
                ping = Math.max(0, info.getLatency());
        }
        float ticksAhead = Mth.clamp(3f + ping / 50f, 0f, 20f);
        Vec3 shift = new Vec3(vx * ticksAhead, 0, vz * ticksAhead);
        return shift.lengthSqr() > 9 ? shift.normalize().scale(3) : shift;
    }

    /** The closest solid neighbour whose facing side is in reach, to click when placing here. */
    private static Support findSupport(BlockPos pos, Vec3 eyes) {
        Support best = null;
        double bestSq = Double.MAX_VALUE;
        for (Direction dir : Direction.values()) {
            BlockPos n = pos.relative(dir);
            BlockState state = mc.level.getBlockState(n);
            // clicking an anchor would charge or detonate it rather than place against it
            if (state.canBeReplaced() || state.is(Blocks.RESPAWN_ANCHOR))
                continue;
            Direction back = dir.getOpposite();
            Vec3 face = Vec3.atCenterOf(n).add(back.getStepX() * 0.5, back.getStepY() * 0.5, back.getStepZ() * 0.5);
            double sq = eyes.distanceToSqr(face);
            if (sq <= SUPPORT_REACH_SQ && sq < bestSq) {
                bestSq = sq;
                best = new Support(n.immutable(), face);
            }
        }
        return best;
    }

    private static boolean placeable(BlockPos pos) {
        return mc.level.getBlockState(pos).canBeReplaced()
                && mc.level.isUnobstructed(Blocks.OBSIDIAN.defaultBlockState(), pos, CollisionContext.empty());
    }

    /** A point on the block the eyes can see: the centre, else the centre of an exposed face. */
    private static Vec3 visiblePoint(Vec3 eyes, BlockPos pos) {
        Vec3 centre = Vec3.atCenterOf(pos);
        if (sees(eyes, centre, pos))
            return centre;
        Vec3 best = null;
        double bestSq = Double.MAX_VALUE;
        for (Direction dir : Direction.values()) {
            Vec3 face = centre.add(dir.getStepX() * 0.49, dir.getStepY() * 0.49, dir.getStepZ() * 0.49);
            double sq = eyes.distanceToSqr(face);
            if (sq < bestSq && sees(eyes, face, pos)) {
                bestSq = sq;
                best = face;
            }
        }
        return best;
    }

    private static boolean sees(Vec3 eyes, Vec3 point, BlockPos pos) {
        BlockHitResult hit = mc.level.clip(new ClipContext(eyes, point, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
    }

    private Item coverItem() {
        return safeBlock.getValue() == SafeBlock.Obsidian ? Items.OBSIDIAN : Items.GLOWSTONE;
    }

    private static int nonGlowstoneSlot() {
        for (int i = 0; i < 9; i++)
            if (!mc.player.getInventory().getItem(i).is(Items.GLOWSTONE))
                return i;
        return -1;
    }

    private static boolean isAnchor(BlockPos pos) {
        return pos != null && mc.level.getBlockState(pos).is(Blocks.RESPAWN_ANCHOR);
    }

    private static int charges(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        return state.is(Blocks.RESPAWN_ANCHOR) ? state.getValue(RespawnAnchorBlock.CHARGE) : 0;
    }

    // ---- damage ----

    /** Upper bound of the blast against someone standing at feet: full exposure, hard difficulty, no armour. */
    private static double rawDamage(Vec3 feet, Vec3 boom) {
        double d = feet.distanceTo(boom) / (ANCHOR_POWER * 2);
        if (d > 1)
            return 0;
        double impact = 1 - d;
        return ((impact * impact + impact) / 2 * 7 * ANCHOR_POWER * 2 + 1) * 1.5;
    }

    /**
     * Vanilla anchor blast damage (power 5) to the entity moved by shift, with the anchor itself gone (it is removed
     * before it explodes) and optionally a cover block that is not there yet treated as solid. Armour, toughness,
     * resistance and protection enchantments are applied.
     */
    private static double explosionDamage(LivingEntity e, Vec3 shift, Vec3 boom, BlockPos ignore, BlockPos cover) {
        Vec3 feet = e.position().add(shift);
        double d = feet.distanceTo(boom) / (ANCHOR_POWER * 2);
        if (d > 1)
            return 0;
        double impact = (1 - d) * exposure(boom, e.getBoundingBox().move(shift), ignore, cover);
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

    // ---- render ----

    private static float approach(float value, float goal, float step) {
        return value < goal ? Math.min(goal, value + step) : Math.max(goal, value - step);
    }

    /** A theme-coloured box that grows in and shrinks out with the fade and breathes while it is shown. */
    private static void drawBlock(BlockPos pos, int colour, float fade, float pulse) {
        int alpha = Math.round(255 * fade * (0.6f + 0.4f * pulse));
        AABB box = new AABB(pos).inflate(-(1 - fade) * 0.45);
        RenderUtils.renderBox(box, RenderUtils.withAlpha(colour, Math.max(1, alpha)), true, true);
    }
}
