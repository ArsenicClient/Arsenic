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
import arsenic.utils.timer.MSTimer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Full crystal sequence on the nearest player: picks the best spot around them by predicted damage, places obsidian
 * there if needed, places a crystal on it and breaks it, then keeps looping crystals on the same block while the
 * target stays put. Aim is silent by default and every action goes out only once the rotation sent points at it.
 */
@ModuleInfo(name = "AutoObbyCrystal", description = "Places obsidian and crystals by the target and loops crystals on it", category = ModuleCategory.COMBAT, tier = ModuleTier.BLATANT)
public class AutoObbyCrystal extends Module {

    /** Squared reach for a candidate block centre from either eye position (5.25 blocks). */
    private static final double SEARCH_REACH_SQ = 27.5625;
    /** Obsidian goes against a face no further than this. */
    private static final double OBBY_REACH = 4.25;
    /** An aim point further than this from the eyes aborts the sequence. */
    private static final double AIM_REACH = 4.4;
    private static final float AIM_TOLERANCE = 6f;
    private static final float CRYSTAL_POWER = 6f;
    private static final EquipmentSlot[] ARMOUR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private enum Stage { IDLE, PLACE_OBSIDIAN, PLACE_CRYSTAL, BREAK_CRYSTAL }

    public enum BreakMode {
        /** Never attacks; you break the crystal yourself and the module places the next one. */
        Manual,
        /** Attacks with whatever is in hand. */
        Attack,
        /** Switches back to the slot you were on before attacking. */
        SwapAttack
    }

    public enum MomentumMode { None, TowardHole, AwayFromSelf, TowardCenter }

    public final DoubleProperty targetRange = new DoubleProperty("Target Range", new DoubleValue(1, 16, 10, 0.5));
    public final DoubleProperty minDamage = new DoubleProperty("Min Damage", new DoubleValue(0, 36, 6, 0.5));
    public final DoubleProperty maxSelfDamage = new DoubleProperty("Max Self Damage", new DoubleValue(0, 36, 8, 0.5));
    public final BooleanProperty antiSuicide = new BooleanProperty("Anti Suicide", true);
    public final EnumProperty<BreakMode> breakMode = new EnumProperty<>("Break Mode", BreakMode.Attack);
    public final BooleanProperty rotateSilently = new BooleanProperty("Rotate Silently", true);
    public final BooleanProperty swapBack = new BooleanProperty("Swap Back", true);

    @PropertyGroup(Group.ADVANCED)
    public final DoubleProperty maxTargetDistance = new DoubleProperty("Max Target Distance", new DoubleValue(1, 20, 12, 0.5));
    @PropertyGroup(Group.ADVANCED)
    public final RangeProperty placeDelay = new RangeProperty("Place Delay (ms)", new RangeValue(0, 500, 60, 120, 1));
    @PropertyGroup(Group.ADVANCED)
    public final RangeProperty breakDelay = new RangeProperty("Break Delay (ms)", new RangeValue(0, 500, 60, 140, 1));
    @PropertyGroup(Group.ADVANCED)
    public final RangeProperty firstActionDelay = new RangeProperty("First Action Delay (ms)", new RangeValue(0, 200, 40, 80, 1));
    @PropertyGroup(Group.ADVANCED)
    public final DoubleProperty moveTolerance = new DoubleProperty("Mid Sequence Movement Tolerance", new DoubleValue(1, 8, 3, 1));
    @PropertyGroup(Group.ADVANCED)
    public final DoubleProperty damageTolerance = new DoubleProperty("Mid Sequence Damage Tolerance", new DoubleValue(0, 1, 0.6, 0.05));
    @PropertyGroup(Group.ADVANCED)
    public final BooleanProperty debugAborts = new BooleanProperty("Debug Aborts", false);
    @PropertyGroup(Group.ADVANCED)
    public final BooleanProperty fastSwap = new BooleanProperty("Fast Swap", true);
    @PropertyGroup(Group.ADVANCED)
    @ShowWhen(property = "Rotate Silently", is = "true")
    public final DoubleProperty silentAimFov = new DoubleProperty("Silent Aim FOV", new DoubleValue(0, 180, 180, 1));
    @PropertyGroup(Group.ADVANCED)
    public final EnumProperty<MomentumMode> momentumMode = new EnumProperty<>("Momentum Mode", MomentumMode.None);
    @PropertyGroup(Group.ADVANCED)
    @ShowWhen(property = "Momentum Mode", not = "None")
    public final DoubleProperty momentumWeight = new DoubleProperty("Momentum Weight", new DoubleValue(0, 5, 1, 0.1));
    @PropertyGroup(Group.ADVANCED)
    public final DoubleProperty yawChangeWeight = new DoubleProperty("Yaw Change Weight", new DoubleValue(0, 1, 0.05, 0.01));
    @PropertyGroup(Group.ADVANCED)
    public final DoubleProperty yawChangeCap = new DoubleProperty("Yaw Change Cap", new DoubleValue(0, 180, 60, 1));
    @PropertyGroup(Group.ADVANCED)
    public final BooleanProperty prefer1x1 = new BooleanProperty("Prefer 1x1", true);
    @PropertyGroup(Group.ADVANCED)
    public final BooleanProperty preferExisting = new BooleanProperty("Prefer Existing Obby", true);
    @PropertyGroup(Group.ADVANCED)
    public final BooleanProperty onlyExisting = new BooleanProperty("Only Existing Obby", false);
    @PropertyGroup(Group.ADVANCED)
    public final DoubleProperty pathLookahead = new DoubleProperty("Path Lookahead", new DoubleValue(0, 10, 3, 1));
    @PropertyGroup(Group.ADVANCED)
    public final BooleanProperty predictTarget = new BooleanProperty("Predict Target", true);
    @PropertyGroup(Group.ADVANCED)
    @ShowWhen(property = "Predict Target", is = "true")
    public final DoubleProperty predictTicks = new DoubleProperty("Predict Ticks", new DoubleValue(0, 10, 3, 0.5));
    @PropertyGroup(Group.ADVANCED)
    @ShowWhen(property = "Predict Target", is = "true")
    public final DoubleProperty predictPingBonus = new DoubleProperty("Predict Ping Bonus", new DoubleValue(0, 2, 1, 0.05));
    @PropertyGroup(Group.ADVANCED)
    @ShowWhen(property = "Predict Target", is = "true")
    public final DoubleProperty predictMaxOffset = new DoubleProperty("Predict Max Offset", new DoubleValue(0, 8, 3, 0.25));
    @PropertyGroup(Group.ADVANCED)
    public final BooleanProperty predictSelf = new BooleanProperty("Predict Self", true);
    @PropertyGroup(Group.ADVANCED)
    @ShowWhen(property = "Predict Self", is = "true")
    public final DoubleProperty selfPredictTicks = new DoubleProperty("Self Predict Ticks", new DoubleValue(0, 8, 2, 0.5));
    @PropertyGroup(Group.ADVANCED)
    @ShowWhen(property = "Predict Self", is = "true")
    public final DoubleProperty selfPredictMaxOffset = new DoubleProperty("Self Predict Max Offset", new DoubleValue(0, 6, 2, 0.25));
    @PropertyGroup(Group.ADVANCED)
    public final DoubleProperty reach = new DoubleProperty("Reach", new DoubleValue(1, 3, 2.95, 0.05));

