package arsenic.module.impl.blatant;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventSilentRotation;
import arsenic.gui.themes.ThemeManager;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.io.Keys;
import arsenic.utils.minecraft.ContainerUtils;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.rotations.SilentRotationManager;
import arsenic.utils.timer.MSTimer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * TNT minecart PvP: puts a rail and a TNT cart where your arrow will land after you shoot a bow or crossbow, or at your
 * crosshair on a bind and then fires at it. It can stack several carts on one rail, put a block between you and the
 * cart, and fire a drawn bow at a placed cart on its own. The aim is silent and the bow arc is solved by simulation.
 */
@ModuleInfo(name = "AutoCart", description = "Places rails and TNT minecarts where your arrows land and fires at them", category = ModuleCategory.COMBAT, tier = ModuleTier.BLATANT)
public class AutoCart extends Module {

    public enum Bind {
        None, Middle, Mouse4, Mouse5, R, F, G, V, X, Z, C, Q, Tab, Left_Alt, Caps_Lock
    }

    public enum PlaceMode {
        Trajectory, Crosshair
    }

    private enum State {
        IDLE, SWITCH_RAIL, SWITCH_CART, SWITCH_SAFE, WAIT_BACK, SWITCH_WEAPON, USE_WEAPON, CHARGE_BOW, RELEASE_BOW
    }

    private static final Set<Item> RAILS = Set.of(Items.RAIL, Items.POWERED_RAIL, Items.DETECTOR_RAIL, Items.ACTIVATOR_RAIL);
    private static final Set<Block> RAIL_BLOCKS = Set.of(Blocks.RAIL, Blocks.POWERED_RAIL, Blocks.DETECTOR_RAIL, Blocks.ACTIVATOR_RAIL);
    private static final Set<Item> SAFE_BLOCKS = Set.of(Items.OBSIDIAN, Items.CRYING_OBSIDIAN, Items.NETHERITE_BLOCK,
            Items.DEEPSLATE, Items.COBBLED_DEEPSLATE, Items.ENDER_CHEST, Items.RESPAWN_ANCHOR);
    private static final float CROSSBOW_POWER = 1.0500001f;
    private static final float RELEASE_TOLERANCE = 1.5f;
    private static final float PLACE_TOLERANCE = 8f;

    public final DoubleProperty switchDelay = new DoubleProperty("Switch Delay", new DoubleValue(0, 20, 2, 1));
    public final DoubleProperty bowBackDelay = new DoubleProperty("Bow Back Delay", new DoubleValue(0, 20, 0, 1));
    public final DoubleProperty minCharge = new DoubleProperty("Min Charge", new DoubleValue(1, 20, 1, 1));
    public final DoubleProperty arc = new DoubleProperty("Arc", new DoubleValue(0, 100, 20, 1));
    public final EnumProperty<Bind> triggerBind = new EnumProperty<>("Trigger Bind", Bind.None);
    public final DoubleProperty overload = new DoubleProperty("Overload", new DoubleValue(1, 4, 1, 1));
    public final EnumProperty<Bind> overloadBind = new EnumProperty<>("Overload Bind", Bind.None);
    public final RangeProperty overloadDelay = new RangeProperty("Overload Delay (ms)", new RangeValue(0, 1000, 50, 150, 5));
    public final BooleanProperty overloadHotbarOnly = new BooleanProperty("Overload Hotbar Only", false);
    public final EnumProperty<PlaceMode> placeMode = new EnumProperty<>("Place Mode", PlaceMode.Trajectory);
    public final BooleanProperty onBowShot = new BooleanProperty("On Bow Shot", true);
    public final BooleanProperty autoHit = new BooleanProperty("Auto Hit Placed", true);
    public final DoubleProperty autoHitRange = new DoubleProperty("Auto Hit Range", new DoubleValue(5, 50, 24, 1));
    public final DoubleProperty autoHitMinCharge = new DoubleProperty("Auto Hit Min Charge", new DoubleValue(10, 20, 20, 1));
    public final BooleanProperty switchBack = new BooleanProperty("Switch Back To Bow", true);
    public final BooleanProperty safeBlocks = new BooleanProperty("Safe Blocks", true);
    public final BooleanProperty silentAim = new BooleanProperty("Silent Aim", true);
    public final DoubleProperty silentAimFov = new DoubleProperty("Silent Aim FOV", new DoubleValue(0, 180, 180, 5));
    public final BooleanProperty renderPath = new BooleanProperty("Render Path", true);

