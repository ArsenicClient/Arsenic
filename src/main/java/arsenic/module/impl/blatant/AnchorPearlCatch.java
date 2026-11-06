package arsenic.module.impl.blatant;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventSilentRotation;
import arsenic.gui.design.M3;
import arsenic.gui.hud.HudStyle;
import arsenic.gui.themes.ThemeManager;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.impl.client.AntiBot;
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
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.render.WorldToScreen;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.rotations.SilentRotationManager;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Watches enemy ender pearls, predicts where they land, and has a charged respawn anchor waiting there: it places the
 * anchor (optionally behind a cover block that soaks the blast for you), charges it with glowstone, and detonates it
 * once they arrive. Aim is silent and each click goes out only once the rotation being sent looks at the right face.
 */
@ModuleInfo(name = "AnchorPearlCatch", description = "Pre-places and detonates a respawn anchor where enemy pearls land", category = ModuleCategory.COMBAT, tier = ModuleTier.BLATANT)
public class AnchorPearlCatch extends Module {

    private static final Direction[] DIRECTIONS = Direction.values();
    private static final int[][] COVER_OFFSETS = {{-1, 0, 0}, {1, 0, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, -1}, {-1, 0, 1}, {1, 0, -1}, {1, 0, 1}};
    private static final double REACH = 4.4;
    private static final double SUPPORT_REACH_SQ = 18.0625;
    private static final double ANCHOR_RADIUS = 10.0;
    private static final int SIM_TICKS = 200;
    private static final int MAX_LAND_TICKS = 100;
    private static final int WAIT_TIMEOUT = 120;
    private static final int ACTIVE_TIMEOUT = 160;
    private static final int LAND_TIMEOUT = 30;
    private static final long STALL_MS = 200;
    private static final float AIM_TOLERANCE = 6f;

    public enum CoverBlock { Glowstone, Obsidian }

    private enum State { IDLE, WAIT, PLACE_ANCHOR, SAFE_COVER, CHARGE_ANCHOR, DETONATE }

    /** True while a catch is lined up or running, so other anchor modules can hold off. */
    public static boolean active;

    public final DoubleProperty scanRange = new DoubleProperty("Scan Range", new DoubleValue(16, 96, 48, 1));
    public final DoubleProperty minDamage = new DoubleProperty("Min Damage", new DoubleValue(0, 40, 15, 0.5));
    public final DoubleProperty maxSelfDamage = new DoubleProperty("Max Self Damage", new DoubleValue(0, 36, 10, 0.5));
    public final BooleanProperty antiSuicide = new BooleanProperty("Anti Suicide", true);
    public final BooleanProperty damageTick = new BooleanProperty("Damage Tick", true);
    public final BooleanProperty safeAnchor = new BooleanProperty("Safe Anchor", true);
    @ShowWhen(property = "Safe Anchor", is = "true")
    public final EnumProperty<CoverBlock> safeBlock = new EnumProperty<>("Safe Block", CoverBlock.Glowstone);
    public final DoubleProperty aimFov = new DoubleProperty("Aim FOV", new DoubleValue(0, 180, 180, 1));
    public final BooleanProperty swapBack = new BooleanProperty("Swap Back", true);
    public final RangeProperty placeDelay = new RangeProperty("Place Delay (ms)", new RangeValue(0, 500, 0, 40, 5));
    public final RangeProperty chargeDelay = new RangeProperty("Charge Delay (ms)", new RangeValue(0, 500, 20, 60, 5));
    public final RangeProperty detonateDelay = new RangeProperty("Detonate Delay (ms)", new RangeValue(0, 500, 40, 120, 5));
    public final BooleanProperty render = new BooleanProperty("Render", true);

    private final Set<UUID> handled = new HashSet<>();
    private State state = State.IDLE;
    private ThrownEnderpearl pearl;
    private Player target;
    private Vec3 landing;
    private int ticksToLand = -1;
    private BlockPos anchorPos, anchorSupport, coverPos, coverSupport;
    private Vec3 anchorHitVec, coverHitVec;
    private int restoreSlot = -1;
    private int clickMisses, activeTicks, waitTicks, landTicks, escapeTicks;
    private long stepStart, lastProgress, lastNotice;
    private double placeRoll, chargeRoll, detonateRoll;