    @PropertyGroup(Group.VISUAL)
    public final BooleanProperty render = new BooleanProperty("Render", true);
    @PropertyGroup(Group.VISUAL)
    @ShowWhen(property = "Render", is = "true")
    public final BooleanProperty renderTarget = new BooleanProperty("Render Target", true);
    @PropertyGroup(Group.VISUAL)
    @ShowWhen(property = "Render", is = "true")
    public final BooleanProperty renderPlacement = new BooleanProperty("Render Placement", true);
    @PropertyGroup(Group.VISUAL)
    @ShowWhen(property = "Render", is = "true")
    public final BooleanProperty renderCandidates = new BooleanProperty("Render Candidates", true);
    @PropertyGroup(Group.VISUAL)
    @ShowWhen(property = "Render", is = "true")
    @ShowWhen(property = "Render Candidates", is = "true")
    public final DoubleProperty maxCandidates = new DoubleProperty("Max Candidates", new DoubleValue(1, 30, 8, 1));

    /** True while a sequence is running, so other crystal modules can stand back. */
    public static boolean active;

    private Stage stage = Stage.IDLE;
    private Player target;
    /** The block the crystal sits on, the block obsidian is placed against, and where to aim for that placement. */
    private BlockPos base, support;
    private Vec3 obbyAim;
    /** Where the target stood when the sequence started. */
    private BlockPos startPos;
    private int drift;
    /** Whether anything has been sent this sequence (the first action waits for the reaction delay). */
    private boolean acted;
    /** Whether the break stage already fell back to placing once after timing out. */
    private boolean retriedPlace;
    /** The crystal just attacked, which no longer counts as occupying its block. */
    private int lastBroken = -1;
    /** Whether a crystal was found but sits beyond Reach. */
    private boolean crystalOutOfReach;
    /** The hotbar slot from before the first swap, or -1. */
    private int originalSlot = -1;

    private final MSTimer placeTimer = new MSTimer(), breakTimer = new MSTimer(), stageTimer = new MSTimer(),
            firstTimer = new MSTimer(), errorTimer = MSTimer.expired();
    private double placeMs, breakMs, firstMs;

    /** The point aimed at this tick, and the stage it was aimed for. */
    private Vec3 planned;
    private Stage plannedStage;

    // target velocity, smoothed per tick
    private Entity trackedEntity;
    private Vec3 trackedPos;
    private Vec3 trackedVel = Vec3.ZERO;
    private long trackedTick = Long.MIN_VALUE;

    private final List<Candidate> candidates = new ArrayList<>();
    private final BlockPos.MutableBlockPos scan = new BlockPos.MutableBlockPos();

    // render
    private final TargetFx.Target fx = new TargetFx.Target();
    private final Map<BlockPos, Float> candidateFade = new LinkedHashMap<>();
    private final Set<BlockPos> shownCandidates = new HashSet<>();
    private Player shownTarget;
    private BlockPos shownBase;
    private float targetFade, placementFade;
    private long lastFrame;

    private record Candidate(BlockPos base, BlockPos support, Vec3 aim, boolean needsObby, boolean hasCrystal, double score) {
    }

    @Override
    public boolean isSwappingHotbar() {
        return originalSlot != -1;
    }

    @Override
    protected void onEnable() {
        reset();
    }

    @Override
    protected void onDisable() {
        restoreSlot();
        reset();
    }

    private void reset() {
        stage = Stage.IDLE;
        target = null;
        base = null;
        support = null;
        obbyAim = null;
        startPos = null;
        acted = false;
        retriedPlace = false;
        lastBroken = -1;
        originalSlot = -1;
        planned = null;
        plannedStage = null;
        trackedEntity = null;
        trackedPos = null;
        trackedVel = Vec3.ZERO;
        trackedTick = Long.MIN_VALUE;
        candidates.clear();
        candidateFade.clear();
        shownCandidates.clear();
        shownTarget = null;
        shownBase = null;
        targetFade = 0;
        placementFade = 0;
        lastFrame = 0;
        active = false;
    }

