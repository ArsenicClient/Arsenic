package arsenic.module.impl.blatant;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPlayerJoinWorld;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.injection.accessor.IMixinMultiPlayerGameMode;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.notifications.Notification;
import arsenic.notifications.NotificationManager;
import arsenic.notifications.NotificationType;
import arsenic.utils.minecraft.HotbarUse;
import arsenic.utils.rotations.SilentRotationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * One-shot mace setup: throws a pearl forward at an angle and a wind charge right behind it, so the burst carries you
 * along to catch your own pearl. Offhand mode puts the wind charge in your offhand first so both throws go out with
 * no slot change between them. Turns itself off when done.
 */
@ModuleInfo(name = "FastPearlCatch", description = "Pearl and wind charge back-to-back to catch your own pearl", category = ModuleCategory.COMBAT, tier = ModuleTier.BLATANT)
public class FastPearlCatch extends Module {

    /** How long to wait for our pearl to show up before giving up, in ms. */
    private static final long PEARL_TIMEOUT = 1500;
    /** How close the sent pitch must be to the wanted one before throwing, in degrees. */
    private static final float AIM_TOLERANCE = 1.5f;
    /** Forward speed leans the wind charge down by this much per block a tick, so the burst follows the pearl. */
    private static final float FORWARD_LEAN = 1.6f;

    public enum Mode { Swap, Offhand }

    private enum Stage { PREP, PEARL, CHARGE, RESTORE, DONE }

    public final EnumProperty<Mode> mode = new EnumProperty<>("Mode", Mode.Swap);
    public final DoubleProperty pearlAngle = new DoubleProperty("Pearl Angle", new DoubleValue(0, 90, 48, 1));
    public final DoubleProperty chargeAngle = new DoubleProperty("Wind Charge Angle", new DoubleValue(0, 90, 40, 1));
    public final DoubleProperty offhandPearlAngle = new DoubleProperty("Offhand Pearl Angle", new DoubleValue(0, 90, 70, 1));
    public final DoubleProperty offhandChargeAngle = new DoubleProperty("Offhand Wind Charge Angle", new DoubleValue(0, 90, 66, 1));
    public final BooleanProperty swapBack = new BooleanProperty("Swap Back", true);
    public final BooleanProperty legitRotate = new BooleanProperty("Legit Rotate", false);
    public final DoubleProperty smoothing = new DoubleProperty("Smoothing", new DoubleValue(0.1, 2, 0.5, 0.05));

    private final HotbarUse swap = new HotbarUse();
    private Stage stage = Stage.PREP;
    private Mode running = Mode.Swap;
    private long pearlTime;
    private float yaw;
    private float pitch;
    private boolean aiming;
    private Level level;
    /** The hotbar slot held at enable, put back at the end in Offhand mode. */
    private int originalSlot = -1;
    /** The hotbar slot whose wind charges went to the offhand, or -1 if the offhand already had them. */
    private int offhandFrom = -1;
    /** Ticks until the busy grace after letting go of right click runs out. */
    private int useGrace;

    public FastPearlCatch() {
        pearlAngle.setVisible(() -> mode.getValue() == Mode.Swap);
        chargeAngle.setVisible(() -> mode.getValue() == Mode.Swap);
        offhandPearlAngle.setVisible(() -> mode.getValue() == Mode.Offhand);
        offhandChargeAngle.setVisible(() -> mode.getValue() == Mode.Offhand);
        swapBack.setVisible(() -> mode.getValue() == Mode.Swap);
        legitRotate.setVisible(() -> mode.getValue() == Mode.Swap);
        smoothing.setVisible(() -> mode.getValue() == Mode.Swap && legitRotate.getValue());
    }

    @Override
    public boolean isSwappingHotbar() {
        return swap.active() || (isEnabled() && running == Mode.Offhand && stage != Stage.DONE);
    }

    @Override
    protected void onEnable() {
        running = mode.getValue();
        stage = running == Mode.Offhand ? Stage.PREP : Stage.PEARL;
        aiming = false;
        pearlTime = 0;
        offhandFrom = -1;
        useGrace = 0;
        level = mc.level;
        if (mc.player == null)
            return;
        yaw = mc.player.getYRot();
        originalSlot = mc.player.getInventory().getSelectedSlot();
        if (pearlSlot() == -1) {
            stop("no ender pearl in hotbar");
        } else if (chargeSlot() == -1 && !(running == Mode.Offhand && mc.player.getOffhandItem().is(Items.WIND_CHARGE))) {
            stop("no wind charge");
        }
    }