    /** Set in the pre-movement hook, consumed in the post hook once the sent rotation lines up. */
    private Vec3 aim;
    private BlockPos clickBlock;
    private BlockPos clickResult;
    private Item clickItem;

    @Override
    public boolean isSwappingHotbar() {
        return restoreSlot != -1;
    }

    @Override
    protected void onEnable() {
        reset();
        handled.clear();
    }

    @Override
    protected void onDisable() {
        restoreHotbar();
        reset();
        handled.clear();
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        aim = null;
        clickBlock = null;
        if (mc.level == null) {
            active = false;
            return;
        }

        if (state == State.WAIT) {
            tickWait();
            active = state == State.WAIT && inWindow();
            if (state == State.WAIT) {
                if (inWindow())
                    preAim(event);
                return;
            }
        }

        if (mc.gui.screen() != null || mc.player.isUsingItem()) {
            active = false;
            return;
        }

        if (state == State.IDLE) {
            restoreHotbar();
            scan();
            active = false;
            return;
        }

        if (++activeTicks > ACTIVE_TIMEOUT) {
            abort("timed out", true);
            return;
        }
        long now = System.currentTimeMillis();
        if (state != State.DETONATE && now - lastProgress > STALL_MS + (long) currentDelay()) {
            abort("stalled", true);
            return;
        }
        if (state == State.DETONATE && !tickDetonate())
            return;
        if (state == State.IDLE)
            return;
        if (now - stepStart < currentDelay())
            return;

        planClick();
        if (aim == null || state == State.IDLE)
            return;
        if (mc.player.getEyePosition(1f).distanceTo(aim) > REACH) {
            abort("the anchor left our reach", true);
            return;
        }
        float[] rots = RotationUtils.rotationsTo(mc.player.getEyePosition(1f), aim);
        if (Math.abs(RotationUtils.getYawDifference(mc.player.getYRot(), rots[0])) >= aimFov.getValue().getInput()) {
            abort("outside aim FOV", true);
            return;
        }
        applyRotation(event, rots);

        // Swap first and click on a later tick, so the slot change never shares a tick with the use.
        if (clickItem != null && !mc.player.getMainHandItem().is(clickItem)) {
            int slot = findSlot(clickItem);
            if (slot != -1)
                selectSlot(slot);
            lastProgress = now;
            clickBlock = null;
        } else if (clickItem == null && mc.player.getMainHandItem().is(Items.GLOWSTONE)) {
            int slot = detonateSlot();
            if (slot == -1) {
                abort("no non-glowstone slot to detonate with", true);
                return;
            }
            selectSlot(slot);
            lastProgress = now;
            clickBlock = null;
        }
        active = true;
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onPost = event -> {
        if (aim == null || clickBlock == null || mc.level == null)
            return;
        float[] want = RotationUtils.rotationsTo(mc.player.getEyePosition(1f), aim);
        if (Math.abs(RotationUtils.getYawDifference(event.getYaw(), want[0])) > AIM_TOLERANCE
                || Math.abs(event.getPitch() - want[1]) > AIM_TOLERANCE)
            return;
        BlockHitResult hit = event.getRayTrace();
        if (hit == null || hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(clickBlock)
                || (clickResult != null && !hit.getBlockPos().relative(hit.getDirection()).equals(clickResult))) {
            if (++clickMisses >= 3)
                abort("lost the click line", true);
            return;
        }
        clickMisses = 0;
        click(hit);
        advanceAfterClick();
        aim = null;
        clickBlock = null;
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRenderWorld = event -> {
        if (!render.getValue() || state == State.IDLE)
            return;
        if (state == State.WAIT && landing != null)
            RenderUtils.renderBlock(BlockPos.containing(landing), ThemeManager.getGradientColor(), true, true);
        if (anchorPos != null && state != State.WAIT)
            RenderUtils.renderBlock(anchorPos, ThemeManager.getMainColor(), true, true);
        if (state == State.SAFE_COVER && coverPos != null)
            RenderUtils.renderBlock(coverPos, ThemeManager.getDarkerColor(), true, true);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRender2D> onRender2D = event -> {
        if (!render.getValue() || state == State.IDLE || anchorPos == null)
            return;
        if (mc.gui.screen() != null && !(mc.gui.screen() instanceof ChatScreen))
            return;
        Vec3 world = state == State.WAIT && landing != null
                ? landing.add(0, 0.8, 0)
                : new Vec3(anchorPos.getX() + 0.5, anchorPos.getY() + 1.3, anchorPos.getZ() + 0.5);
        WorldToScreen.Point p = WorldToScreen.project(world);
        if (!WorldToScreen.isOnScreen(p))
            return;
        String title = "Anchor";
        String eta = ticksToLand > 0 ? String.format("%.1fs", ticksToLand / 20f) : "Now";
        FontRendererExtension<?> fr = HudStyle.label();
        float padX = 6f, padY = 4f, gap = 3f;
        float lineH = fr.getHeight(title);
        float w = Math.max(fr.getWidth(title), fr.getWidth(eta)) + padX * 2f;
        float h = lineH * 2f + gap * 2f + 1f + padY * 2f;
        float x1 = p.x() - w / 2f, y1 = p.y() - h / 2f;
        HudStyle.card(x1, y1, x1 + w, y1 + h, HudStyle.RADIUS_COMPACT);
        float titleCy = y1 + padY + lineH / 2f;
        fr.drawString(title, p.x(), titleCy, ThemeManager.getMainColor(), fr.CENTREX, fr.CENTREY);
        float divY = y1 + padY + lineH + gap;
        HudStyle.divider(x1 + padX, x1 + w - padX, divY);
        fr.drawString(eta, p.x(), divY + 1f + gap + lineH / 2f, M3.onSurface(), fr.CENTREX, fr.CENTREY);
    };

    // ---- state machine ----

    private void scan() {
        if (findSlot(Items.RESPAWN_ANCHOR) == -1 || findSlot(Items.GLOWSTONE) == -1 || firstNonGlowstone() == -1)
            return;
        double range = scanRange.getValue().getInput();
        Vec3 eyes = mc.player.getEyePosition(1f);
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ThrownEnderpearl tp) || handled.contains(tp.getUUID()))
                continue;
            if (eyes.distanceToSqr(tp.position()) > range * range)
                continue;
            if (!(tp.getOwner() instanceof Player owner) || !isEnemy(owner))
                continue;
            Landing land = simulate(tp);
            if (land == null || land.ticks > MAX_LAND_TICKS)
                continue;
            if (!planCatch(land.pos))
                continue;
            pearl = tp;
            target = owner;
            landing = land.pos;
            ticksToLand = land.ticks;
            waitTicks = activeTicks = landTicks = escapeTicks = clickMisses = 0;
            placeRoll = placeDelay.getValue().getRandomInRange();
            chargeRoll = chargeDelay.getValue().getRandomInRange();
            detonateRoll = detonateDelay.getValue().getRandomInRange();
            state = State.WAIT;
            return;
        }
    }

