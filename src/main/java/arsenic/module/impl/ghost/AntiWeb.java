package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventSilentRotation;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.notifications.Notification;
import arsenic.notifications.NotificationManager;
import arsenic.notifications.NotificationType;
import arsenic.utils.minecraft.HotbarUse;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.rotations.SilentRotationManager;
import arsenic.utils.timer.MSTimer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Gets you out of a cobweb with a water bucket: it pours water on top of the web, waits for the flow to wash the web
 * away, then scoops the water back up. In Legit mode you aim the pour yourself; Blatant aims both steps silently.
 */
@ModuleInfo(name = "AntiWeb", description = "Washes away cobwebs you are stuck in with a water bucket", category = ModuleCategory.COMBAT, tier = ModuleTier.LEGIT)
public class AntiWeb extends Module {

    public enum Mode { Legit, Blatant }

    /** Ticks to wait for the water to clear the web before giving up. */
    private static final int WAIT_TICKS = 40;
    /** Scoop attempts before giving up. */
    private static final int SCOOP_TRIES = 6;
    /** How close the committed rotation has to be to the aim point before using the bucket, in degrees. */
    private static final float AIM_TOLERANCE = 4f;

    public final EnumProperty<Mode> mode = new EnumProperty<>("Mode", Mode.Legit);
    public final RangeProperty delay = new RangeProperty("Delay", new RangeValue(0, 500, 50, 100, 10));
    public final BooleanProperty autoSwap = new BooleanProperty("Auto Swap", true);
    public final BooleanProperty swapBack = new BooleanProperty("Swap Back", true);
    public final BooleanProperty bodyCheck = new BooleanProperty("Body Check", true);

    private enum State { IDLE, PLACE, WAIT, SCOOP, BACK }

    private final MSTimer timer = new MSTimer();
    private final MSTimer messageTimer = MSTimer.expired();
    private State state = State.IDLE;
    /** Topmost web of the column the water is poured on. */
    private BlockPos top;
    /** The web we are stuck in. */
    private BlockPos stuck;
    private int restoreSlot = -1;
    private int waited;
    private int scoopTries;
    private double currentDelay;
    /** Point to aim the bucket at this tick, or null when nothing is to be used. */
    private Vec3 aim;

    public AntiWeb() {
        swapBack.setVisible(autoSwap::getValue);
    }

    @Override
    public boolean isSwappingHotbar() {
        return restoreSlot != -1;
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

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        aim = null;
        if (mc.level == null) {
            reset();
            return;
        }
        if (mc.gui.screen() != null || mc.player.isUsingItem())
            return;

        switch (state) {
            case IDLE -> findWeb();
            case WAIT -> {
                if (columnClear()) {
                    next(State.SCOOP);
                } else if (++waited > WAIT_TICKS) {
                    notify("The water never cleared the web.");
                    abort();
                }
            }
            case BACK -> {
                if (!delayPassed())
                    return;
                if (swapBack.getValue())
                    restoreSlot();
                restoreSlot = -1;
                reset();
            }
            case PLACE -> planPlace();
            case SCOOP -> planScoop();
        }

        if (aim == null || mode.getValue() == Mode.Legit && state == State.PLACE)
            return;
        float[] rots = RotationUtils.rotationsTo(mc.player.getEyePosition(1f), aim);
        event.setYaw(rots[0]);
        event.setPitch(rots[1]);
        event.setSpeed(mode.getValue() == Mode.Blatant ? 180f : 40f);
        event.setPreventDuplicateLook(true);
        event.setMovementFix(SilentRotationManager.MovementFix.SILENT);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onPost = event -> {
        if (aim == null || mc.level == null)
            return;
        float yaw = event.getYaw();
        float pitch = event.getPitch();
        if (state == State.PLACE) {
            BlockHitResult hit = rayTrace(yaw, pitch, ClipContext.Fluid.NONE);
            if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(top))
                return;
            if (use(yaw, pitch))
                next(State.WAIT);
        } else if (state == State.SCOOP) {
            BlockHitResult hit = rayTrace(yaw, pitch, ClipContext.Fluid.SOURCE_ONLY);
            boolean onWater = hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(top.above());
            if (onWater && use(yaw, pitch)) {
                next(State.BACK);
            } else if (onWater || aimedAt(yaw, pitch)) {
                if (++scoopTries > SCOOP_TRIES) {
                    notify("Couldn't scoop the water.");
                    next(State.BACK);
                }
            }
        }
    };

    private void findWeb() {
        BlockPos feet = mc.player.blockPosition();
        BlockPos found = touchingWeb(feet);
        if (found == null)
            return;
        stuck = found;
        top = found;
        while (isWeb(top.above()))
            top = top.above();

        if (autoSwap.getValue() ? HotbarUse.find(s -> s.is(Items.WATER_BUCKET)) == -1 && !holding(Items.WATER_BUCKET)
                : !holding(Items.WATER_BUCKET)) {
            notify("No water bucket in hotbar.");
            reset();
        } else if (mc.level.getFluidState(top.above()).isSource()) {
            state = State.WAIT;
            waited = 0;
        } else if (!mc.level.getBlockState(top.above()).canBeReplaced()) {
            notify("No room above the web for water.");
            reset();
        } else {
            next(State.PLACE);
        }
    }

