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
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.ContainerUtils;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.rotations.SilentRotationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * Puts a rail and a TNT minecart on the block you are looking at, lights a fire in the arrow's path and shoots a
 * loaded crossbow through it, so the burning arrow sets the cart off. Turns itself off when it is done.
 */
@ModuleInfo(name = "XbowCart", description = "Places a TNT minecart and detonates it with a flaming crossbow shot", category = ModuleCategory.COMBAT, tier = ModuleTier.BLATANT)
public class XbowCart extends Module {

    private enum State {
        IDLE, SWITCH_RAIL, SWITCH_CART, SWITCH_FIRE, SWITCH_XBOW, FIRE
    }

    private static final Set<Item> RAILS = Set.of(Items.RAIL, Items.POWERED_RAIL, Items.DETECTOR_RAIL, Items.ACTIVATOR_RAIL);
    /** Crossbow arrow speed in blocks per tick. */
    private static final double ARROW_SPEED = 3.15;
    private static final float RELEASE_TOLERANCE = 1.5f;
    private static final int TIMEOUT_TICKS = 80;

    public final DoubleProperty switchDelay = new DoubleProperty("Switch Delay", new DoubleValue(0, 20, 2, 1));
    public final BooleanProperty silentAim = new BooleanProperty("Silent Aim", true);
    public final DoubleProperty silentAimFov = new DoubleProperty("Silent Aim FOV", new DoubleValue(0, 180, 180, 5));
    public final BooleanProperty render = new BooleanProperty("Render", true);

    private State state = State.IDLE;
    private int cartX, cartY, cartZ;
    /** Where the fire goes, on the arrow's path to the cart. */
    private BlockPos firePos;
    private int restoreSlot = -1;
    private int wait;
    private int railTries;
    private int ticks;
    private int aimTicks;
    /** The shot rotation that was checked to pass through the fire and hit the cart. */
    private float[] shot;
    /** The rotation requested this tick. */
    private float[] aim;

    private final BlockPos.MutableBlockPos scan = new BlockPos.MutableBlockPos();

    public XbowCart() {
        silentAimFov.setVisible(silentAim::getValue);
    }

    @Override
    public boolean isSwappingHotbar() {
        return state != State.IDLE;
    }

    @Override
    protected void onEnable() {
        reset();
        if (mc.player == null || mc.level == null) {
            setEnabled(false);
            return;
        }
        restoreSlot = mc.player.getInventory().getSelectedSlot();
        if (crossbowSlot() == -1 || railSlot() == -1 || !hasCart() || flintSlot() == -1) {
            PlayerUtils.addWaterMarkedMessageToChat("XbowCart needs a loaded crossbow, a rail, a TNT minecart and flint and steel.");
            reset();
            setEnabled(false);
            return;
        }
        state = State.SWITCH_RAIL;
    }

    @Override
    protected void onDisable() {
        reset();
    }

    private void reset() {
        state = State.IDLE;
        firePos = null;
        restoreSlot = -1;
        wait = 0;
        railTries = 0;
        ticks = 0;
        aimTicks = 0;
        shot = null;
        aim = null;
    }

    private void abort() {
        if (isEnabled())
            setEnabled(false);
        else
            reset();
    }

    private void finish() {
        if (restoreSlot != -1)
            select(restoreSlot);
        abort();
    }