    private State state = State.IDLE;
    /** True for a bind-started run that goes on to fire the weapon, false for a placement after a manual shot. */
    private boolean bowMode;
    /** Where the cart goes (the block above the rail's support). */
    private int cartX, cartY, cartZ;
    /** The block the rail is put on, when it came from the arrow prediction rather than the crosshair. */
    private BlockPos landing;
    private int restoreSlot = -1;
    private int wait;
    private int failTicks;
    private boolean crossbow;
    private boolean triggerHeld, overloadHeld;
    private boolean stacking;
    private int placedCarts;
    private boolean awaitingStack;
    private final MSTimer stackTimer = new MSTimer();
    private double stackDelay;
    private int releaseWait;
    /** Overload-bind run: fire first, then stack the carts where the arrow lands. */
    private boolean fireFirst;
    private BlockPos fireTarget;
    /** Our own release/use packet is on its way; do not treat it as a manual shot. */
    private boolean skipPacket;
    private boolean ownShot;
    private MinecartTNT hitCart;
    private boolean releaseOnCart;
    private int aimTicks;

    /** The rotation asked for this tick, and the one that would have to be on the server before a release. */
    private float[] aim;
    private boolean aimForRelease;

    public AutoCart() {
        overloadBind.setVisible(() -> overload.getValue().getInput() > 1);
        overloadHotbarOnly.setVisible(() -> overload.getValue().getInput() > 1);
        overloadDelay.setVisible(() -> overload.getValue().getInput() > 1);
        silentAim.setVisible(() -> placeMode.getValue() == PlaceMode.Trajectory);
        silentAimFov.setVisible(() -> placeMode.getValue() == PlaceMode.Trajectory && silentAim.getValue());
        renderPath.setVisible(() -> placeMode.getValue() == PlaceMode.Trajectory);
        autoHitRange.setVisible(autoHit::getValue);
        autoHitMinCharge.setVisible(autoHit::getValue);
    }

    @Override
    public boolean isSwappingHotbar() {
        return state != State.IDLE;
    }

    @Override
    protected void onEnable() {
        reset();
        triggerHeld = bindDown(triggerBind.getValue());
        overloadHeld = bindDown(overloadBind.getValue());
        hitCart = null;
        releaseOnCart = false;
        skipPacket = false;
        ownShot = false;
    }

    @Override
    protected void onDisable() {
        if (state == State.CHARGE_BOW || state == State.RELEASE_BOW || state == State.USE_WEAPON)
            restoreUseKey();
        reset();
        hitCart = null;
        releaseOnCart = false;
    }

    private void reset() {
        state = State.IDLE;
        bowMode = false;
        restoreSlot = -1;
        wait = 0;
        failTicks = 0;
        crossbow = false;
        landing = null;
        stacking = false;
        placedCarts = 0;
        awaitingStack = false;
        releaseWait = 0;
        fireFirst = false;
        fireTarget = null;
        aimTicks = 0;
    }

    // ---------------------------------------------------------------- manual shots

