package arsenic.module.impl.blatant;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPlayerJoinWorld;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.notifications.Notification;
import arsenic.notifications.NotificationManager;
import arsenic.notifications.NotificationType;
import arsenic.utils.minecraft.HotbarUse;
import arsenic.utils.rotations.SilentRotationManager;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * One-shot mace setup: throws an ender pearl straight up, then a moment later a wind charge under you, so the burst
 * launches you up to catch your own pearl high in the sky. Turns itself off when done.
 */
@ModuleInfo(name = "AutoPearlCatch", description = "Throws a pearl and a wind charge straight up to catch the pearl", category = ModuleCategory.COMBAT, tier = ModuleTier.BLATANT)
public class AutoPearlCatch extends Module {

    /** Extra time to wait for our pearl to show up before giving up, in ms. */
    private static final long PEARL_TIMEOUT = 1500;
    /** How close the sent pitch must be to the wanted one before throwing, in degrees. */
    private static final float AIM_TOLERANCE = 1.5f;

    private enum Stage { PEARL, WAIT, CHARGE, DONE }

    public final DoubleProperty windChargeDelay = new DoubleProperty("Wind Charge Delay", new DoubleValue(50, 300, 120, 5));
    public final DoubleProperty movingDelay = new DoubleProperty("Moving Delay", new DoubleValue(50, 300, 80, 5));
    public final BooleanProperty legitRotate = new BooleanProperty("Legit Rotate", false);
    public final DoubleProperty smoothing = new DoubleProperty("Smoothing", new DoubleValue(0.1, 2, 0.5, 0.05));

    private final HotbarUse swap = new HotbarUse();
    private Stage stage = Stage.PEARL;
    private long pearlTime;
    private float yaw;
    private float pitch;
    private boolean aiming;
    private Level level;

    public AutoPearlCatch() {
        smoothing.setVisible(legitRotate::getValue);
    }

    @Override
    public boolean isSwappingHotbar() {
        return swap.active();
    }

    @Override
    protected void onEnable() {
        stage = Stage.PEARL;
        aiming = false;
        pearlTime = 0;
        level = mc.level;
        if (mc.player == null)
            return;
        if (pearlSlot() == -1) {
            stop("no ender pearl in hotbar");
        } else if (chargeSlot() == -1) {
            stop("no wind charge in hotbar");
        }
    }

    @Override
    protected void onDisable() {
        swap.release();
        aiming = false;
        stage = Stage.PEARL;
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
        switch (stage) {
            case DONE -> setEnabled(false);
            case WAIT -> {
                boolean moving = mc.player.getDeltaMovement().x != 0 || mc.player.getDeltaMovement().z != 0;
                double delay = (moving ? movingDelay : windChargeDelay).getValue().getInput();
                if (System.currentTimeMillis() - pearlTime >= delay)
                    stage = Stage.CHARGE;
            }
            case CHARGE -> {
                if (ownPearl() == null && System.currentTimeMillis() - pearlTime > maxDelay() + PEARL_TIMEOUT)
                    stop("pearl not found");
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
        yaw = mc.player.getYRot();
        if (stage == Stage.PEARL) {
            pitch = -90f;
        } else {
            if (ownPearl() == null)
                return;
            // lean the charge forward a touch with forward speed so the burst follows the pearl
            Vec3 velocity = mc.player.getDeltaMovement();
            double rad = Math.toRadians(yaw);
            float forward = (float) (velocity.x * -Math.sin(rad) + velocity.z * Math.cos(rad));
            pitch = -90f + forward * 1.6f;
        }
        aiming = true;
        event.setYaw(yaw);
        event.setPitch(pitch);
        event.setSpeed(legitRotate.getValue() ? (float) (30 * smoothing.getValue().getInput()) : 180f);
        event.setPreventDuplicateLook(true);
        event.setMovementFix(SilentRotationManager.MovementFix.SILENT);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onPost = event -> {
        if (!aiming)
            return;
        aiming = false;
        if (legitRotate.getValue()) {
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
            if (throwFrom(slot, event.getYaw(), event.getPitch())) {
                pearlTime = System.currentTimeMillis();
                stage = Stage.WAIT;
            }
        } else if (stage == Stage.CHARGE) {
            int slot = chargeSlot();
            if (slot == -1) {
                stop("no wind charge in hotbar");
                return;
            }
            if (throwFrom(slot, event.getYaw(), event.getPitch()))
                stage = Stage.DONE;
        }
    };

    /** Uses the item in the slot with the rotation the server gets this tick, so the use packet matches it. */
    private boolean throwFrom(int slot, float sentYaw, float sentPitch) {
        float realYaw = mc.player.getYRot(), realPitch = mc.player.getXRot();
        swap.switchOnce(slot);
        mc.player.setYRot(sentYaw);
        mc.player.setXRot(sentPitch);
        boolean accepted;
        try {
            ItemStack stack = mc.player.getMainHandItem();
            InteractionResult result = mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            accepted = result.consumesAction();
            if (result instanceof InteractionResult.Success success
                    && success.swingSource() == InteractionResult.SwingSource.PREDICTED)
                mc.player.swing(InteractionHand.MAIN_HAND, stack.getInteractAnimation(), false);
        } finally {
            mc.player.setYRot(realYaw);
            mc.player.setXRot(realPitch);
        }
        return accepted;
    }

    /** Holding right click or using an item: wait so we never swap mid-use. */
    private boolean busy() {
        return mc.player.isUsingItem() || mc.options.keyUse.isDown();
    }

    private double maxDelay() {
        return Math.max(windChargeDelay.getValue().getInput(), movingDelay.getValue().getInput());
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