    private void tickWait() {
        if (target != null && (target.isRemoved() || !isEnemy(target))) {
            abort(null, true);
            return;
        }
        if (++waitTicks > WAIT_TIMEOUT) {
            abort(null, true);
            return;
        }
        if (pearl != null && !pearl.isRemoved()) {
            Landing land = simulate(pearl);
            if (land == null) {
                abort(null, true);
                return;
            }
            ticksToLand = land.ticks;
            if (land.pos.distanceTo(landing) > 2.5) {
                abort(null, false);
                return;
            }
            if (land.ticks <= requiredTicks())
                begin();
        } else if (target != null && target.position().distanceTo(landing) <= 3.0) {
            begin();
        } else {
            abort(null, true);
        }
    }

    /** Ticks the whole place, cover, charge sequence needs, so it starts just in time. */
    private int requiredTicks() {
        int t = 4 + Math.max(1, (int) Math.round(placeRoll / 50.0));
        if (safeAnchor.getValue() && coverPos != null)
            t += 3;
        t += Math.max(1, (int) Math.round(chargeRoll / 50.0));
        return t + 5;
    }

    private boolean inWindow() {
        return ticksToLand >= 0 && ticksToLand <= requiredTicks() + 8;
    }

    private void begin() {
        resetStep();
        clickMisses = 0;
        BlockState s = mc.level.getBlockState(anchorPos);
        if (s.is(Blocks.RESPAWN_ANCHOR)) {
            if (safeAnchor.getValue() && coverPos != null)
                state = State.SAFE_COVER;
            else if (s.getValue(RespawnAnchorBlock.CHARGE) > 0)
                toDetonate();
            else
                state = State.CHARGE_ANCHOR;
        } else {
            state = State.PLACE_ANCHOR;
        }
    }