    @RequiresPlayer
    @EventLink
    public final Listener<EventPacket.OutGoing> onOutgoing = event -> {
        if (!(event.getPacket() instanceof ServerboundPlayerActionPacket) && !(event.getPacket() instanceof ServerboundUseItemPacket))
            return;
        if (skipPacket) {
            skipPacket = false;
            return;
        }
        if (!onBowShot.getValue() || state != State.IDLE || ownShot || mc.level == null)
            return;
        ItemStack held = mc.player.getMainHandItem();
        boolean shot = false;
        float power = 1f;
        if (event.getPacket() instanceof ServerboundPlayerActionPacket action
                && action.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM && held.is(Items.BOW)) {
            int used = mc.player.getTicksUsingItem();
            if (used < 4)
                return;
            shot = true;
            power = BowItem.getPowerForTime(used);
        } else if (event.getPacket() instanceof ServerboundUseItemPacket && isChargedCrossbow(held)) {
            shot = true;
            power = CROSSBOW_POWER;
        }
        if (!shot || railSlot() == -1 || !hasCart())
            return;

        BlockPos land = null;
        if (placeMode.getValue() == PlaceMode.Trajectory) {
            float[] rot = serverRotation();
            land = simulate(rot[0], rot[1], power, Double.NEGATIVE_INFINITY);
            if (land == null || !canRailOn(land))
                return;
        }
        restoreSlot = selected();
        bowMode = false;
        landing = land;
        stacking = overloadActive();
        placedCarts = 0;
        state = State.SWITCH_RAIL;
        wait = 0;
    };