    // ---------------------------------------------------------------
    //  Tick
    // ---------------------------------------------------------------

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        planned = null;
        plannedStage = null;
        if (mc.level == null)
            return;
        if (mc.gui.screen() != null || !mc.isWindowActive() || mc.player.isUsingItem()) {
            active = false;
            return;
        }
        if (AutoHitCrystal.active) {
            if (stage != Stage.IDLE)
                abort("AutoHitCrystal is running");
            restoreSlot();
            active = false;
            return;
        }
        track(target != null ? target : nearestEnemy());

        if (stage != Stage.IDLE && !targetValid())
            abort("invalid target");
        if (stage != Stage.IDLE && targetMoved())
            abort("target moved " + drift);
        if (stage != Stage.IDLE && stageTimer.hasTimeElapsed(stageTimeout())) {
            if (stage == Stage.BREAK_CRYSTAL && !retriedPlace) {
                // the crystal never showed up: place it again once
                retriedPlace = true;
                enter(Stage.PLACE_CRYSTAL);
            } else {
                abort("stage timeout " + (int) stageTimeout() + "ms");
            }
        }

        if (stage == Stage.IDLE) {
            restoreSlot();
            search();
        } else if (stage == Stage.BREAK_CRYSTAL && breakMode.getValue() == BreakMode.Manual) {
            // you break it yourself; once it is gone the next one goes down
            if (base != null && !crystalAt(base.above()) && breakTimer.hasTimeElapsed(breakMs))
                enter(Stage.PLACE_CRYSTAL);
        }
        active = stage != Stage.IDLE;
        if (stage == Stage.IDLE || !ready())
            return;

        Vec3 aim = aimPoint();
        if (unreachable(aim)) {
            abort("unreachable");
            return;
        }
        if (aim == null)
            return;
        Vec3 eyes = mc.player.getEyePosition(1f);
        float[] rots = RotationUtils.rotationsTo(eyes, aim);
        if (rotateSilently.getValue()
                && Math.abs(RotationUtils.getYawDifference(rots[0], mc.player.getYRot())) > silentAimFov.getValue().getInput()) {
            abort("silent aim FOV");
            return;
        }
        event.setYaw(rots[0]);
        event.setPitch(rots[1]);
        event.setSpeed(rotateSilently.getValue() ? 180f : 40f);
        event.setPreventDuplicateLook(true);
        event.setMovementFix(SilentRotationManager.MovementFix.SILENT);
        planned = aim;
        plannedStage = stage;
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onPost = event -> {
        if (planned == null || mc.level == null)
            return;
        Vec3 aim = planned;
        Stage forStage = plannedStage;
        planned = null;
        plannedStage = null;
        if (!rotateSilently.getValue()) {
            // the camera follows what was sent, like turning the mouse
            mc.player.setYRot(event.getYaw());
            mc.player.setXRot(event.getPitch());
        }
        if (forStage != stage)
            return;
        float[] want = RotationUtils.rotationsTo(mc.player.getEyePosition(1f), aim);
        if (Math.abs(RotationUtils.getYawDifference(event.getYaw(), want[0])) > AIM_TOLERANCE
                || Math.abs(event.getPitch() - want[1]) > AIM_TOLERANCE)
            return;
        switch (stage) {
            case PLACE_OBSIDIAN -> placeObsidian(event.getRayTrace());
            case PLACE_CRYSTAL -> placeCrystal(event.getRayTrace());
            case BREAK_CRYSTAL -> breakCrystal(event.getYaw(), event.getPitch());
            default -> {
            }
        }
    };

    /** Whether the current stage may act yet: the reaction delay first, then the place and break delays. */
    private boolean ready() {
        if (!acted)
            return !(stage == Stage.BREAK_CRYSTAL && breakMode.getValue() == BreakMode.Manual) && firstTimer.hasTimeElapsed(firstMs);
        return switch (stage) {
            case PLACE_OBSIDIAN -> true;
            case PLACE_CRYSTAL -> placeTimer.hasTimeElapsed(placeMs);
            case BREAK_CRYSTAL -> breakMode.getValue() != BreakMode.Manual && breakTimer.hasTimeElapsed(breakMs);
            default -> false;
        };
    }

    private Vec3 aimPoint() {
        return switch (stage) {
            case PLACE_OBSIDIAN -> obbyAim;
            case PLACE_CRYSTAL -> visiblePoint(base);
            case BREAK_CRYSTAL -> {
                EndCrystal crystal = findCrystal();
                yield crystal == null ? null : crystalAimPoint(crystal);
            }
            default -> null;
        };
    }

    private boolean unreachable(Vec3 aim) {
        if (stage == Stage.BREAK_CRYSTAL)
            return crystalOutOfReach;
        return aim != null && mc.player.getEyePosition(1f).distanceTo(aim) > AIM_REACH;
    }

    // ---------------------------------------------------------------
    //  Actions
    // ---------------------------------------------------------------