    @Override
    protected void onDisable() {
        swap.release();
        // left mid-way in Offhand mode: give the offhand item and the slot back
        if (running == Mode.Offhand && mc.player != null && mc.level == level && stage != Stage.PREP && stage != Stage.DONE)
            restoreOffhand();
        aiming = false;
        stage = Stage.PREP;
        offhandFrom = -1;
        originalSlot = -1;
        level = null;
    }

    @EventLink
    public final Listener<EventPlayerJoinWorld> onJoin = event -> {
        if (isEnabled())
            setEnabled(false);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        swap.tick();
        if (mc.level != level) {
            setEnabled(false);
            return;
        }
        if (mc.player.isUsingItem() || mc.options.keyUse.isDown())
            useGrace = 2;
        else if (useGrace > 0)
            useGrace--;

        switch (stage) {
            case DONE -> setEnabled(false);
            case PREP -> {
                if (!busy() && mc.gui.screen() == null)
                    prepareOffhand();
            }
            case CHARGE -> {
                if (ownPearl() == null && System.currentTimeMillis() - pearlTime > PEARL_TIMEOUT)
                    stop("pearl not found");
            }
            case RESTORE -> {
                if (!busy()) {
                    restoreOffhand();
                    stage = Stage.DONE;
                    setEnabled(false);
                }
            }
            default -> {
            }
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        aiming = false;
        if ((stage != Stage.PEARL && stage != Stage.CHARGE) || mc.gui.screen() != null || busy())
            return;
        boolean offhand = running == Mode.Offhand;
        if (stage == Stage.PEARL) {
            pitch = -(float) (offhand ? offhandPearlAngle : pearlAngle).getValue().getInput();
        } else {
            if (ownPearl() == null)
                return;
            pitch = -(float) (offhand ? offhandChargeAngle : chargeAngle).getValue().getInput() + forwardSpeed() * FORWARD_LEAN;
            pitch = Math.max(-90f, Math.min(90f, pitch));
        }
        boolean legit = !offhand && legitRotate.getValue();
        aiming = true;
        event.setYaw(yaw);
        event.setPitch(pitch);
        event.setSpeed(legit ? (float) (30 * smoothing.getValue().getInput()) : 180f);
        event.setPreventDuplicateLook(true);
        event.setMovementFix(SilentRotationManager.MovementFix.SILENT);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onPost = event -> {
        if (!aiming)
            return;
        aiming = false;
        boolean offhand = running == Mode.Offhand;
        if (!offhand && legitRotate.getValue()) {
            // the camera follows what was sent, like turning the mouse
            mc.player.setYRot(event.getYaw());
            mc.player.setXRot(event.getPitch());
        }
        if (Math.abs(event.getPitch() - pitch) > AIM_TOLERANCE)
            return;
        if (stage == Stage.PEARL) {
            int slot = pearlSlot();
            if (slot == -1) {
                stop("no ender pearl in hotbar");
                return;
            }
            // Offhand mode already holds the pearl from the prep tick
            if (offhand ? throwHeld(InteractionHand.MAIN_HAND, event) : throwFrom(slot, event)) {
                pearlTime = System.currentTimeMillis();
                stage = Stage.CHARGE;
            }
        } else if (stage == Stage.CHARGE) {
            if (offhand) {
                // the swap-hands result arrives from the server; wait for it until the timeout
                if (!mc.player.getOffhandItem().is(Items.WIND_CHARGE)
                        || mc.player.getCooldowns().isOnCooldown(mc.player.getOffhandItem())) {
                    if (System.currentTimeMillis() - pearlTime > PEARL_TIMEOUT)
                        stop("no wind charge in offhand");
                    return;
                }
                if (throwHeld(InteractionHand.OFF_HAND, event))
                    stage = Stage.RESTORE;
            } else {
                int slot = chargeSlot();
                if (slot == -1) {
                    stop("no wind charge in hotbar");
                    return;
                }
                if (throwFrom(slot, event))
                    stage = Stage.DONE;
            }
        }
    };

    /** Offhand mode: wind charge into the offhand with the swap-hands action, then hold the pearl. */
    private void prepareOffhand() {
        int pearl = pearlSlot();
        boolean chargeInOffhand = mc.player.getOffhandItem().is(Items.WIND_CHARGE);
        int charge = chargeInOffhand ? -1 : chargeSlot();
        if (pearl == -1 || (!chargeInOffhand && charge == -1)) {
            stop(pearl == -1 ? "no ender pearl in hotbar" : "no wind charge");
            return;
        }
        originalSlot = mc.player.getInventory().getSelectedSlot();
        offhandFrom = -1;
        if (!chargeInOffhand) {
            selectSynced(charge);
            swapHands();
            offhandFrom = charge;
        }
        selectSynced(pearl);
        stage = Stage.PEARL;
    }

    /** Puts the offhand item back where it came from and selects the slot held at the start. */
    private void restoreOffhand() {
        if (offhandFrom != -1) {
            selectSynced(offhandFrom);
            swapHands();
            offhandFrom = -1;
        }
        if (originalSlot != -1)
            selectSynced(originalSlot);
    }

    private void selectSynced(int slot) {
        if (mc.player.getInventory().getSelectedSlot() != slot)
            mc.player.getInventory().setSelectedSlot(slot);
        ((IMixinMultiPlayerGameMode) mc.gameMode).syncCurrentPlayItem();
    }

    private void swapHands() {
        mc.player.connection.send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
    }

    /** Swap mode: switches to the slot for this tick and throws with the rotation the server gets this tick. */
    private boolean throwFrom(int slot, EventSilentRotation.Post event) {
        if (swapBack.getValue())
            swap.switchOnce(slot);
        else
            mc.player.getInventory().setSelectedSlot(slot);
        return throwHeld(InteractionHand.MAIN_HAND, event);
    }

    /** Uses the item in the hand with the sent rotation, so the use packet matches it. */
    private boolean throwHeld(InteractionHand hand, EventSilentRotation.Post event) {
        float realYaw = mc.player.getYRot(), realPitch = mc.player.getXRot();
        mc.player.setYRot(event.getYaw());
        mc.player.setXRot(event.getPitch());
        boolean accepted;
        try {
            ItemStack stack = mc.player.getItemInHand(hand);
            InteractionResult result = mc.gameMode.useItem(mc.player, hand);
            accepted = result.consumesAction();
            if (result instanceof InteractionResult.Success success
                    && success.swingSource() == InteractionResult.SwingSource.PREDICTED)
                mc.player.swing(hand, stack.getInteractAnimation(), false);
        } finally {
            mc.player.setYRot(realYaw);
            mc.player.setXRot(realPitch);
        }
        return accepted;
    }

    /** Holding right click or using an item (or just let go): wait so we never swap mid-use. */
    private boolean busy() {
        return useGrace > 0;
    }

    /** Horizontal speed along the locked yaw, in blocks a tick. */
    private float forwardSpeed() {
        Vec3 velocity = mc.player.getDeltaMovement();
        double rad = Math.toRadians(yaw);
        return (float) (velocity.x * -Math.sin(rad) + velocity.z * Math.cos(rad));
    }

    /** Our youngest ender pearl in the world, or null. */
    private ThrownEnderpearl ownPearl() {
        ThrownEnderpearl best = null;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof ThrownEnderpearl pearl && pearl.getOwner() == mc.player
                    && (best == null || pearl.tickCount < best.tickCount))
                best = pearl;
        }
        return best;
    }

    private int pearlSlot() {
        return HotbarUse.find(s -> s.is(Items.ENDER_PEARL) && !mc.player.getCooldowns().isOnCooldown(s));
    }

    private int chargeSlot() {
        return HotbarUse.find(s -> s.is(Items.WIND_CHARGE) && !mc.player.getCooldowns().isOnCooldown(s));
    }

    private void stop(String reason) {
        NotificationManager.show(new Notification(NotificationType.INFO, getName(), "Stopped: " + reason, 1));
        setEnabled(false);
    }
}
