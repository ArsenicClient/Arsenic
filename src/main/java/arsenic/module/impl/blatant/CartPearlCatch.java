package arsenic.module.impl.blatant;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPlayerJoinWorld;
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
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.notifications.Notification;
import arsenic.notifications.NotificationManager;
import arsenic.notifications.NotificationType;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.io.Keys;
import arsenic.utils.minecraft.ContainerUtils;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.render.WorldToScreen;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.rotations.SilentRotationManager;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Watches enemy ender pearls, predicts where they land, and puts a rail and a TNT minecart next to that spot just in
 * time, then sets it off with flint and steel plus a charged crossbow, or with a bow shot timed to the pearl's landing.
 */
@ModuleInfo(name = "CartPearlCatch", description = "Detonates a TNT minecart where enemy pearls land", category = ModuleCategory.COMBAT, tier = ModuleTier.BLATANT)
public class CartPearlCatch extends Module {

    private static final Set<Item> RAILS = Set.of(Items.RAIL, Items.POWERED_RAIL, Items.DETECTOR_RAIL, Items.ACTIVATOR_RAIL);
    private static final double[] AIM_HEIGHTS = {0.0, 0.15, 0.3, 0.5, 0.8, 1.2, 1.8, 2.6};
    private static final double[] BODY_HEIGHTS = {0.2, 1.0, 1.7};
    private static final float CROSSBOW_POWER = 1.05f;
    private static final int SIM_TICKS = 200;
    private static final int MAX_LAND_TICKS = 100;
    private static final int TIMEOUT = 100;
    private static final int MAX_DRAW = 30;
    private static final float RELEASE_TOLERANCE = 1.5f;

    private enum State { IDLE, ARMED, SWITCH_RAIL, SWITCH_CART, SWITCH_FIRE, SWITCH_XBOW, FIRE, SWITCH_BOW, USE_BOW, CHARGE_WAIT, RELEASE }

    public final DoubleProperty scanRange = new DoubleProperty("Scan Range", new DoubleValue(16, 96, 48, 1));
    public final DoubleProperty minDamage = new DoubleProperty("Min Damage", new DoubleValue(0, 40, 15, 0.5));
    public final DoubleProperty maxSelfDamage = new DoubleProperty("Max Self Damage", new DoubleValue(0, 30, 6, 0.5));
    public final BooleanProperty preferXbow = new BooleanProperty("Prefer Xbow", true);
    public final DoubleProperty switchDelay = new DoubleProperty("Switch Delay", new DoubleValue(0, 20, 2, 1));
    public final BooleanProperty silentAim = new BooleanProperty("Silent Aim", true);
    public final DoubleProperty silentAimFov = new DoubleProperty("Silent Aim FOV", new DoubleValue(0, 180, 180, 1));
    public final BooleanProperty render = new BooleanProperty("Render", true);

    private final Set<UUID> handled = new HashSet<>();
    private State state = State.IDLE;
    private UUID pearlId;
    /** The block the cart sits in; the rail goes on the block below. */
    private int cartX, cartY, cartZ;
    private BlockPos fireSpot;
    private boolean xbowMode;
    private int restoreSlot = -1;
    private boolean restorePending;
    private int wait, activeTicks, aimWaits;
    private boolean holdingUse;
    /** Rotation of the planned shot and its flight time in ticks (-1 unknown). */
    private float[] shot;
    private int flightTicks = -1;
    private long lastNotice;

    /** Rotation asked for this tick, for the release check in the post hook. */
    private float[] aim;

    public CartPearlCatch() {
        silentAimFov.setVisible(silentAim::getValue);
    }

    @Override
    public boolean isSwappingHotbar() {
        return state.ordinal() > State.ARMED.ordinal() || restorePending;
    }

    @Override
    protected void onEnable() {
        handled.clear();
        restoreSlot = -1;
        restorePending = false;
        reset();
    }

    @Override
    protected void onDisable() {
        releaseUse();
        if (restoreSlot != -1 && mc.player != null)
            mc.player.getInventory().setSelectedSlot(restoreSlot);
        restoreSlot = -1;
        restorePending = false;
        reset();
        handled.clear();
    }