    // ---------------------------------------------------------------- aim

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        aim = null;
        if (mc.level == null || state == State.IDLE)
            return;
        computeAim();
        if (aim == null || !silentAim.getValue())
            return;
        event.setYaw(aim[0]);
        event.setPitch(aim[1]);
        event.setSpeed(180f);
        event.setMovementFix(SilentRotationManager.MovementFix.SILENT);
    };

    private void computeAim() {
        float fov = (float) silentAimFov.getValue().getInput();
        if (state == State.SWITCH_XBOW || state == State.FIRE) {
            float[] rot = shotRotation();
            if (rot != null && passesFire(rot[0], rot[1], firePos)) {
                shot = rot;
                if (withinFov(rot, fov))
                    aim = rot;
            }
            return;
        }
        Vec3 look = null;
        if (state == State.SWITCH_CART)
            look = new Vec3(cartX + 0.5, cartY + 0.5, cartZ + 0.5);
        else if (state == State.SWITCH_FIRE && firePos != null)
            look = Vec3.atCenterOf(firePos);
        if (look != null) {
            float[] rot = RotationUtils.rotationsTo(mc.player.getEyePosition(1f), look);
            if (withinFov(rot, fov))
                aim = rot;
        }
    }

    private boolean withinFov(float[] rot, float fov) {
        return Math.abs(RotationUtils.getYawDifference(rot[0], mc.player.getYRot())) <= fov;
    }

    private boolean serverAimed(EventSilentRotation.Post event) {
        if (aim == null)
            return true;
        return Math.abs(RotationUtils.getYawDifference(event.getPrevYaw(), aim[0])) <= RELEASE_TOLERANCE
                && Math.abs(event.getPrevPitch() - aim[1]) <= RELEASE_TOLERANCE;
    }

    // ---------------------------------------------------------------- state machine

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onPost = event -> {
        if (mc.level == null) {
            abort();
            return;
        }
        if (state == State.IDLE)
            return;
        if (++ticks > TIMEOUT_TICKS) {
            abort();
            return;
        }
        switch (state) {
            case SWITCH_RAIL -> {
                int rail = railSlot();
                if (rail == -1) {
                    abort();
                    return;
                }
                if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                        || hit.getDirection() != Direction.UP) {
                    if (++railTries > 10)
                        abort();
                    return;
                }
                BlockPos base = hit.getBlockPos();
                cartX = base.getX();
                cartY = base.getY() + 1;
                cartZ = base.getZ();
                if (!mc.level.getBlockState(base.above()).canBeReplaced() || !inReach(cartX + 0.5, cartY + 0.5, cartZ + 0.5)) {
                    abort();
                    return;
                }
                select(rail);
                interact(new BlockHitResult(new Vec3(cartX + 0.5, base.getY() + 1.0, cartZ + 0.5), Direction.UP, base, false));
                wait = (int) switchDelay.getValue().getInput();
                state = State.SWITCH_CART;
            }
            case SWITCH_CART -> {
                if (wait > 0) {
                    wait--;
                    return;
                }
                int cart = cartSlot();
                if (cart == -1) {
                    abort();
                    return;
                }
                // a cart pulled down from the inventory arrives a tick later
                if (!mc.player.getInventory().getItem(cart).is(Items.TNT_MINECART))
                    return;
                if (!inReach(cartX + 0.5, cartY + 0.5, cartZ + 0.5)) {
                    abort();
                    return;
                }
                select(cart);
                interact(new BlockHitResult(new Vec3(cartX + 0.5, cartY + 0.125, cartZ + 0.5), Direction.UP,
                        new BlockPos(cartX, cartY, cartZ), false));
                wait = (int) switchDelay.getValue().getInput();
                state = State.SWITCH_FIRE;
            }
            case SWITCH_FIRE -> {
                if (wait > 0) {
                    wait--;
                    return;
                }
                if (firePos == null) {
                    firePos = findFirePos();
                    if (firePos == null) {
                        abort();
                        return;
                    }
                }
                int flint = flintSlot();
                if (flint == -1) {
                    abort();
                    return;
                }
                BlockPos below = firePos.below();
                select(flint);
                interact(new BlockHitResult(new Vec3(below.getX() + 0.5, below.getY() + 1.0, below.getZ() + 0.5), Direction.UP, below, false));
                wait = (int) switchDelay.getValue().getInput();
                state = State.SWITCH_XBOW;
            }
            case SWITCH_XBOW -> {
                if (wait > 0) {
                    wait--;
                    return;
                }
                int xbow = crossbowSlot();
                if (xbow == -1) {
                    abort();
                    return;
                }
                select(xbow);
                aimTicks = 0;
                state = State.FIRE;
            }
            case FIRE -> {
                if (silentAim.getValue() && shot == null) {
                    abort();
                    return;
                }
                if (!isLoadedCrossbow(mc.player.getMainHandItem())) {
                    int xbow = crossbowSlot();
                    if (xbow == -1) {
                        abort();
                        return;
                    }
                    select(xbow);
                    return;
                }
                // a crossbow fires on use, so the shot rotation has to be on the server already
                if (!serverAimed(event) && aimTicks++ < 5)
                    return;
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                finish();
            }
            default -> {
            }
        }
    };

    // ---------------------------------------------------------------- ballistics

    /** Aim point above the cart that makes up for the arrow's drop over the distance. */
    private Vec3 aimPoint() {
        Vec3 eyes = mc.player.getEyePosition(1f);
        Vec3 cart = new Vec3(cartX + 0.5, cartY + 0.45, cartZ + 0.5);
        int flightTicks = (int) Math.ceil(eyes.distanceTo(cart) / ARROW_SPEED);
        return cart.add(0, 0.05 * flightTicks * (flightTicks + 1) / 2.0, 0);
    }

    private float[] shotRotation() {
        Vec3 eyes = mc.player.getEyePosition(1f);
        Vec3 target = aimPoint();
        double dx = target.x - eyes.x;
        double dy = target.y - (eyes.y - 0.1);
        double dz = target.z - eyes.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 1.0E-6)
            return null;
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.asin(dy / dist));
        return new float[]{yaw, pitch};
    }

    /** Initial arrow velocity for a rotation, including the shooter's motion. */
    private double[] velocity(float yaw, float pitch) {
        double yawRad = Math.toRadians(yaw), pitchRad = Math.toRadians(pitch);
        double vx = -Math.sin(yawRad) * Math.cos(pitchRad);
        double vy = -Math.sin(pitchRad);
        double vz = Math.cos(yawRad) * Math.cos(pitchRad);
        double len = Math.sqrt(vx * vx + vy * vy + vz * vz);
        vx = vx / len * ARROW_SPEED;
        vy = vy / len * ARROW_SPEED;
        vz = vz / len * ARROW_SPEED;
        Vec3 motion = mc.player.getDeltaMovement();
        vx += motion.x;
        vz += motion.z;
        if (!mc.player.onGround())
            vy += motion.y;
        return new double[]{vx, vy, vz};
    }

    /** The first free block on the shot's path to the cart that can hold fire, or null if the path reaches the cart first. */
    private BlockPos findFirePos() {
        float[] rot = shotRotation();
        if (rot == null)
            return null;
        double[] v = velocity(rot[0], rot[1]);
        double vx = v[0], vy = v[1], vz = v[2];
        Vec3 eyes = mc.player.getEyePosition(1f);
        double x = eyes.x, y = eyes.y - 0.1, z = eyes.z;
        AABB self = mc.player.getBoundingBox().inflate(0.3);
        for (int i = 0; i < 60; i++) {
            double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
            int steps = Math.max(1, (int) Math.ceil(speed / 0.25));
            for (int s = 0; s < steps; s++) {
                x += vx / steps;
                y += vy / steps;
                z += vz / steps;
                scan.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
                if (scan.getX() == cartX && scan.getZ() == cartZ && (scan.getY() == cartY || scan.getY() == cartY + 1))
                    return null;
                if (self.intersects(new AABB(scan)))
                    continue;
                if (inReach(scan.getX() + 0.5, scan.getY(), scan.getZ() + 0.5)
                        && mc.level.getBlockState(scan).canBeReplaced()
                        && mc.level.getBlockState(scan.below()).isFaceSturdy(mc.level, scan.below(), Direction.UP))
                    return scan.immutable();
            }
            vx *= 0.99;
            vz *= 0.99;
            vy = vy * 0.99 - 0.05;
        }
        return null;
    }

    /** Whether a shot with this rotation flies through the fire block and then into the cart without hitting a wall. */
    private boolean passesFire(float yaw, float pitch, BlockPos fire) {
        double[] v = velocity(yaw, pitch);
        double vx = v[0], vy = v[1], vz = v[2];
        Vec3 eyes = mc.player.getEyePosition(1f);
        double x = eyes.x, y = eyes.y - 0.1, z = eyes.z;
        AABB cart = new AABB(cartX + 0.01, cartY + 0.0625, cartZ + 0.01, cartX + 0.99, cartY + 0.7625, cartZ + 0.99);
        AABB fireBox = fire != null ? new AABB(fire) : null;
        BlockPos cartPos = new BlockPos(cartX, cartY, cartZ);
        boolean burning = false;
        for (int i = 0; i < 60; i++) {
            Vec3 from = new Vec3(x, y, z);
            double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
            int steps = Math.max(1, (int) Math.ceil(speed / 0.25));
            for (int s = 0; s < steps; s++) {
                x += vx / steps;
                y += vy / steps;
                z += vz / steps;
                if (!burning && fireBox != null && fireBox.contains(x, y, z))
                    burning = true;
                if (cart.contains(x, y, z))
                    return burning;
            }
            BlockHitResult hit = mc.level.clip(new ClipContext(from, new Vec3(x, y, z), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() == HitResult.Type.BLOCK && !hit.getBlockPos().equals(cartPos))
                return false;
            if (y < cartY - 3)
                return false;
            vx *= 0.99;
            vz *= 0.99;
            vy = vy * 0.99 - 0.05;
        }
        return false;
    }

    // ---------------------------------------------------------------- render

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRender = event -> {
        if (!render.getValue() || state == State.IDLE || state == State.SWITCH_RAIL || mc.level == null)
            return;
        int main = ThemeManager.getMainColor();
        RenderUtils.renderBox(new AABB(cartX, cartY, cartZ, cartX + 1, cartY + 0.125, cartZ + 1),
                RenderUtils.withAlpha(main, 170), true, true);
        if (firePos != null)
            RenderUtils.renderBlock(firePos, RenderUtils.withAlpha(ThemeManager.getDarkerColor(), 90), true, false);
    };

    // ---------------------------------------------------------------- inventory

    private void select(int slot) {
        if (slot >= 0 && slot < 9 && slot != mc.player.getInventory().getSelectedSlot())
            mc.player.getInventory().setSelectedSlot(slot);
    }

    private void interact(BlockHitResult hit) {
        if (mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit).consumesAction())
            PlayerUtils.swingItem();
    }

    private int railSlot() {
        for (int i = 0; i < 9; i++)
            if (RAILS.contains(mc.player.getInventory().getItem(i).getItem()))
                return i;
        return -1;
    }

    private int flintSlot() {
        for (int i = 0; i < 9; i++)
            if (mc.player.getInventory().getItem(i).is(Items.FLINT_AND_STEEL))
                return i;
        return -1;
    }

    private int crossbowSlot() {
        for (int i = 0; i < 9; i++)
            if (isLoadedCrossbow(mc.player.getInventory().getItem(i)))
                return i;
        return -1;
    }

    /** A crossbow loaded with an arrow (a firework would not light on fire). */
    private static boolean isLoadedCrossbow(ItemStack stack) {
        if (!stack.is(Items.CROSSBOW))
            return false;
        ChargedProjectiles loaded = stack.get(DataComponents.CHARGED_PROJECTILES);
        return loaded != null && (loaded.contains(Items.ARROW) || loaded.contains(Items.SPECTRAL_ARROW));
    }

    private int hotbarCart() {
        for (int i = 0; i < 9; i++)
            if (mc.player.getInventory().getItem(i).is(Items.TNT_MINECART))
                return i;
        return -1;
    }

    private int mainInventoryCart() {
        for (int i = 9; i < 36; i++)
            if (mc.player.getInventory().getItem(i).is(Items.TNT_MINECART))
                return i;
        return -1;
    }

    private int emptyHotbarSlot() {
        int current = mc.player.getInventory().getSelectedSlot();
        for (int i = 0; i < 9; i++)
            if (i != current && mc.player.getInventory().getItem(i).isEmpty())
                return i;
        return -1;
    }

    private boolean hasCart() {
        return hotbarCart() != -1 || (mainInventoryCart() != -1 && emptyHotbarSlot() != -1);
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
        ContainerUtils.swap(from, to);
        return to;
    }

    private boolean inReach(double x, double y, double z) {
        double range = mc.player.blockInteractionRange();
        return mc.player.getEyePosition(1f).distanceToSqr(x, y, z) <= range * range;
    }

    @Override
    public String getHudInfo() {
        return state == State.IDLE ? null : state.name().replace("SWITCH_", "").toLowerCase();
    }
}