    /** The web at, below or above the feet, or (with Body Check) one beside us that the hitbox overlaps. */
    private BlockPos touchingWeb(BlockPos feet) {
        if (isWeb(feet.below()))
            return feet.below();
        if (isWeb(feet))
            return feet;
        if (isWeb(feet.above()))
            return feet.above();
        if (!bodyCheck.getValue())
            return null;
        AABB box = mc.player.getBoundingBox();
        for (int y = 0; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && z == 0)
                        continue;
                    BlockPos pos = feet.offset(x, y, z);
                    if (isWeb(pos) && box.intersects(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1))
                        return pos;
                }
            }
        }
        return null;
    }

    private void planPlace() {
        if (!delayPassed())
            return;
        if (!isWeb(top)) {
            if (mc.level.getFluidState(top.above()).isSource()) {
                next(State.WAIT);
            } else {
                next(State.BACK);
            }
            return;
        }
        // stepped out of the web before pouring: nothing to do
        if (touchingWeb(mc.player.blockPosition()) == null) {
            abort();
            return;
        }
        if (!ensureHolding(Items.WATER_BUCKET))
            return;
        aim = aimPoint(top);
    }

    private void planScoop() {
        if (!delayPassed())
            return;
        if (!mc.level.getFluidState(top.above()).isSource()) {
            next(State.BACK);
            return;
        }
        if (!ensureHolding(Items.BUCKET))
            return;
        aim = aimPoint(top.above());
    }

    /** The middle of the block, nudged a quarter block along the look so the ray lands on its top. */
    private Vec3 aimPoint(BlockPos pos) {
        double yaw = Math.toRadians(mc.player.getYRot());
        return Vec3.atCenterOf(pos).add(-Math.sin(yaw) * 0.25, 0, Math.cos(yaw) * 0.25);
    }

    private boolean aimedAt(float yaw, float pitch) {
        float[] want = RotationUtils.rotationsTo(mc.player.getEyePosition(1f), aim);
        return Math.abs(RotationUtils.getYawDifference(yaw, want[0])) <= AIM_TOLERANCE
                && Math.abs(pitch - want[1]) <= AIM_TOLERANCE;
    }

    /**
     * True when the item is in the main hand. Otherwise swaps to it (when Auto Swap is on) and returns false, so the
     * use happens on a later tick than the slot change.
     */
    private boolean ensureHolding(Item item) {
        if (holding(item))
            return true;
        if (!autoSwap.getValue())
            return false;
        int slot = HotbarUse.find(s -> s.is(item));
        if (slot == -1) {
            abort();
            return false;
        }
        if (restoreSlot == -1)
            restoreSlot = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(slot);
        return false;
    }

    private boolean holding(Item item) {
        return mc.player.getMainHandItem().is(item);
    }

    /** Uses the main hand item with the committed rotation, so the packet carries the angle the server saw. */
    private boolean use(float yaw, float pitch) {
        float realYaw = mc.player.getYRot();
        float realPitch = mc.player.getXRot();
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        boolean used;
        try {
            used = mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND).consumesAction();
        } finally {
            mc.player.setYRot(realYaw);
            mc.player.setXRot(realPitch);
        }
        if (used)
            PlayerUtils.swingItem();
        return used;
    }

    private BlockHitResult rayTrace(float yaw, float pitch, ClipContext.Fluid fluid) {
        Vec3 eyes = mc.player.getEyePosition(1f);
        Vec3 end = eyes.add(Entity.calculateViewVector(pitch, yaw).scale(mc.player.blockInteractionRange()));
        return mc.level.clip(new ClipContext(eyes, end, ClipContext.Block.OUTLINE, fluid, mc.player));
    }

    /** Whether the water has washed every web from the one we were stuck in up to the top. */
    private boolean columnClear() {
        for (BlockPos pos = stuck; pos.getY() <= top.getY(); pos = pos.above()) {
            if (isWeb(pos))
                return false;
        }
        return true;
    }

    private boolean isWeb(BlockPos pos) {
        return pos != null && mc.level.getBlockState(pos).is(Blocks.COBWEB);
    }

    private boolean delayPassed() {
        return timer.hasTimeElapsed((long) currentDelay);
    }

    private void next(State next) {
        state = next;
        waited = 0;
        scoopTries = 0;
        timer.reset();
        currentDelay = delay.getValue().getRandomInRange();
    }

    private void abort() {
        if (swapBack.getValue())
            restoreSlot();
        restoreSlot = -1;
        reset();
    }

    private void restoreSlot() {
        if (restoreSlot != -1 && mc.player != null)
            mc.player.getInventory().setSelectedSlot(restoreSlot);
        restoreSlot = -1;
    }

    private void reset() {
        state = State.IDLE;
        top = null;
        stuck = null;
        aim = null;
        waited = 0;
        scoopTries = 0;
        timer.reset();
        currentDelay = delay.getValue().getRandomInRange();
    }

    private void notify(String message) {
        if (!messageTimer.hasTimeElapsed(3000))
            return;
        messageTimer.reset();
        NotificationManager.show(new Notification(NotificationType.INFO, getName(), message, 2));
    }
}