    private void preAim(EventSilentRotation event) {
        Vec3 point = !mc.level.getBlockState(anchorPos).is(Blocks.RESPAWN_ANCHOR) ? anchorHitVec
                : (safeAnchor.getValue() && coverHitVec != null ? coverHitVec : Vec3.atCenterOf(anchorPos));
        if (point == null || mc.gui.screen() != null || mc.player.isUsingItem())
            return;
        float[] rots = RotationUtils.rotationsTo(mc.player.getEyePosition(1f), point);
        if (Math.abs(RotationUtils.getYawDifference(mc.player.getYRot(), rots[0])) >= aimFov.getValue().getInput())
            return;
        applyRotation(event, rots);
    }

    /** Fills aim, clickBlock, clickResult and clickItem for the current step, or moves the state on. */
    private void planClick() {
        clickItem = null;
        clickResult = null;
        switch (state) {
            case PLACE_ANCHOR -> {
                if (mc.level.getBlockState(anchorPos).is(Blocks.RESPAWN_ANCHOR)) {
                    afterAnchorPlaced();
                    return;
                }
                if (!placeable(anchorPos)) {
                    abort("the anchor spot got blocked", true);
                    return;
                }
                if (findSlot(Items.RESPAWN_ANCHOR) == -1) {
                    abort("no respawn anchor in hotbar", true);
                    return;
                }
                aim = anchorHitVec;
                clickBlock = anchorSupport;
                clickResult = anchorPos;
                clickItem = Items.RESPAWN_ANCHOR;
            }
            case SAFE_COVER -> {
                Item cover = coverItem();
                if (coverPos == null || coverSupport == null || coverHitVec == null
                        || !mc.level.getBlockState(coverPos).canBeReplaced() || findSlot(cover) == -1) {
                    toCharge();
                    return;
                }
                aim = coverHitVec;
                clickBlock = coverSupport;
                clickResult = coverPos;
                clickItem = cover;
            }
            case CHARGE_ANCHOR -> {
                BlockState s = mc.level.getBlockState(anchorPos);
                if (!s.is(Blocks.RESPAWN_ANCHOR)) {
                    missClick();
                    return;
                }
                if (s.getValue(RespawnAnchorBlock.CHARGE) > 0) {
                    toDetonate();
                    return;
                }
                if (findSlot(Items.GLOWSTONE) == -1) {
                    abort("no glowstone in hotbar", true);
                    return;
                }
                aim = Vec3.atCenterOf(anchorPos);
                clickBlock = anchorPos;
                clickItem = Items.GLOWSTONE;
            }
            case DETONATE -> {
                aim = Vec3.atCenterOf(anchorPos);
                clickBlock = anchorPos;
            }
            default -> {
            }
        }
    }

    /** Detonate bookkeeping; returns false when this tick should not click. */
    private boolean tickDetonate() {
        BlockState s = mc.level.getBlockState(anchorPos);
        if (!s.is(Blocks.RESPAWN_ANCHOR)) {
            missClick();
            return false;
        }
        if (s.getValue(RespawnAnchorBlock.CHARGE) <= 0) {
            toCharge();
            return false;
        }
        Landing land = pearl != null && !pearl.isRemoved() ? simulate(pearl) : null;
        ticksToLand = land != null ? land.ticks : 0;
        boolean landed = pearlLanded();
        if (!landed) {
            if (++landTicks > LAND_TIMEOUT)
                abort("the pearl never landed", true);
            return false;
        }
        if (target != null && target.position().distanceTo(Vec3.atCenterOf(anchorPos)) > 4.5 && ++escapeTicks > 10) {
            abort("they got away from the anchor", true);
            return false;
        }
        if (target != null) {
            if (target.position().distanceTo(Vec3.atCenterOf(anchorPos)) > 4.0)
                return false;
            if (damageTick.getValue() && target.hurtTime > 0)
                return false;
        }
        double self = selfDamage(Vec3.atCenterOf(anchorPos), null);
        float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        if (self > maxSelfDamage.getValue().getInput()) {
            abort("self damage too high", true);
            return false;
        }
        if (antiSuicide.getValue() && self >= hp - 1f) {
            abort("would have killed us", true);
            return false;
        }
        return true;
    }