    @EventLink
    public final Listener<EventPlayerJoinWorld> onJoin = event -> handled.clear();

    // ---------------------------------------------------------------- aim

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        aim = null;
        if (!silentAim.getValue() || mc.level == null || state == State.IDLE || state == State.ARMED)
            return;
        float[] rot = null;
        if (shot != null && (state == State.FIRE || state == State.RELEASE || state == State.SWITCH_XBOW
                || state == State.SWITCH_BOW || state == State.USE_BOW || state == State.CHARGE_WAIT)) {
            rot = shot;
        } else if (state == State.SWITCH_RAIL || state == State.SWITCH_CART) {
            rot = RotationUtils.rotationsTo(mc.player.getEyePosition(1f), new Vec3(cartX + 0.5, cartY + 0.5, cartZ + 0.5));
        } else if (state == State.SWITCH_FIRE && fireSpot != null) {
            rot = RotationUtils.rotationsTo(mc.player.getEyePosition(1f), Vec3.atCenterOf(fireSpot));
        }
        if (rot == null || Math.abs(RotationUtils.getYawDifference(rot[0], mc.player.getYRot())) > silentAimFov.getValue().getInput())
            return;
        aim = rot;
        event.setYaw(rot[0]);
        event.setPitch(rot[1]);
        event.setSpeed(180f);
        event.setMovementFix(SilentRotationManager.MovementFix.SILENT);
    };

    /** Whether the rotation the server already has matches the shot closely enough to fire. */
    private boolean serverAimed(EventSilentRotation.Post event) {
        if (aim == null || shot == null)
            return true;
        return Math.abs(RotationUtils.getYawDifference(event.getPrevYaw(), shot[0])) <= RELEASE_TOLERANCE
                && Math.abs(event.getPrevPitch() - shot[1]) <= RELEASE_TOLERANCE;
    }

    // ---------------------------------------------------------------- state machine (pre-movement)

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onPost = event -> {
        if (mc.level == null)
            return;
        if (state == State.IDLE) {
            // put the hotbar back a tick after the last action, never in the same tick as a use
            if (restorePending) {
                if (restoreSlot != -1 && !mc.player.isUsingItem())
                    mc.player.getInventory().setSelectedSlot(restoreSlot);
                restoreSlot = -1;
                restorePending = false;
                return;
            }
            if (mc.gui.screen() == null)
                scan();
            return;
        }
        if (++activeTicks > TIMEOUT) {
            abort("timed out");
            return;
        }
        step(event);
    };

    private void step(EventSilentRotation.Post event) {
        switch (state) {
            case ARMED -> {
                ThrownEnderpearl pearl = pearl();
                if (pearl == null) {
                    abort("too late, the pearl already landed");
                    return;
                }
                int left = landTicks(pearl);
                if (xbowMode) {
                    findShot(CROSSBOW_POWER);
                    if (left <= requiredTicks() + Math.max(flightTicks, 0))
                        state = State.SWITCH_RAIL;
                } else if (left <= requiredTicks() + 20) {
                    state = State.SWITCH_RAIL;
                }
            }
            case SWITCH_RAIL -> {
                int rail = railSlot();
                if (rail == -1) {
                    abort("no rail in hotbar");
                    return;
                }
                if (!inReach(cartX + 0.5, cartY + 0.5, cartZ + 0.5)) {
                    abort("placement out of reach");
                    return;
                }
                select(rail);
                interact(new BlockHitResult(new Vec3(cartX + 0.5, cartY, cartZ + 0.5), Direction.UP, new BlockPos(cartX, cartY - 1, cartZ), false));
                wait = delay();
                state = State.SWITCH_CART;
            }
            case SWITCH_CART -> {
                if (wait > 0) {
                    wait--;
                    return;
                }
                int cart = cartSlot();
                if (cart == -1) {
                    abort("no TNT carts");
                    return;
                }
                if (!mc.player.getInventory().getItem(cart).is(Items.TNT_MINECART))
                    return;
                if (!inReach(cartX + 0.5, cartY + 0.5, cartZ + 0.5)) {
                    abort("cart out of reach");
                    return;
                }
                select(cart);
                interact(new BlockHitResult(new Vec3(cartX + 0.5, cartY + 0.125, cartZ + 0.5), Direction.UP, new BlockPos(cartX, cartY, cartZ), false));
                if (xbowMode) {
                    wait = delay();
                    state = State.SWITCH_FIRE;
                } else {
                    state = State.SWITCH_BOW;
                }
            }
            case SWITCH_FIRE -> {
                if (wait > 0) {
                    wait--;
                    return;
                }
                if (fireSpot == null) {
                    fireSpot = findFireSpot();
                    if (fireSpot == null) {
                        abort("no fire spot on the shot path");
                        return;
                    }
                }
                int flint = slotOf(Items.FLINT_AND_STEEL);
                if (flint == -1) {
                    abort("lost the flint and steel");
                    return;
                }
                BlockPos below = fireSpot.below();
                select(flint);
                interact(new BlockHitResult(new Vec3(below.getX() + 0.5, below.getY() + 1.0, below.getZ() + 0.5), Direction.UP, below, false));
                wait = delay();
                state = State.SWITCH_XBOW;
            }
            case SWITCH_XBOW -> {
                if (wait > 0) {
                    wait--;
                    return;
                }
                int xbow = chargedCrossbowSlot();
                if (xbow == -1) {
                    abort("no charged crossbow");
                    return;
                }
                select(xbow);
                aimWaits = 0;
                state = State.FIRE;
            }
            case FIRE -> {
                float[] rot = rotationTo(cartCentre());
                if (rot != null && passesFire(rot[0], rot[1])) {
                    shot = rot;
                    int t = flight(rot[0], rot[1], CROSSBOW_POWER);
                    if (t != -1)
                        flightTicks = t;
                }
                if (shot == null) {
                    abort("no firing line through the fire");
                    return;
                }
                if (!isChargedCrossbow(mc.player.getMainHandItem())) {
                    int xbow = chargedCrossbowSlot();
                    if (xbow == -1) {
                        abort("the crossbow lost its charge");
                        return;
                    }
                    select(xbow);
                    return;
                }
                if (!timeToShoot())
                    return;
                if (!serverAimed(event) && aimWaits++ < 5)
                    return;
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                finish();
            }
            case SWITCH_BOW -> {
                int bow = slotOf(Items.BOW);
                if (bow == -1) {
                    abort("no bow in hotbar");
                    return;
                }
                select(bow);
                state = State.USE_BOW;
            }
            case USE_BOW -> {
                mc.options.keyUse.setDown(true);
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                holdingUse = true;
                state = State.CHARGE_WAIT;
            }
            case CHARGE_WAIT -> {
                if (!mc.options.keyUse.isDown())
                    mc.options.keyUse.setDown(true);
                int used = mc.player.getTicksUsingItem();
                if (used >= 3)
                    findShot(BowItem.getPowerForTime(used));
                if (shot == null && used >= MAX_DRAW) {
                    abort("no firing line at the cart");
                    return;
                }
                if (shot != null && (timeToShoot() || used >= MAX_DRAW)) {
                    aimWaits = 0;
                    state = State.RELEASE;
                }
            }
            case RELEASE -> {
                if (shot == null) {
                    abort("no firing line at the cart");
                    return;
                }
                if ((!timeToShoot() && mc.player.getTicksUsingItem() < MAX_DRAW) || (!serverAimed(event) && aimWaits++ < 5)) {
                    if (!mc.options.keyUse.isDown())
                        mc.options.keyUse.setDown(true);
                    return;
                }
                releaseUse();
                finish();
            }
            default -> {
            }
        }
    }

    /** Ticks the place-and-fire sequence needs before the shot. */
    private int requiredTicks() {
        int d = delay();
        return xbowMode ? 3 * d + 4 : d + 2;
    }

    /** True once an arrow fired now would reach the cart as the pearl lands. */
    private boolean timeToShoot() {
        if (flightTicks < 0)
            return false;
        ThrownEnderpearl pearl = pearl();
        if (pearl == null)
            return true;
        Landing land = simulate(pearl);
        return land == null || land.ticks <= flightTicks + 1;
    }

    private void scan() {
        if (railSlot() == -1)
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
            boolean xbow = preferXbow.getValue() && chargedCrossbowSlot() != -1 && slotOf(Items.FLINT_AND_STEEL) != -1;
            if ((!xbow && slotOf(Items.BOW) == -1) || !hasCart())
                continue;
            xbowMode = xbow;
            BlockPos spot = bestSpot(land.pos);
            if (spot == null)
                continue;
            cartX = spot.getX();
            cartY = spot.getY();
            cartZ = spot.getZ();
            if (!findShot(xbow ? CROSSBOW_POWER : 1f))
                continue;
            pearlId = tp.getUUID();
            activeTicks = 0;
            wait = 0;
            state = State.ARMED;
            return;
        }
    }

    private void finish() {
        if (pearlId != null)
            handled.add(pearlId);
        restorePending = restoreSlot != -1;
        reset();
    }

    private void abort(String reason) {
        long now = System.currentTimeMillis();
        if (reason != null && now - lastNotice > 3000) {
            NotificationManager.show(new Notification(NotificationType.INFO, getName(), "Stopped: " + reason, 1));
            lastNotice = now;
        }
        if (pearlId != null)
            handled.add(pearlId);
        releaseUse();
        restorePending = restoreSlot != -1;
        reset();
    }

    private void reset() {
        state = State.IDLE;
        pearlId = null;
        fireSpot = null;
        shot = null;
        flightTicks = -1;
        wait = activeTicks = aimWaits = 0;
        aim = null;
        if (handled.size() > 256)
            handled.clear();
    }

    private void releaseUse() {
        if (!holdingUse)
            return;
        holdingUse = false;
        mc.options.keyUse.setDown(mc.gui.screen() == null && Keys.isPhysicallyDown(mc.options.keyUse));
        if (mc.player != null && mc.player.isUsingItem())
            mc.gameMode.releaseUsingItem(mc.player);
    }

    // ---------------------------------------------------------------- planning

    private record Landing(Vec3 pos, int ticks) {}

    private ThrownEnderpearl pearl() {
        if (pearlId == null || mc.level == null)
            return null;
        for (Entity e : mc.level.entitiesForRendering())
            if (e instanceof ThrownEnderpearl tp && pearlId.equals(tp.getUUID()))
                return tp;
        return null;
    }

    private int landTicks(ThrownEnderpearl pearl) {
        Landing land = simulate(pearl);
        return land != null ? land.ticks : 0;
    }

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

    /** The cart spot near the landing that hurts them most while keeping our own damage under the limit. */
    private BlockPos bestSpot(Vec3 land) {
        double targetPower = xbowMode ? 6.4 : 6.25;
        double selfPower = xbowMode ? 3.15 : 3.0;
        BlockPos centre = BlockPos.containing(land);
        BlockPos best = null;
        double bestDamage = minDamage.getValue().getInput();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -4; dz <= 4; dz++) {
                    BlockPos support = centre.offset(dx, dy, dz);
                    BlockPos spot = support.above();
                    if (mc.level.getBlockState(support).canBeReplaced() || !mc.level.getBlockState(spot).canBeReplaced()
                            || !mc.level.getBlockState(support).isFaceSturdy(mc.level, support, Direction.UP))
                        continue;
                    if (!inReach(spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5))
                        continue;
                    Vec3 cart = new Vec3(spot.getX() + 0.5, spot.getY() + 0.0625, spot.getZ() + 0.5);
                    if (selfDamage(cart, selfPower) > maxSelfDamage.getValue().getInput())
                        continue;
                    double dmg = targetDamage(land, cart, targetPower);
                    if (dmg >= bestDamage) {
                        bestDamage = dmg;
                        best = spot;
                    }
                }
            }
        }
        return best;
    }

    /** Pre-armour blast damage to someone standing at the landing point. */
    private static double targetDamage(Vec3 land, Vec3 cart, double power) {
        double radius = power * 2.0;
        double d = land.distanceTo(cart) / radius;
        if (d > 1)
            return 0;
        int seen = 0;
        for (double h : BODY_HEIGHTS) {
            HitResult hit = mc.level.clip(new ClipContext(cart, land.add(0, h, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() == HitResult.Type.MISS)
                seen++;
        }
        double impact = (1 - d) * (seen / 3.0);
        return (impact * impact + impact) / 2 * 7 * radius + 1;
    }

    /** Blast damage to us with armour and difficulty roughly applied. */
    private static double selfDamage(Vec3 cart, double power) {
        double radius = power * 2.0;
        double d = Math.sqrt(mc.player.distanceToSqr(cart)) / radius;
        if (d > 1)
            return 0;
        AABB box = mc.player.getBoundingBox();
        double[] xs = {box.minX, box.maxX}, ys = {box.minY, box.maxY}, zs = {box.minZ, box.maxZ};
        int seen = 0, total = 0;
        for (double x : xs)
            for (double y : ys)
                for (double z : zs) {
                    total++;
                    if (clear(cart, new Vec3(x, y, z)))
                        seen++;
                }
        total++;
        if (clear(cart, box.getCenter()))
            seen++;
        double impact = (1 - d) * (seen / (double) total);
        double dmg = (impact * impact + impact) / 2 * 7 * radius + 1;
        Difficulty difficulty = mc.level.getDifficulty();
        if (difficulty == Difficulty.PEACEFUL)
            return 0;
        if (difficulty == Difficulty.EASY)
            dmg = Math.min(dmg / 2 + 1, dmg);
        else if (difficulty == Difficulty.HARD)
            dmg *= 1.5;
        return dmg * (1 - Math.min(20, mc.player.getArmorValue()) / 25.0);
    }

    private static boolean clear(Vec3 a, Vec3 b) {
        return mc.level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player)).getType() == HitResult.Type.MISS;
    }

    private Vec3 cartCentre() {
        return new Vec3(cartX + 0.5, cartY + 0.45, cartZ + 0.5);
    }

    /** Looks for a rotation whose arrow hits the cart, trying aim points above it for a lob; stores it in shot. */
    private boolean findShot(float power) {
        Vec3 centre = cartCentre();
        for (double h : AIM_HEIGHTS) {
            float[] rot = rotationTo(centre.add(0, h, 0));
            if (rot == null)
                continue;
            int t = flight(rot[0], rot[1], power);
            if (t != -1) {
                shot = rot;
                flightTicks = t;
                return true;
            }
        }
        return false;
    }

    /** Yaw and pitch from the arrow spawn point (just below the eyes) to the point. */
    private float[] rotationTo(Vec3 to) {
        Vec3 eyes = mc.player.getEyePosition(1f);
        double dx = to.x - eyes.x, dy = to.y - (eyes.y - 0.1), dz = to.z - eyes.z;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-6)
            return null;
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.asin(dy / len));
        return new float[]{yaw, pitch};
    }

    /** Arrow start velocity for this rotation and power, with our own motion added as vanilla does. */
    private double[] launch(float yaw, float pitch, float power) {
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        double vx = -Math.sin(yr) * Math.cos(pr);
        double vy = -Math.sin(pr);
        double vz = Math.cos(yr) * Math.cos(pr);
        double len = Math.sqrt(vx * vx + vy * vy + vz * vz);
        double speed = Math.min(power, CROSSBOW_POWER) * 3.0;
        vx = vx / len * speed;
        vy = vy / len * speed;
        vz = vz / len * speed;
        Vec3 motion = mc.player.getDeltaMovement();
        vx += motion.x;
        vz += motion.z;
        if (!mc.player.onGround())
            vy += motion.y;
        return new double[]{vx, vy, vz};
    }

    private AABB cartBox() {
        return new AABB(cartX + 0.01, cartY + 0.0625, cartZ + 0.01, cartX + 0.99, cartY + 0.7625, cartZ + 0.99);
    }

    /** Ticks until an arrow with this rotation and power reaches the cart, or -1 when something is in the way. */
    private int flight(float yaw, float pitch, float power) {
        return trace(yaw, pitch, power, false);
    }

    /** Whether a crossbow arrow with this rotation passes through the fire before reaching the cart. */
    private boolean passesFire(float yaw, float pitch) {
        return trace(yaw, pitch, CROSSBOW_POWER, true) != -1;
    }

    private int trace(float yaw, float pitch, float power, boolean needFire) {
        Vec3 eyes = mc.player.getEyePosition(1f);
        double x = eyes.x, y = eyes.y - 0.1, z = eyes.z;
        double[] v = launch(yaw, pitch, power);
        double vx = v[0], vy = v[1], vz = v[2];
        AABB cart = cartBox();
        AABB fire = fireSpot != null ? new AABB(fireSpot) : null;
        boolean throughFire = false;
        BlockPos cartPos = new BlockPos(cartX, cartY, cartZ);
        for (int tick = 1; tick <= 60; tick++) {
            Vec3 from = new Vec3(x, y, z);
            double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
            int steps = Math.max(1, (int) Math.ceil(speed / 0.25));
            for (int s = 0; s < steps; s++) {
                x += vx / steps;
                y += vy / steps;
                z += vz / steps;
                if (fire != null && fire.contains(x, y, z))
                    throughFire = true;
                if (cart.contains(x, y, z))
                    return !needFire || throughFire ? tick : -1;
            }
            BlockHitResult hit = mc.level.clip(new ClipContext(from, new Vec3(x, y, z), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() == HitResult.Type.BLOCK && !hit.getBlockPos().equals(cartPos))
                return -1;
            if (y < cartY - 3)
                return -1;
            vx *= 0.99;
            vz *= 0.99;
            vy = vy * 0.99 - 0.05;
        }
        return -1;
    }

    /** First free block on the crossbow path to the cart that fire can be lit in, so the arrow catches alight. */
    private BlockPos findFireSpot() {
        float[] rot = rotationTo(cartCentre());
        if (rot == null)
            return null;
        Vec3 eyes = mc.player.getEyePosition(1f);
        double x = eyes.x, y = eyes.y - 0.1, z = eyes.z;
        double[] v = launch(rot[0], rot[1], CROSSBOW_POWER);
        double vx = v[0], vy = v[1], vz = v[2];
        BlockPos cartPos = new BlockPos(cartX, cartY, cartZ);
        AABB self = mc.player.getBoundingBox().inflate(0.3);
        for (int tick = 0; tick < 60; tick++) {
            double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
            int steps = Math.max(1, (int) Math.ceil(speed / 0.25));
            for (int s = 0; s < steps; s++) {
                x += vx / steps;
                y += vy / steps;
                z += vz / steps;
                BlockPos pos = BlockPos.containing(x, y, z);
                if (pos.equals(cartPos) || pos.equals(cartPos.above()))
                    return null;
                if (self.intersects(new AABB(pos)))
                    continue;
                BlockPos below = pos.below();
                if (inReach(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5)
                        && mc.level.getBlockState(pos).canBeReplaced()
                        && mc.level.getBlockState(below).isFaceSturdy(mc.level, below, Direction.UP))
                    return pos;
            }
            vx *= 0.99;
            vz *= 0.99;
            vy = vy * 0.99 - 0.05;
        }
        return null;
    }

    // ---------------------------------------------------------------- render

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRenderWorld = event -> {
        if (!render.getValue() || state == State.IDLE)
            return;
        BlockPos cart = new BlockPos(cartX, cartY, cartZ);
        RenderUtils.renderBox(new AABB(cartX, cartY, cartZ, cartX + 1, cartY + 0.125, cartZ + 1),
                RenderUtils.withAlpha(ThemeManager.getMainColor(), 170), true, true);
        RenderUtils.renderBlock(cart, RenderUtils.withAlpha(ThemeManager.getMainColor(), 80), true, false);
        if (fireSpot != null)
            RenderUtils.renderBlock(fireSpot, RenderUtils.withAlpha(ThemeManager.getDarkerColor(), 90), true, true);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRender2D> onRender2D = event -> {
        if (!render.getValue() || state == State.IDLE || mc.level == null)
            return;
        if (mc.gui.screen() != null && !(mc.gui.screen() instanceof ChatScreen))
            return;
        WorldToScreen.Point p = WorldToScreen.project(new Vec3(cartX + 0.5, cartY + 1.3, cartZ + 0.5));
        if (!WorldToScreen.isOnScreen(p))
            return;
        ThrownEnderpearl pearl = pearl();
        String title = "TNT Cart";
        String eta = pearl == null ? "Now" : String.format("%.1fs", landTicks(pearl) / 20f);
        FontRendererExtension<?> fr = HudStyle.label();
        float padX = 6f, padY = 4f, gap = 3f;
        float lineH = fr.getHeight(title);
        float w = Math.max(fr.getWidth(title), fr.getWidth(eta)) + padX * 2f;
        float h = lineH * 2f + gap * 2f + 1f + padY * 2f;
        float x1 = p.x() - w / 2f, y1 = p.y() - h / 2f;
        HudStyle.card(x1, y1, x1 + w, y1 + h, HudStyle.RADIUS_COMPACT);
        fr.drawString(title, p.x(), y1 + padY + lineH / 2f, ThemeManager.getMainColor(), fr.CENTREX, fr.CENTREY);
        float divY = y1 + padY + lineH + gap;
        HudStyle.divider(x1 + padX, x1 + w - padX, divY);
        fr.drawString(eta, p.x(), divY + 1f + gap + lineH / 2f, M3.onSurface(), fr.CENTREX, fr.CENTREY);
    };

    // ---------------------------------------------------------------- helpers

    private boolean isEnemy(Player p) {
        return p != mc.player && p.isAlive()
                && !Arsenic.getArsenic().getFriendManager().isFriend(p)
                && !AntiBot.isBot(p);
    }

    private int delay() {
        return (int) switchDelay.getValue().getInput();
    }

    private void interact(BlockHitResult hit) {
        if (mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit).consumesAction())
            PlayerUtils.swingItem();
    }

    private boolean inReach(double x, double y, double z) {
        double range = mc.player.blockInteractionRange();
        return mc.player.getEyePosition(1f).distanceToSqr(x, y, z) <= range * range;
    }

    private void select(int slot) {
        if (slot < 0 || slot >= 9 || slot == mc.player.getInventory().getSelectedSlot())
            return;
        if (restoreSlot == -1)
            restoreSlot = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(slot);
    }

    private static int slotOf(Item item) {
        for (int i = 0; i < 9; i++)
            if (mc.player.getInventory().getItem(i).is(item))
                return i;
        return -1;
    }

    private static int railSlot() {
        for (int i = 0; i < 9; i++)
            if (RAILS.contains(mc.player.getInventory().getItem(i).getItem()))
                return i;
        return -1;
    }

    private static int mainInventoryCart() {
        for (int i = 9; i < 36; i++)
            if (mc.player.getInventory().getItem(i).is(Items.TNT_MINECART))
                return i;
        return -1;
    }

    private static int emptyHotbarSlot() {
        int current = mc.player.getInventory().getSelectedSlot();
        for (int i = 0; i < 9; i++)
            if (i != current && mc.player.getInventory().getItem(i).isEmpty())
                return i;
        return -1;
    }

    private static boolean hasCart() {
        return slotOf(Items.TNT_MINECART) != -1 || (mainInventoryCart() != -1 && emptyHotbarSlot() != -1);
    }

    /** A hotbar slot holding a TNT cart, pulling one down from the main inventory into an empty slot if needed. */
    private static int cartSlot() {
        int slot = slotOf(Items.TNT_MINECART);
        if (slot != -1)
            return slot;
        int from = mainInventoryCart();
        int to = emptyHotbarSlot();
        if (from == -1 || to == -1)
            return -1;
        ContainerUtils.swap(from, to);
        return to;
    }

    private static int chargedCrossbowSlot() {
        for (int i = 0; i < 9; i++)
            if (isChargedCrossbow(mc.player.getInventory().getItem(i)))
                return i;
        return -1;
    }

    /** A crossbow loaded with an arrow (fireworks do not light a cart). */
    private static boolean isChargedCrossbow(ItemStack stack) {
        if (!stack.is(Items.CROSSBOW))
            return false;
        ChargedProjectiles loaded = stack.get(DataComponents.CHARGED_PROJECTILES);
        return loaded != null && (loaded.contains(Items.ARROW) || loaded.contains(Items.SPECTRAL_ARROW));
    }

    @Override
    public String getHudInfo() {
        return preferXbow.getValue() ? "Xbow" : "Bow";
    }
}