    // ---------------------------------------------------------------- aim

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        aim = null;
        aimForRelease = false;
        if (mc.level == null)
            return;
        computeAim();
        if (aim == null)
            return;
        event.setYaw(aim[0]);
        event.setPitch(aim[1]);
        event.setSpeed(180f);
        event.setMovementFix(SilentRotationManager.MovementFix.SILENT);
    };

    private void computeAim() {
        float fov = (float) silentAimFov.getValue().getInput();

        // a drawn bow and a cart in front of us: aim the arc at the cart
        if (autoHit.getValue() && state == State.IDLE && drawingBow()) {
            MinecartTNT cart = hitCart != null && hitCart.isAlive() ? hitCart : findCart();
            if (cart != null) {
                float[] rot = solveArc(cart.position().add(0, 0.25, 0), 1f);
                if (rot != null && withinFov(rot, fov)) {
                    aim = rot;
                    aimForRelease = true;
                    return;
                }
            }
        }

        // Overload Bind run: shoot at the crosshair block first
        if (bowMode && fireFirst && fireTarget != null
                && (state == State.USE_WEAPON || state == State.CHARGE_BOW || state == State.RELEASE_BOW)) {
            float power = weaponPower();
            float[] rot = power > 0 ? solveArc(Vec3.atCenterOf(fireTarget), power) : null;
            if (rot != null && withinFov(rot, fov)) {
                aim = rot;
                aimForRelease = true;
                return;
            }
        }

        // Trigger Bind run: shoot at the cart we just placed
        if (bowMode && !fireFirst && (state == State.SWITCH_SAFE || state == State.SWITCH_WEAPON
                || state == State.USE_WEAPON || state == State.CHARGE_BOW || state == State.RELEASE_BOW)) {
            float power = weaponPower();
            float[] rot = power > 0 ? solveArc(new Vec3(cartX + 0.5, cartY + 0.5, cartZ + 0.5), power) : null;
            if (rot != null && withinFov(rot, fov)) {
                aim = rot;
                aimForRelease = true;
                return;
            }
        }

        // placing at a predicted landing: look at it
        if (silentAim.getValue() && placeMode.getValue() == PlaceMode.Trajectory && landing != null
                && (state == State.SWITCH_RAIL || state == State.SWITCH_CART)) {
            BlockPos above = landing.above();
            float[] rot = RotationUtils.rotationsTo(mc.player.getEyePosition(1f), new Vec3(above.getX() + 0.5, above.getY() + 0.125, above.getZ() + 0.5));
            if (withinFov(rot, fov))
                aim = rot;
        }
    }

    private boolean withinFov(float[] rot, float fov) {
        return Math.abs(RotationUtils.getYawDifference(rot[0], mc.player.getYRot())) <= fov;
    }

    /** Whether the rotation the server already has (sent last tick) matches the aim closely enough to release. */
    private boolean serverAimed(EventSilentRotation.Post event, float tolerance) {
        if (aim == null)
            return true;
        return Math.abs(RotationUtils.getYawDifference(event.getPrevYaw(), aim[0])) <= tolerance
                && Math.abs(event.getPrevPitch() - aim[1]) <= tolerance;
    }

    // ---------------------------------------------------------------- state machine (pre-movement)

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onPost = event -> {
        if (mc.level == null) {
            reset();
            return;
        }
        boolean trigger = bindDown(triggerBind.getValue());
        boolean triggerPressed = trigger && !triggerHeld;
        triggerHeld = trigger;
        boolean over = bindDown(overloadBind.getValue());
        boolean overPressed = over && !overloadHeld;
        overloadHeld = over;
        if (!mc.options.keyUse.isDown())
            ownShot = false;

        if (state == State.IDLE && mc.gui.screen() == null && (triggerPressed || overPressed))
            startBindRun();

        // auto hit: release a drawn bow once it is aimed at a placed cart
        if (releaseOnCart && state == State.IDLE && drawingBow()) {
            skipPacket = true;
            restoreUseKey();
            if (mc.player.isUsingItem())
                mc.gameMode.releaseUsingItem(mc.player);
            releaseOnCart = false;
            hitCart = null;
            return;
        }
        releaseOnCart = false;
        if (autoHit.getValue() && state == State.IDLE && drawingBow()) {
            hitCart = findCart();
            if (hitCart != null && mc.player.getTicksUsingItem() >= autoHitMinCharge.getValue().getInput()
                    && aim != null && aimForRelease && serverAimed(event, RELEASE_TOLERANCE))
                releaseOnCart = true;
        } else {
            hitCart = null;
        }

        if (state != State.IDLE)
            step(event);
    };

    private void startBindRun() {
        boolean fire = overloadActive();
        boolean hasWeapon = chargedCrossbowSlot() != -1 || bowSlot() != -1;
        if (railSlot() == -1 || !hasCart() || (fire && !hasWeapon))
            return;
        restoreSlot = selected();
        bowMode = true;
        fireFirst = fire;
        fireTarget = fire && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;
        landing = null;
        ownShot = true;
        stacking = overloadActive();
        placedCarts = 0;
        state = fire ? State.SWITCH_WEAPON : State.SWITCH_RAIL;
        wait = 0;
    }

    private void step(EventSilentRotation.Post event) {
        switch (state) {
            case SWITCH_RAIL -> {
                int rail = railSlot();
                if (rail == -1) {
                    reset();
                    return;
                }
                BlockHitResult hit = railHit();
                if (hit == null) {
                    if (++failTicks > 10)
                        reset();
                    return;
                }
                if (!inReach(cartX + 0.5, cartY + 0.5, cartZ + 0.5)) {
                    reset();
                    return;
                }
                if (landing != null && aim != null && !serverAimed(event, PLACE_TOLERANCE) && aimTicks++ < 5)
                    return;
                aimTicks = 0;
                select(rail);
                interact(hit);
                wait = (int) switchDelay.getValue().getInput();
                state = State.SWITCH_CART;
            }
            case SWITCH_CART -> {
                if (wait > 0) {
                    wait--;
                    return;
                }
                if (awaitingStack) {
                    if (stackTimer.getTime() < stackDelay)
                        return;
                    awaitingStack = false;
                }
                int cart = overloadHotbarOnly.getValue() && placedCarts > 0 ? hotbarCart() : cartSlot();
                if (cart == -1) {
                    if (placedCarts > 0)
                        afterPlacing();
                    else
                        reset();
                    return;
                }
                if (!mc.player.getInventory().getItem(cart).is(Items.TNT_MINECART))
                    return;
                if (!inReach(cartX + 0.5, cartY + 0.5, cartZ + 0.5)) {
                    reset();
                    return;
                }
                select(cart);
                interact(new BlockHitResult(new Vec3(cartX + 0.5, cartY + 0.125, cartZ + 0.5), Direction.UP,
                        new BlockPos(cartX, cartY, cartZ), false));
                placedCarts++;
                if ((stacking || overloadActive()) && placedCarts < (int) overload.getValue().getInput()) {
                    stackTimer.reset();
                    stackDelay = overloadDelay.getValue().getRandomInRange();
                    awaitingStack = true;
                    return;
                }
                if (safeBlocks.getValue()) {
                    state = State.SWITCH_SAFE;
                    wait = (int) bowBackDelay.getValue().getInput();
                } else {
                    afterPlacing();
                }
            }
            case SWITCH_SAFE -> {
                if (wait > 0) {
                    wait--;
                    return;
                }
                int block = safeBlockSlot();
                if (block != -1 && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                        && hit.getDirection() == Direction.UP) {
                    BlockPos pos = hit.getBlockPos();
                    Vec3 feet = mc.player.position();
                    double toCart = feet.distanceTo(new Vec3(cartX + 0.5, cartY + 0.5, cartZ + 0.5));
                    double toBlock = feet.distanceTo(Vec3.atCenterOf(pos));
                    if (pos.getY() <= Mth.floor(feet.y) && toBlock < toCart
                            && !RAIL_BLOCKS.contains(mc.level.getBlockState(pos).getBlock())
                            && !mc.level.getBlockState(pos).canBeReplaced()
                            && inReach(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5)) {
                        select(block);
                        interact(hit);
                    }
                }
                afterPlacing();
            }
            case WAIT_BACK -> {
                if (!switchBack.getValue()) {
                    reset();
                    return;
                }
                if (wait > 0) {
                    wait--;
                    return;
                }
                if (restoreSlot != -1)
                    select(restoreSlot);
                reset();
            }
            case SWITCH_WEAPON -> {
                if (wait > 0) {
                    wait--;
                    return;
                }
                int xbow = chargedCrossbowSlot();
                if (xbow != -1) {
                    select(xbow);
                    crossbow = true;
                } else {
                    int bow = bowSlot();
                    if (bow == -1) {
                        reset();
                        return;
                    }
                    select(bow);
                    crossbow = false;
                }
                releaseWait = 0;
                state = State.USE_WEAPON;
            }
            case USE_WEAPON -> {
                if (crossbow) {
                    // a crossbow fires on use, so the aim has to be on the server already
                    if (aim != null && aimForRelease && !serverAimed(event, RELEASE_TOLERANCE) && releaseWait++ < 10)
                        return;
                    ownShot = true;
                    skipPacket = true;
                    float[] rot = serverRotation();
                    mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                    if (fireFirst)
                        placeAtLanding(CROSSBOW_POWER, rot[0], rot[1]);
                    else
                        reset();
                } else {
                    ownShot = true;
                    mc.options.keyUse.setDown(true);
                    mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                    failTicks = 0;
                    state = State.CHARGE_BOW;
                }
            }
            case CHARGE_BOW -> {
                if (!mc.options.keyUse.isDown())
                    mc.options.keyUse.setDown(true);
                if (!mc.player.isUsingItem()) {
                    // nothing to shoot (no arrows) or the draw was cancelled
                    if (++failTicks > 5) {
                        restoreUseKey();
                        reset();
                    }
                    return;
                }
                if (mc.player.getTicksUsingItem() < chargeTicks())
                    return;
                releaseWait = 0;
                state = State.RELEASE_BOW;
            }
            case RELEASE_BOW -> {
                if (aim != null && aimForRelease && !serverAimed(event, RELEASE_TOLERANCE) && releaseWait++ < 10) {
                    if (!mc.options.keyUse.isDown())
                        mc.options.keyUse.setDown(true);
                    return;
                }
                float power = BowItem.getPowerForTime(mc.player.getTicksUsingItem());
                float[] rot = serverRotation();
                restoreUseKey();
                if (mc.player.isUsingItem()) {
                    ownShot = true;
                    skipPacket = true;
                    mc.gameMode.releaseUsingItem(mc.player);
                }
                if (fireFirst)
                    placeAtLanding(power, rot[0], rot[1]);
                else
                    reset();
            }
            default -> {
            }
        }
    }

    /** After the carts (and safe block) are down: shoot them on a Trigger Bind run, otherwise go back to the bow. */
    private void afterPlacing() {
        awaitingStack = false;
        if (bowMode && !fireFirst) {
            wait = (int) switchDelay.getValue().getInput();
            state = State.SWITCH_WEAPON;
        } else {
            wait = (int) bowBackDelay.getValue().getInput();
            state = State.WAIT_BACK;
        }
    }

    /** Overload Bind run, after the shot: put the rail and carts where the arrow is going to land. */
    private void placeAtLanding(float power, float yaw, float pitch) {
        BlockPos land = simulate(yaw, pitch, power, Double.NEGATIVE_INFINITY);
        if (land != null && canRailOn(land)) {
            landing = land;
            stacking = true;
            placedCarts = 0;
            fireFirst = false;
            bowMode = false;
            state = State.SWITCH_RAIL;
            wait = 0;
        } else {
            reset();
        }
    }

    private boolean canRailOn(BlockPos pos) {
        return !hasCartAt(pos.above()) && !hasCartAt(pos) && mc.level.getBlockState(pos.above()).canBeReplaced()
                && inReach(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
    }

    /** The face the rail goes on: the predicted landing if there is one, otherwise the top of the block in the crosshair. */
    private BlockHitResult railHit() {
        if (landing != null) {
            cartX = landing.getX();
            cartY = landing.getY() + 1;
            cartZ = landing.getZ();
            return new BlockHitResult(new Vec3(cartX + 0.5, landing.getY() + 1.0, cartZ + 0.5), Direction.UP, landing, false);
        }
        if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && hit.getDirection() == Direction.UP) {
            BlockPos pos = hit.getBlockPos();
            cartX = pos.getX();
            cartY = pos.getY() + 1;
            cartZ = pos.getZ();
            return hit;
        }
        return null;
    }

    private void interact(BlockHitResult hit) {
        if (mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit).consumesAction())
            PlayerUtils.swingItem();
    }

    private void restoreUseKey() {
        mc.options.keyUse.setDown(mc.gui.screen() == null && Keys.isPhysicallyDown(mc.options.keyUse));
    }

    // ---------------------------------------------------------------- arrow simulation

    /**
     * The block an arrow shot now with this rotation and power (0-1 for a bow, 1.05 for a crossbow) would hit first,
     * or null. Stops once the arrow drops below {@code floorY}.
     */
    private BlockPos simulate(float yaw, float pitch, float power, double floorY) {
        Vec3 eyes = mc.player.getEyePosition(1f);
        double x = eyes.x, y = eyes.y - 0.1, z = eyes.z;
        float yawRad = (float) Math.toRadians(yaw), pitchRad = (float) Math.toRadians(pitch);
        double vx = -Mth.sin(yawRad) * Mth.cos(pitchRad);
        double vy = -Mth.sin(pitchRad);
        double vz = Mth.cos(yawRad) * Mth.cos(pitchRad);
        double len = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (len == 0)
            return null;
        float speed = Mth.clamp(power, 0f, CROSSBOW_POWER) * 3f;
        vx = vx / len * speed;
        vy = vy / len * speed;
        vz = vz / len * speed;
        Vec3 motion = mc.player.getDeltaMovement();
        vx += motion.x;
        vz += motion.z;
        if (!mc.player.onGround())
            vy += motion.y;
        Vec3 from = new Vec3(x, y, z);
        for (int i = 0; i < 300; i++) {
            x += vx;
            y += vy;
            z += vz;
            vx *= 0.99;
            vy *= 0.99;
            vz *= 0.99;
            vy -= 0.05;
            Vec3 to = new Vec3(x, y, z);
            BlockHitResult hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() == HitResult.Type.BLOCK)
                return hit.getBlockPos();
            if (y < -65 || y < floorY)
                break;
            from = to;
        }
        return null;
    }

    /**
     * Yaw and pitch that land an arrow of this power within a block of the target, or null. Arc 0 takes the flattest
     * shot that reaches, 100 the highest lob.
     */
    private float[] solveArc(Vec3 target, float power) {
        Vec3 eyes = mc.player.getEyePosition(1f);
        float yaw = RotationUtils.yawTo(target.x - eyes.x, target.z - eyes.z);
        double floorY = target.y - 6;
        List<float[]> hits = new ArrayList<>();
        double best = Double.MAX_VALUE;
        for (float pitch = -89f; pitch <= 60f; pitch += 0.5f) {
            BlockPos pos = simulate(yaw, pitch, power, floorY);
            if (pos == null)
                continue;
            double d = Vec3.atCenterOf(pos).distanceToSqr(target);
            hits.add(new float[]{pitch, (float) d});
            best = Math.min(best, d);
        }
        if (best > 4.0)
            return null;
        double cutoff = best + 0.25;
        hits.removeIf(h -> h[1] > cutoff);
        // hits run from the highest lob (pitch -89) to the flattest shot; Arc 0 is the flattest
        int last = hits.size() - 1;
        int index = last - Math.min((int) Math.round(arc.getValue().getInput() / 100.0 * last), last);
        float pitch = hits.get(index)[0];
        float bestPitch = pitch;
        for (float p = pitch - 0.5f; p <= pitch + 0.5f; p += 0.05f) {
            BlockPos pos = simulate(yaw, p, power, floorY);
            if (pos == null)
                continue;
            double d = Vec3.atCenterOf(pos).distanceToSqr(target);
            if (d < best) {
                best = d;
                bestPitch = p;
            }
        }
        return new float[]{yaw, bestPitch};
    }

    /** The rotation the server has, which is what an arrow is fired with. */
    private float[] serverRotation() {
        SilentRotationManager rotations = Arsenic.getArsenic().getSilentRotationManager();
        if (rotations != null && rotations.isModified())
            return new float[]{rotations.yaw, rotations.pitch};
        return new float[]{mc.player.getYRot(), mc.player.getXRot()};
    }

    // ---------------------------------------------------------------- render

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRender = event -> {
        if (!renderPath.getValue() || placeMode.getValue() != PlaceMode.Trajectory || mc.level == null)
            return;
        ItemStack held = mc.player.getMainHandItem();
        float power;
        if (held.is(Items.BOW)) {
            if (!mc.player.isUsingItem())
                return;
            power = BowItem.getPowerForTime(mc.player.getTicksUsingItem());
            if (power < 0.05f)
                return;
        } else if (isChargedCrossbow(held)) {
            power = CROSSBOW_POWER;
        } else {
            return;
        }
        float[] rot = serverRotation();
        BlockPos land = simulate(rot[0], rot[1], power, Double.NEGATIVE_INFINITY);
        if (land == null)
            return;
        BlockPos above = land.above();
        boolean placeable = railSlot() != -1 && hasCart() && canRailOn(land);
        int colour = placeable ? ThemeManager.getMainColor() : ThemeManager.getDarkerColor();
        RenderUtils.renderBox(new AABB(above.getX(), above.getY(), above.getZ(), above.getX() + 1, above.getY() + 0.125,
                above.getZ() + 1), RenderUtils.withAlpha(colour, 170), true, true);
        RenderUtils.renderBlock(above, RenderUtils.withAlpha(colour, 90), true, false);
    };

    // ---------------------------------------------------------------- inventory

    private int selected() {
        return mc.player.getInventory().getSelectedSlot();
    }

    private void select(int slot) {
        if (slot >= 0 && slot < 9 && slot != selected())
            mc.player.getInventory().setSelectedSlot(slot);
    }

    private int railSlot() {
        for (int i = 0; i < 9; i++)
            if (RAILS.contains(mc.player.getInventory().getItem(i).getItem()))
                return i;
        return -1;
    }

    private int safeBlockSlot() {
        for (int i = 0; i < 9; i++)
            if (SAFE_BLOCKS.contains(mc.player.getInventory().getItem(i).getItem()))
                return i;
        return -1;
    }

    private int hotbarCart() {
        for (int i = 0; i < 9; i++)
            if (mc.player.getInventory().getItem(i).is(Items.TNT_MINECART))
                return i;
        return -1;
    }

    private boolean hasCart() {
        if (hotbarCart() != -1)
            return true;
        return mainInventoryCart() != -1 && emptyHotbarSlot() != -1;
    }

    private int mainInventoryCart() {
        for (int i = 9; i < 36; i++)
            if (mc.player.getInventory().getItem(i).is(Items.TNT_MINECART))
                return i;
        return -1;
    }

    private int emptyHotbarSlot() {
        int current = selected();
        for (int i = 0; i < 9; i++)
            if (i != current && mc.player.getInventory().getItem(i).isEmpty())
                return i;
        return -1;
    }

    /** A hotbar slot holding a TNT cart, pulling one down from the main inventory into an empty slot if needed. */
    private int cartSlot() {
        int slot = hotbarCart();
        if (slot != -1)
            return slot;
        int from = mainInventoryCart();
        int to = emptyHotbarSlot();
        if (from == -1 || to == -1)
            return -1;
        // inventory menu slots 9-35 are the main inventory, the button is the hotbar index
        ContainerUtils.swap(from, to);
        return to;
    }

    private int bowSlot() {
        for (int i = 0; i < 9; i++)
            if (mc.player.getInventory().getItem(i).is(Items.BOW))
                return i;
        return -1;
    }

    private int chargedCrossbowSlot() {
        for (int i = 0; i < 9; i++)
            if (isChargedCrossbow(mc.player.getInventory().getItem(i)))
                return i;
        return -1;
    }

    private static boolean isChargedCrossbow(ItemStack stack) {
        return stack.is(Items.CROSSBOW) && CrossbowItem.isCharged(stack);
    }

    private boolean drawingBow() {
        return mc.player.isUsingItem() && mc.player.getUseItem().is(Items.BOW);
    }

    private int chargeTicks() {
        return Math.max((int) minCharge.getValue().getInput(), 3);
    }

    /** Power of the weapon about to be fired: 1.05 for a loaded crossbow, the charge target for a bow, or -1. */
    private float weaponPower() {
        if (chargedCrossbowSlot() != -1)
            return CROSSBOW_POWER;
        return bowSlot() != -1 ? BowItem.getPowerForTime(chargeTicks()) : -1f;
    }

    private boolean overloadActive() {
        return overload.getValue().getInput() > 1 && bindDown(overloadBind.getValue());
    }

    // ---------------------------------------------------------------- world

    private boolean inReach(double x, double y, double z) {
        double range = mc.player.blockInteractionRange();
        return mc.player.getEyePosition(1f).distanceToSqr(x, y, z) <= range * range;
    }

    private boolean hasCartAt(BlockPos pos) {
        return !mc.level.getEntitiesOfClass(MinecartTNT.class, new AABB(pos)).isEmpty();
    }

    /** The TNT cart in range closest to where we are looking. */
    private MinecartTNT findCart() {
        double range = autoHitRange.getValue().getInput();
        Vec3 eyes = mc.player.getEyePosition(1f);
        Vec3 look = mc.player.getViewVector(1f);
        MinecartTNT best = null;
        double bestScore = Double.MAX_VALUE;
        for (MinecartTNT cart : mc.level.getEntitiesOfClass(MinecartTNT.class, mc.player.getBoundingBox().inflate(range))) {
            Vec3 to = cart.position().subtract(eyes);
            double dist = to.length();
            if (dist > range || to.x * to.x + to.z * to.z < 1.0E-12)
                continue;
            double angle = Math.acos(Mth.clamp(look.dot(to.normalize()), -1.0, 1.0));
            double score = angle * 8.0 + dist * 0.05;
            if (score < bestScore) {
                bestScore = score;
                best = cart;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- binds

    private static boolean bindDown(Bind bind) {
        if (bind == Bind.None || mc.gui.screen() != null)
            return false;
        int mask;
        switch (bind) {
            case Middle -> mask = 2;
            case Mouse4 -> mask = 8;
            case Mouse5 -> mask = 16;
            default -> mask = 0;
        }
        if (mask != 0)
            return (org.lwjgl.sdl.SDLMouse.SDL_GetMouseState(null, null) & mask) != 0;
        return Keys.isKeyDown(Keys.getKeyIndex(bind.name()));
    }

    @Override
    public String getHudInfo() {
        return placeMode.getValue().name();
    }
}