    private boolean pearlLanded() {
        if (pearl == null || pearl.isRemoved())
            return true;
        Landing land = simulate(pearl);
        return land == null || land.ticks <= 1;
    }

    private void advanceAfterClick() {
        lastProgress = System.currentTimeMillis();
        switch (state) {
            case PLACE_ANCHOR -> afterAnchorPlaced();
            case SAFE_COVER -> toCharge();
            case CHARGE_ANCHOR -> toDetonate();
            case DETONATE -> finish();
            default -> {
            }
        }
    }

    private void afterAnchorPlaced() {
        resetStep();
        if (safeAnchor.getValue() && coverPos != null)
            state = State.SAFE_COVER;
        else
            toCharge();
    }

    private void toCharge() {
        state = State.CHARGE_ANCHOR;
        resetStep();
    }

    private void toDetonate() {
        state = State.DETONATE;
        landTicks = escapeTicks = 0;
        resetStep();
    }

    private void resetStep() {
        clickMisses = 0;
        stepStart = lastProgress = System.currentTimeMillis();
    }

    private double currentDelay() {
        return switch (state) {
            case SAFE_COVER -> placeRoll;
            case CHARGE_ANCHOR -> chargeRoll;
            case DETONATE -> detonateRoll;
            default -> 0;
        };
    }

    private void missClick() {
        if (++clickMisses >= 3)
            abort("lost the click line", true);
    }

    private void finish() {
        if (pearl != null)
            handled.add(pearl.getUUID());
        restoreHotbar();
        reset();
    }

    private void abort(String reason, boolean forget) {
        if (reason != null) {
            long now = System.currentTimeMillis();
            if (now - lastNotice > 3000) {
                NotificationManager.show(new Notification(NotificationType.INFO, getName(), "Stopped: " + reason, 1));
                lastNotice = now;
            }
        }
        if (forget && pearl != null)
            handled.add(pearl.getUUID());
        restoreHotbar();
        reset();
    }

    private void reset() {
        state = State.IDLE;
        pearl = null;
        target = null;
        landing = null;
        ticksToLand = -1;
        anchorPos = anchorSupport = coverPos = coverSupport = null;
        anchorHitVec = coverHitVec = null;
        aim = null;
        clickBlock = clickResult = null;
        clickItem = null;
        clickMisses = activeTicks = waitTicks = landTicks = escapeTicks = 0;
        active = false;
        if (handled.size() > 256)
            handled.clear();
    }

    // ---- planning ----

    private record Landing(Vec3 pos, int ticks) {}

    private record Face(BlockPos support, Vec3 hitVec) {}

    /** Steps the pearl forward (drag 0.99, gravity 0.03) until it hits a block. */
    private static Landing simulate(ThrownEnderpearl tp) {
        double x = tp.getX(), y = tp.getY(), z = tp.getZ();
        Vec3 v = tp.getDeltaMovement();
        double vx = v.x, vy = v.y, vz = v.z;
        for (int i = 1; i <= SIM_TICKS; i++) {
            Vec3 from = new Vec3(x, y, z);
            x += vx;
            y += vy;
            z += vz;
            vx *= 0.99;
            vz *= 0.99;
            vy = vy * 0.99 - 0.03;
            BlockHitResult hit = mc.level.clip(new ClipContext(from, new Vec3(x, y, z), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, tp));
            if (hit.getType() == HitResult.Type.BLOCK)
                return new Landing(hit.getLocation(), i);
            if (y < mc.level.getMinY() - 1)
                break;
        }
        return null;
    }