    private void placeObsidian(BlockHitResult hit) {
        if (isObby(base)) {
            enter(Stage.PLACE_CRYSTAL);
            return;
        }
        if (solid(base) || entityIn(base, 1)) {
            abort("base blocked");
            return;
        }
        int slot = HotbarUse.find(s -> s.is(Items.OBSIDIAN));
        if (slot == -1) {
            error("No obsidian in hotbar.");
            return;
        }
        if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(support)
                || !hit.getBlockPos().relative(hit.getDirection()).equals(base))
            return;
        if (!swapTo(slot))
            return;
        interact(InteractionHand.MAIN_HAND, hit);
        placeTimer.reset();
        enter(Stage.PLACE_CRYSTAL);
    }

    private void placeCrystal(BlockHitResult hit) {
        if (!isObby(base))
            return;
        BlockPos spawn = base.above();
        if (crystalAt(spawn)) {
            enter(Stage.BREAK_CRYSTAL);
            return;
        }
        if (!mc.level.getBlockState(spawn).isAir() || entityIn(spawn, 2)) {
            abort("crystal spawn blocked");
            return;
        }
        Vec3 boom = crystalPos(base);
        double damage = damage(target, Vec3.ZERO, boom, null);
        if (predictTarget.getValue() && targetMoving())
            damage = Math.max(damage, damage(target, predictedTarget().subtract(target.position()), boom, null));
        if (damage < minDamage.getValue().getInput() * damageTolerance.getValue().getInput()) {
            abort(String.format("re-gate target damage %.1f", damage));
            return;
        }
        double self = damage(mc.player, Vec3.ZERO, boom, null);
        if (selfTooHigh(self)) {
            abort(String.format("re-gate self damage %.1f", self));
            return;
        }
        InteractionHand hand;
        if (mc.player.getOffhandItem().is(Items.END_CRYSTAL)) {
            hand = InteractionHand.OFF_HAND;
        } else {
            int slot = HotbarUse.find(s -> s.is(Items.END_CRYSTAL));
            if (slot == -1) {
                error("No end crystals in hotbar.");
                return;
            }
            if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(base))
                return;
            if (!swapTo(slot))
                return;
            hand = InteractionHand.MAIN_HAND;
        }
        if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(base))
            return;
        interact(hand, hit);
        breakTimer.reset();
        breakMs = breakDelay.getValue().getRandomInRange();
        enter(Stage.BREAK_CRYSTAL);
    }

    private void breakCrystal(float yaw, float pitch) {
        EndCrystal crystal = findCrystal();
        if (crystal == null)
            return;
        Vec3 eyes = mc.player.getEyePosition(1f);
        Vec3 end = eyes.add(Entity.calculateViewVector(pitch, yaw).scale(6));
        Optional<Vec3> hit = crystal.getBoundingBox().clip(eyes, end);
        if (hit.isEmpty() || eyes.distanceTo(hit.get()) > reach.getValue().getInput())
            return;
        double self = damage(mc.player, Vec3.ZERO, crystalPos(base), null);
        if (selfTooHigh(self))
            return;
        if (breakMode.getValue() == BreakMode.SwapAttack && originalSlot != -1
                && mc.player.getInventory().getSelectedSlot() != originalSlot) {
            // swap this tick, attack the next, so the slot change never lands with the attack
            mc.player.getInventory().setSelectedSlot(originalSlot);
            stageTimer.reset();
            return;
        }
        mc.gameMode.attack(mc.player, crystal);
        PlayerUtils.swingItem();
        acted = true;
        lastBroken = crystal.getId();
        placeTimer.reset();
        placeMs = placeDelay.getValue().getRandomInRange();
        retriedPlace = false;
        enter(Stage.PLACE_CRYSTAL);
    }

    private void interact(InteractionHand hand, BlockHitResult hit) {
        if (mc.gameMode.useItemOn(mc.player, hand, hit).consumesAction())
            PlayerUtils.swingItem();
        acted = true;
    }

    /** Selects the slot; without Fast Swap the action waits a tick after the swap. */
    private boolean swapTo(int slot) {
        int current = mc.player.getInventory().getSelectedSlot();
        if (current == slot)
            return true;
        if (originalSlot == -1)
            originalSlot = current;
        mc.player.getInventory().setSelectedSlot(slot);
        if (fastSwap.getValue())
            return true;
        stageTimer.reset();
        return false;
    }

    private void restoreSlot() {
        if (originalSlot == -1)
            return;
        if (swapBack.getValue() && mc.player != null)
            mc.player.getInventory().setSelectedSlot(originalSlot);
        originalSlot = -1;
    }

    private void enter(Stage next) {
        stage = next;
        stageTimer.reset();
    }

    private void abort(String reason) {
        if (debugAborts.getValue() && stage != Stage.IDLE)
            NotificationManager.show(new Notification(NotificationType.INFO, getName(), "Abort: " + stage + ", " + reason, 1));
        endSequence();
    }

    private void error(String message) {
        if (errorTimer.hasTimeElapsed(3000L)) {
            NotificationManager.show(new Notification(NotificationType.ERROR, getName(), message, 1));
            errorTimer.reset();
        }
        endSequence();
    }

    private void endSequence() {
        restoreSlot();
        stage = Stage.IDLE;
        target = null;
        base = null;
        support = null;
        obbyAim = null;
        startPos = null;
        retriedPlace = false;
        planned = null;
        plannedStage = null;
        candidates.clear();
        active = false;
    }

    /** Stage timeout from ping: longer for the break, which waits on the server spawning the crystal. */
    private float stageTimeout() {
        int ping = latency(mc.player);
        return stage == Stage.BREAK_CRYSTAL
                ? Math.min(1500f, Math.max(500f, 300f + ping * 1.5f))
                : Math.min(600f, Math.max(200f, 120f + ping * 0.8f));
    }

    private boolean targetValid() {
        return target != null && target.isAlive() && !target.isRemoved()
                && mc.player.distanceTo(target) <= maxTargetDistance.getValue().getInput()
                && TargetManager.isValidTarget(target);
    }

    private boolean targetMoved() {
        if (startPos == null || target == null)
            return false;
        BlockPos now = target.blockPosition();
        drift = Math.max(Math.abs(now.getX() - startPos.getX()),
                Math.max(Math.abs(now.getY() - startPos.getY()), Math.abs(now.getZ() - startPos.getZ())));
        return drift > (stage == Stage.PLACE_OBSIDIAN ? 2 : (int) moveTolerance.getValue().getInput());
    }

    private boolean selfTooHigh(double self) {
        return self > maxSelfDamage.getValue().getInput()
                || (antiSuicide.getValue() && self >= mc.player.getHealth() + mc.player.getAbsorptionAmount() - 1f);
    }

    // ---------------------------------------------------------------
    //  Search
    // ---------------------------------------------------------------

    private void search() {
        Player enemy = nearestEnemy();
        if (enemy == null || !enemy.isAlive() || mc.player.distanceTo(enemy) > targetRange.getValue().getInput()) {
            candidates.clear();
            return;
        }
        target = enemy;
        Candidate best = findBest();
        if (best == null) {
            target = null;
            return;
        }
        base = best.base();
        support = best.support();
        obbyAim = best.aim();
        startPos = target.blockPosition();
        retriedPlace = false;
        acted = false;
        enter(best.hasCrystal() ? Stage.BREAK_CRYSTAL : best.needsObby() ? Stage.PLACE_OBSIDIAN : Stage.PLACE_CRYSTAL);
        firstTimer.reset();
        placeTimer.reset();
        breakTimer.reset();
        firstMs = firstActionDelay.getValue().getRandomInRange();
        placeMs = placeDelay.getValue().getRandomInRange();
        breakMs = breakDelay.getValue().getRandomInRange();
    }

    private Candidate findBest() {
        candidates.clear();
        BlockPos feet = target.blockPosition();
        Vec3 predicted = predictedTarget();
        Vec3 shift = predicted.subtract(target.position());
        Vec3 eyes = mc.player.getEyePosition(1f);
        Vec3 eyesAhead = predictedEyes();
        Vec3 momentum = momentumDirection(predicted);
        float health = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        boolean moving = predictTarget.getValue() && targetMoving();
        boolean silent = rotateSilently.getValue();
        double reachValue = reach.getValue().getInput();
        double min = minDamage.getValue().getInput();
        Candidate best = null;

        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    scan.set(feet.getX() + dx, feet.getY() + dy, feet.getZ() + dz);
                    double cx = scan.getX() + 0.5, cy = scan.getY() + 0.5, cz = scan.getZ() + 0.5;
                    if (eyes.distanceToSqr(cx, cy, cz) > SEARCH_REACH_SQ && eyesAhead.distanceToSqr(cx, cy, cz) > SEARCH_REACH_SQ)
                        continue;
                    BlockPos pos = scan.immutable();
                    BlockPos spawn = pos.above();
                    boolean obby = isObby(pos);
                    boolean hasCrystal = obby && crystalAt(spawn);
                    if (!hasCrystal && (!mc.level.getBlockState(spawn).isAir() || pathBlocked(spawn, 2)))
                        continue;

                    BlockPos placeAgainst = null;
                    Vec3 aim;
                    if (obby) {
                        if (hasCrystal) {
                            aim = new Vec3(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5);
                            if (eyes.distanceTo(aim) > reachValue + 0.5 && eyesAhead.distanceTo(aim) > reachValue + 0.5)
                                continue;
                            if (silent && !inFov(aim))
                                continue;
                        } else {
                            aim = visiblePoint(pos);
                            if (aim == null || (silent && !inFov(aim)))
                                continue;
                        }
                    } else {
                        if (onlyExisting.getValue() || solid(pos) || pathBlocked(pos, 1))
                            continue;
                        aim = null;
                        double nearest = Double.MAX_VALUE;
                        for (Direction dir : Direction.values()) {
                            BlockPos neighbour = pos.relative(dir);
                            if (mc.level.getBlockState(neighbour).canBeReplaced())
                                continue;
                            Direction face = dir.getOpposite();
                            Vec3 point = Vec3.atCenterOf(neighbour).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
                            double dist = eyes.distanceTo(point);
                            if (dist > OBBY_REACH && eyesAhead.distanceTo(point) > OBBY_REACH)
                                continue;
                            if (silent && !rayHits(point, neighbour))
                                continue;
                            if (dist < nearest) {
                                nearest = dist;
                                placeAgainst = neighbour;
                                aim = point;
                            }
                        }
                        Vec3 top = Vec3.atCenterOf(pos).add(0, 0.5, 0);
                        if (placeAgainst == null || (eyes.distanceTo(top) > OBBY_REACH && eyesAhead.distanceTo(top) > OBBY_REACH))
                            continue;
                    }

                    Vec3 boom = crystalPos(pos);
                    BlockPos cover = obby ? null : pos;
                    double damage = damage(target, Vec3.ZERO, boom, cover);
                    if (moving && damage < min)
                        damage = Math.max(damage, damage(target, shift, boom, cover));
                    if (damage < min)
                        continue;
                    double self = damage(mc.player, Vec3.ZERO, boom, cover);
                    if (self > maxSelfDamage.getValue().getInput() || (antiSuicide.getValue() && self >= health - 1f))
                        continue;

                    double score = damage;
                    if (damage >= target.getHealth() + target.getAbsorptionAmount())
                        score += 50;
                    if (momentum != null) {
                        Vec3 push = new Vec3(predicted.x - boom.x, 0, predicted.z - boom.z);
                        if (push.lengthSqr() > 1e-4)
                            score += momentumWeight.getValue().getInput() * 8 * Math.max(0, push.normalize().dot(momentum));
                    }
                    if (prefer1x1.getValue() && pos.getY() == feet.getY()
                            && Math.max(Math.abs(pos.getX() - feet.getX()), Math.abs(pos.getZ() - feet.getZ())) == 1)
                        score += 3;
                    if (obby && preferExisting.getValue())
                        score += hasCrystal ? 8 : 4;
                    double yawWeight = yawChangeWeight.getValue().getInput();
                    if (yawWeight > 0) {
                        float[] rots = RotationUtils.rotationsTo(eyes, aim);
                        double change = Math.abs(RotationUtils.getYawDifference(rots[0], mc.player.getYRot()));
                        score -= yawWeight * Math.min(change, yawChangeCap.getValue().getInput());
                    }
                    score -= 0.2 * eyes.distanceTo(aim);

                    Candidate c = new Candidate(pos, placeAgainst, aim, !obby, hasCrystal, score);
                    candidates.add(c);
                    if (best == null || score > best.score())
                        best = c;
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(Candidate::score).reversed());
        return best;
    }

    private Player nearestEnemy() {
        Player best = null;
        double range = targetRange.getValue().getInput();
        for (Player p : mc.level.players()) {
            if (!p.isAlive() || !TargetManager.isValidTarget(p) || mc.player.distanceTo(p) > range)
                continue;
            if (best == null || mc.player.distanceTo(p) < mc.player.distanceTo(best))
                best = p;
        }
        return best;
    }

    /** The direction the blast should push the target in, or null for no preference. */
    private Vec3 momentumDirection(Vec3 predicted) {
        Vec3 dir = switch (momentumMode.getValue()) {
            case AwayFromSelf -> predicted.subtract(mc.player.position());
            case TowardCenter -> new Vec3(-predicted.x, 0, -predicted.z);
            case TowardHole -> {
                BlockPos hole = nearestHole();
                yield hole == null ? null : Vec3.atCenterOf(hole).subtract(predicted);
            }
            default -> null;
        };
        if (dir == null)
            return null;
        dir = new Vec3(dir.x, 0, dir.z);
        return dir.lengthSqr() > 1e-4 ? dir.normalize() : null;
    }

    /** The nearest one-block hole (air at feet and head, solid floor and walls) within six blocks of the target. */
    private BlockPos nearestHole() {
        BlockPos feet = target.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                p.set(feet.getX() + dx, feet.getY(), feet.getZ() + dz);
                if (!mc.level.getBlockState(p).isAir() || !mc.level.getBlockState(p.above()).isAir() || !solid(p.below()))
                    continue;
                boolean walled = true;
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    if (!solid(p.relative(dir))) {
                        walled = false;
                        break;
                    }
                }
                double d = dx * dx + dz * dz;
                if (walled && d < bestDist) {
                    bestDist = d;
                    best = p.immutable();
                }
            }
        }
        return best;
    }

    // ---------------------------------------------------------------
    //  Prediction
    // ---------------------------------------------------------------

    /** Smooths the target's per-tick movement, restarting when the entity changes or a tick was missed. */
    private void track(Entity e) {
        long tick = mc.level.getGameTime();
        if (e == null) {
            trackedEntity = null;
            trackedPos = null;
            trackedVel = Vec3.ZERO;
            trackedTick = tick;
            return;
        }
        Vec3 pos = e.position();
        if (e == trackedEntity && trackedPos != null && tick - trackedTick == 1) {
            trackedVel = trackedVel.scale(0.5).add(pos.subtract(trackedPos).scale(0.5));
        } else if (e != trackedEntity || tick != trackedTick) {
            trackedEntity = e;
            trackedVel = Vec3.ZERO;
        }
        trackedPos = pos;
        trackedTick = tick;
    }

    private Vec3 targetVelocity() {
        return target != null && trackedEntity == target ? trackedVel : Vec3.ZERO;
    }

    private boolean targetMoving() {
        Vec3 v = targetVelocity();
        return v.x * v.x + v.z * v.z > 4e-4;
    }

    /** Where the target will be after Predict Ticks plus its ping in ticks, capped to Predict Max Offset. */
    private Vec3 predictedTarget() {
        Vec3 pos = target.position();
        if (!predictTarget.getValue())
            return pos;
        Vec3 v = targetVelocity();
        if (v.lengthSqr() < 1e-6)
            return pos;
        float ticks = Mth.clamp((float) (predictTicks.getValue().getInput()
                + latency(target) / 50f * predictPingBonus.getValue().getInput()), 0f, 20f);
        return pos.add(capped(new Vec3(v.x * ticks, 0, v.z * ticks), predictMaxOffset.getValue().getInput()));
    }

    private Vec3 predictedEyes() {
        Vec3 eyes = mc.player.getEyePosition(1f);
        if (!predictSelf.getValue())
            return eyes;
        Vec3 v = mc.player.getDeltaMovement();
        if (v.lengthSqr() < 1e-6)
            return eyes;
        double ticks = selfPredictTicks.getValue().getInput();
        return eyes.add(capped(new Vec3(v.x * ticks, 0, v.z * ticks), selfPredictMaxOffset.getValue().getInput()));
    }

    private static Vec3 capped(Vec3 v, double max) {
        if (v.lengthSqr() <= max * max)
            return v;
        return v.lengthSqr() > 1e-6 ? v.normalize().scale(max) : Vec3.ZERO;
    }

    private static int latency(Player p) {
        if (mc.getConnection() == null)
            return 0;
        PlayerInfo info = mc.getConnection().getPlayerInfo(p.getUUID());
        return info == null ? 0 : Math.max(0, info.getLatency());
    }

    // ---------------------------------------------------------------
    //  World checks
    // ---------------------------------------------------------------

    private static boolean isObby(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        return state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK);
    }

    private static boolean solid(BlockPos pos) {
        return !mc.level.getBlockState(pos).canBeReplaced();
    }

    private static Vec3 crystalPos(BlockPos base) {
        return new Vec3(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5);
    }

    /** A crystal other than the one just attacked inside this block. */
    private boolean crystalAt(BlockPos pos) {
        if (lastBroken != -1 && mc.level.getEntity(lastBroken) == null)
            lastBroken = -1;
        return !mc.level.getEntities((Entity) null, new AABB(pos), e -> e instanceof EndCrystal && e.getId() != lastBroken).isEmpty();
    }

    /** Anything but dropped items (and the crystal just attacked) in this column of blocks. */
    private boolean entityIn(BlockPos pos, int height) {
        AABB box = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + height, pos.getZ() + 1);
        return !mc.level.getEntities((Entity) null, box,
                e -> !(e instanceof ItemEntity) && !e.isSpectator() && !(e instanceof EndCrystal && e.getId() == lastBroken)).isEmpty();
    }

    /** Blocked now, or by the target or you walking into it within Path Lookahead ticks. */
    private boolean pathBlocked(BlockPos pos, int height) {
        if (entityIn(pos, height))
            return true;
        int ticks = (int) pathLookahead.getValue().getInput();
        if (ticks <= 0)
            return false;
        AABB column = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + height, pos.getZ() + 1);
        Vec3 v = targetVelocity();
        if (target != null && v.lengthSqr() > 1e-6) {
            AABB box = target.getBoundingBox();
            for (int i = 1; i <= ticks; i++)
                if (box.move(v.scale(i)).intersects(column))
                    return true;
        }
        Vec3 self = mc.player.getDeltaMovement();
        if (self.lengthSqr() > 1e-6) {
            AABB box = mc.player.getBoundingBox();
            for (int i = 1; i <= ticks; i++)
                if (box.move(self.scale(i)).intersects(column))
                    return true;
        }
        return false;
    }

    private boolean inFov(Vec3 point) {
        float[] rots = RotationUtils.rotationsTo(mc.player.getEyePosition(1f), point);
        return Math.abs(RotationUtils.getYawDifference(rots[0], mc.player.getYRot())) <= silentAimFov.getValue().getInput();
    }

    /** Within the silent aim FOV, and a look at the point lands on this block. */
    private boolean rayHits(Vec3 point, BlockPos pos) {
        if (!inFov(point))
            return false;
        Vec3 eyes = mc.player.getEyePosition(1f);
        Vec3 dir = point.subtract(eyes);
        if (dir.lengthSqr() < 1e-6)
            return false;
        Vec3 end = point.add(dir.normalize().scale(0.1));
        BlockHitResult hit = mc.level.clip(new ClipContext(eyes, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
    }

    /** A point on the block a straight look reaches: the top centre first, then the side faces. */
    private Vec3 visiblePoint(BlockPos pos) {
        if (pos == null)
            return null;
        Vec3 eyes = mc.player.getEyePosition(1f);
        Vec3 top = new Vec3(pos.getX() + 0.5, pos.getY() + 0.99, pos.getZ() + 0.5);
        if (eyes.y > pos.getY() + 1 && reachable(eyes, top, pos))
            return top;
        Vec3 best = null;
        double bestDist = Double.MAX_VALUE;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            Vec3 face = Vec3.atCenterOf(pos).add(dir.getStepX() * 0.49, 0.3, dir.getStepZ() * 0.49);
            double d = eyes.distanceTo(face);
            if (d < bestDist && reachable(eyes, face, pos)) {
                bestDist = d;
                best = face;
            }
        }
        return best;
    }

    private static boolean reachable(Vec3 eyes, Vec3 point, BlockPos pos) {
        if (eyes.distanceTo(point) > AIM_REACH)
            return false;
        Vec3 dir = point.subtract(eyes);
        Vec3 end = point.add(dir.normalize().scale(0.1));
        BlockHitResult hit = mc.level.clip(new ClipContext(eyes, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
    }

    /** The crystal on the base that can be broken; flags one that is there but out of Reach. */
    private EndCrystal findCrystal() {
        crystalOutOfReach = false;
        if (base == null || !crystalAt(base.above()))
            return null;
        Vec3 centre = Vec3.atCenterOf(base.above());
        Vec3 eyes = mc.player.getEyePosition(1f);
        for (Entity e : mc.level.getEntities((Entity) null, new AABB(base.above()).inflate(1.5), x -> x instanceof EndCrystal)) {
            EndCrystal crystal = (EndCrystal) e;
            if (!crystal.isAlive() || crystal.getId() == lastBroken || crystal.tickCount < 2 || crystal.position().distanceTo(centre) > 1)
                continue;
            if (boxDistance(crystal.getBoundingBox(), eyes) <= reach.getValue().getInput())
                return crystal;
            crystalOutOfReach = true;
        }
        return null;
    }

    /** The point of the crystal's box closest to the eyes, pulled a little toward its centre. */
    private static Vec3 crystalAimPoint(EndCrystal crystal) {
        Vec3 eyes = mc.player.getEyePosition(1f);
        AABB box = crystal.getBoundingBox();
        Vec3 nearest = new Vec3(Mth.clamp(eyes.x, box.minX, box.maxX), Mth.clamp(eyes.y, box.minY, box.maxY), Mth.clamp(eyes.z, box.minZ, box.maxZ));
        Vec3 toCentre = box.getCenter().subtract(nearest);
        return toCentre.lengthSqr() > 1e-6 ? nearest.add(toCentre.normalize().scale(0.15)) : nearest;
    }

    private static double boxDistance(AABB box, Vec3 p) {
        double dx = Math.max(box.minX - p.x, Math.max(0, p.x - box.maxX));
        double dy = Math.max(box.minY - p.y, Math.max(0, p.y - box.maxY));
        double dz = Math.max(box.minZ - p.z, Math.max(0, p.z - box.maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // ---------------------------------------------------------------
    //  Damage
    // ---------------------------------------------------------------

    /**
     * Vanilla crystal blast damage (power 6) to the entity moved by shift, with an optional block that is not there yet
     * (obsidian about to be placed) treated as solid. Difficulty, armour, toughness, resistance and protection apply.
     */
    private static double damage(LivingEntity e, Vec3 shift, Vec3 boom, BlockPos cover) {
        if (e == null)
            return 0;
        Vec3 feet = e.position().add(shift);
        double d = feet.distanceTo(boom) / (CRYSTAL_POWER * 2);
        if (d > 1)
            return 0;
        double impact = (1 - d) * exposure(boom, e.getBoundingBox().move(shift), cover);
        double dmg = (impact * impact + impact) / 2 * 7 * CRYSTAL_POWER * 2 + 1;
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
        float effective = Mth.clamp(armour - (float) dmg / (2f + toughness / 4f), armour * 0.2f, 20f);
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
    private static double exposure(Vec3 boom, AABB box, BlockPos cover) {
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
                    if (!blocked(point, boom, cover))
                        seen++;
                    total++;
                }
            }
        }
        return total == 0 ? 0 : (double) seen / total;
    }

    private static boolean blocked(Vec3 from, Vec3 to, BlockPos cover) {
        return BlockGetter.traverseBlocks(from, to, null, (ctx, pos) -> {
            if (pos.equals(cover))
                return Boolean.TRUE;
            BlockState state = mc.level.getBlockState(pos);
            if (state.isAir())
                return null;
            VoxelShape shape = state.getCollisionShape(mc.level, pos);
            return !shape.isEmpty() && shape.clip(from, to, pos) != null ? Boolean.TRUE : null;
        }, ctx -> Boolean.FALSE);
    }

    // ---------------------------------------------------------------
    //  Render
    // ---------------------------------------------------------------

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRender = event -> {
        long now = System.currentTimeMillis();
        float step = lastFrame == 0 ? 0 : Math.min(1f, (now - lastFrame) / 180f);
        lastFrame = now;
        if (!render.getValue())
            return;
        float pulse = 0.5f + 0.5f * (float) Math.sin(now / 900.0 * Math.PI * 2);

        boolean showTarget = renderTarget.getValue() && target != null;
        if (showTarget)
            shownTarget = target;
        targetFade = approach(targetFade, showTarget ? 1 : 0, step);
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

        boolean showPlacement = renderPlacement.getValue() && base != null;
        if (showPlacement)
            shownBase = base;
        placementFade = approach(placementFade, showPlacement ? 1 : 0, step);
        if (placementFade > 0.01f && shownBase != null) {
            if (!isObby(shownBase)) {
                // still to be placed: a faint, slightly larger shell breathing round the spot
                AABB shell = new AABB(shownBase).inflate(0.01 * pulse);
                int a = Math.round(255 * placementFade * 0.25f);
                RenderUtils.renderBox(shell, RenderUtils.withAlpha(ThemeManager.getDarkerColor(), Math.max(1, a)), true, false);
            }
            drawBlock(shownBase, ThemeManager.getDarkerColor(), placementFade, pulse);
            drawBlock(shownBase.above(), ThemeManager.getMainColor(), placementFade, pulse);
        }

        shownCandidates.clear();
        boolean showCandidates = renderCandidates.getValue();
        if (showCandidates && stage == Stage.IDLE && !candidates.isEmpty()) {
            int count = Math.min(candidates.size(), (int) maxCandidates.getValue().getInput());
            for (int i = 0; i < count; i++) {
                BlockPos spawn = candidates.get(i).base().above();
                shownCandidates.add(spawn);
                float fade = approach(candidateFade.getOrDefault(spawn, 0f), 1, step);
                candidateFade.put(spawn, fade);
                float rank = 1f - (float) i / Math.max(1, count);
                drawGhost(spawn, fade * Math.max(0.15f, rank));
            }
        }
        Iterator<Map.Entry<BlockPos, Float>> it = candidateFade.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Float> entry = it.next();
            if (shownCandidates.contains(entry.getKey()))
                continue;
            float fade = approach(entry.getValue(), 0, step);
            if (fade < 0.01f) {
                it.remove();
                continue;
            }
            entry.setValue(fade);
            if (showCandidates)
                drawGhost(entry.getKey(), fade * 0.15f);
        }
    };

    private static float approach(float value, float goal, float step) {
        return value < goal ? Math.min(goal, value + step) : Math.max(goal, value - step);
    }

    /** A theme-coloured box that grows in and shrinks out with the fade and breathes while it is shown. */
    private static void drawBlock(BlockPos pos, int colour, float fade, float pulse) {
        int alpha = Math.round(255 * fade * (0.6f + 0.4f * pulse));
        AABB box = new AABB(pos).inflate(-(1 - fade) * 0.45);
        RenderUtils.renderBox(box, RenderUtils.withAlpha(colour, Math.max(1, alpha)), true, true);
    }

    private static void drawGhost(BlockPos pos, float strength) {
        int alpha = Math.round(160 * strength);
        if (alpha <= 0)
            return;
        RenderUtils.renderBox(new AABB(pos).inflate(-0.2), RenderUtils.withAlpha(ThemeManager.getGradientColor(), alpha), false, true);
    }
}