    /** Picks the best anchor spot (and optional cover) around the landing point; false when nothing qualifies. */
    private boolean planCatch(Vec3 land) {
        BlockPos feet = BlockPos.containing(land);
        BlockPos head = feet.above();
        Vec3 eyes = mc.player.getEyePosition(1f);
        float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        Item coverItem = coverItem();
        boolean haveCover = findSlot(coverItem) != -1;
        BlockState coverState = coverBlock().defaultBlockState();
        double bestScore = -Double.MAX_VALUE;
        boolean found = false;

        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos pos = feet.offset(dx, dy, dz);
                    if (pos.equals(feet) || pos.equals(head))
                        continue;
                    Vec3 centre = Vec3.atCenterOf(pos);
                    if (eyes.distanceToSqr(centre) > REACH * REACH)
                        continue;
                    boolean existing = mc.level.getBlockState(pos).is(Blocks.RESPAWN_ANCHOR);
                    Face face = null;
                    if (!existing) {
                        if (!placeable(pos))
                            continue;
                        face = supportFace(pos, eyes);
                        if (face == null)
                            continue;
                    }
                    double dmg = targetDamage(land, centre);
                    if (dmg < minDamage.getValue().getInput())
                        continue;
                    double self = selfDamage(centre, null);
                    if (antiSuicide.getValue() && self >= hp * 2f)
                        continue;
                    BlockPos bestCover = null;
                    Face bestCoverFace = null;
                    double finalSelf = self;
                    if (safeAnchor.getValue() && haveCover && self > 6.0) {
                        double lowest = self;
                        for (int[] o : COVER_OFFSETS) {
                            BlockPos c = pos.offset(o[0], o[1], o[2]);
                            if (c.equals(feet) || c.equals(head) || !placeable(c))
                                continue;
                            Face cf = supportFace(c, eyes);
                            if (cf == null)
                                continue;
                            double covered = selfDamage(centre, c);
                            if (covered < lowest && self - covered >= 2.0) {
                                lowest = covered;
                                bestCover = c;
                                bestCoverFace = cf;
                            }
                        }
                        if (bestCover != null)
                            finalSelf = lowest;
                    }
                    if (finalSelf > maxSelfDamage.getValue().getInput() || (antiSuicide.getValue() && finalSelf >= hp - 1f))
                        continue;
                    double score = dmg - 0.5 * land.distanceTo(centre) + (existing ? 3.0 : 0.0);
                    if (score > bestScore) {
                        bestScore = score;
                        found = true;
                        anchorPos = pos.immutable();
                        anchorSupport = face != null ? face.support : null;
                        anchorHitVec = face != null ? face.hitVec : null;
                        coverPos = bestCover;
                        coverSupport = bestCoverFace != null ? bestCoverFace.support : null;
                        coverHitVec = bestCoverFace != null ? bestCoverFace.hitVec : null;
                    }
                }
            }
        }
        if (!found)
            anchorPos = anchorSupport = coverPos = coverSupport = null;
        return found;
    }

    private static boolean placeable(BlockPos pos) {
        return mc.level.getBlockState(pos).canBeReplaced()
                && mc.level.isUnobstructed(Blocks.RESPAWN_ANCHOR.defaultBlockState(), pos, CollisionContext.empty());
    }

    /** The closest solid neighbour face within reach to place against. */
    private static Face supportFace(BlockPos pos, Vec3 eyes) {
        BlockPos best = null;
        Vec3 bestVec = null;
        double bestDist = Double.MAX_VALUE;
        for (Direction d : DIRECTIONS) {
            BlockPos n = pos.relative(d);
            if (mc.level.getBlockState(n).canBeReplaced())
                continue;
            Direction o = d.getOpposite();
            Vec3 v = Vec3.atCenterOf(n).add(o.getStepX() * 0.5, o.getStepY() * 0.5, o.getStepZ() * 0.5);
            double dist = eyes.distanceToSqr(v);
            if (dist > SUPPORT_REACH_SQ || dist >= bestDist)
                continue;
            bestDist = dist;
            best = n;
            bestVec = v;
        }
        return best == null ? null : new Face(best, bestVec);
    }

    /** Pre-armour anchor damage to someone standing at the landing point. */
    private static double targetDamage(Vec3 land, Vec3 anchor) {
        double d = land.distanceTo(anchor) / ANCHOR_RADIUS;
        if (d > 1)
            return 0;
        int seen = 0;
        for (double h : new double[]{0.2, 1.0, 1.7}) {
            HitResult hit = mc.level.clip(new ClipContext(anchor, land.add(0, h, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() == HitResult.Type.MISS)
                seen++;
        }
        double impact = (1 - d) * (seen / 3.0);
        return (impact * impact + impact) / 2 * 7 * ANCHOR_RADIUS + 1;
    }

    /** Anchor damage to us with armour and difficulty roughly applied, optionally with a cover block in place. */
    private static double selfDamage(Vec3 anchor, BlockPos cover) {
        AABB box = mc.player.getBoundingBox();
        double d = Math.sqrt(mc.player.distanceToSqr(anchor)) / ANCHOR_RADIUS;
        if (d > 1)
            return 0;
        BlockPos anchorBlock = BlockPos.containing(anchor);
        double[] xs = {box.minX, box.maxX}, ys = {box.minY, box.maxY}, zs = {box.minZ, box.maxZ};
        int seen = 0, total = 0;
        for (double x : xs)
            for (double y : ys)
                for (double z : zs) {
                    total++;
                    if (clear(anchor, new Vec3(x, y, z), anchorBlock, cover))
                        seen++;
                }
        total++;
        if (clear(anchor, box.getCenter(), anchorBlock, cover))
            seen++;
        double impact = (1 - d) * (seen / (double) total);
        double dmg = (impact * impact + impact) / 2 * 7 * ANCHOR_RADIUS + 1;
        Difficulty difficulty = mc.level.getDifficulty();
        if (difficulty == Difficulty.PEACEFUL)
            return 0;
        if (difficulty == Difficulty.EASY)
            dmg = Math.min(dmg / 2 + 1, dmg);
        else if (difficulty == Difficulty.HARD)
            dmg *= 1.5;
        return dmg * (1 - Math.min(20, mc.player.getArmorValue()) / 25.0);
    }

    /** Walks the ray in short steps; the anchor itself is gone when it blows, the cover counts as solid. */
    private static boolean clear(Vec3 from, Vec3 to, BlockPos ignore, BlockPos solid) {
        Vec3 delta = to.subtract(from);
        double len = delta.length();
        int steps = Math.max(1, (int) Math.ceil(len / 0.3));
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockPos last = null;
        for (int i = 1; i < steps; i++) {
            double t = i / (double) steps;
            m.set(from.x + delta.x * t, from.y + delta.y * t, from.z + delta.z * t);
            if (m.equals(last))
                continue;
            last = m.immutable();
            if (m.equals(ignore))
                continue;
            if (m.equals(solid))
                return false;
            if (!mc.level.getBlockState(m).getCollisionShape(mc.level, m).isEmpty())
                return false;
        }
        return true;
    }

    // ---- helpers ----

    private boolean isEnemy(Player p) {
        return p != mc.player && p.isAlive()
                && !Arsenic.getArsenic().getFriendManager().isFriend(p)
                && !AntiBot.isBot(p);
    }

    private Block coverBlock() {
        return safeBlock.getValue() == CoverBlock.Glowstone ? Blocks.GLOWSTONE : Blocks.OBSIDIAN;
    }

    private Item coverItem() {
        return coverBlock().asItem();
    }

    private void applyRotation(EventSilentRotation event, float[] rots) {
        event.setYaw(rots[0]);
        event.setPitch(rots[1]);
        event.setSpeed(180f);
        event.setPreventDuplicateLook(true);
        event.setMovementFix(SilentRotationManager.MovementFix.SILENT);
    }

    private void click(BlockHitResult hit) {
        if (mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit).consumesAction())
            PlayerUtils.swingItem();
    }

    private static int findSlot(Item item) {
        for (int i = 0; i < 9; i++)
            if (mc.player.getInventory().getItem(i).is(item))
                return i;
        return -1;
    }

    private static int firstNonGlowstone() {
        for (int i = 0; i < 9; i++)
            if (!mc.player.getInventory().getItem(i).is(Items.GLOWSTONE))
                return i;
        return -1;
    }

    private int detonateSlot() {
        if (restoreSlot != -1) {
            ItemStack s = mc.player.getInventory().getItem(restoreSlot);
            if (!s.is(Items.GLOWSTONE))
                return restoreSlot;
        }
        return firstNonGlowstone();
    }

    private void selectSlot(int slot) {
        if (restoreSlot == -1)
            restoreSlot = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(slot);
    }

    private void restoreHotbar() {
        if (restoreSlot != -1 && mc.player != null && swapBack.getValue())
            mc.player.getInventory().setSelectedSlot(restoreSlot);
        restoreSlot = -1;
    }
}
