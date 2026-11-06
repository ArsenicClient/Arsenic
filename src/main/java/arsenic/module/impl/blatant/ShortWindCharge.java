package arsenic.module.impl.blatant;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
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
import arsenic.utils.minecraft.HotbarUse;
import arsenic.utils.rotations.SilentRotationManager;
import arsenic.utils.timer.MSTimer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Bind it to a key: turning it on looks down, throws a wind charge at your feet (jumping with it for a short boost),
 * looks back up, puts the old slot back and turns itself off.
 */
@ModuleInfo(name = "ShortWindCharge", description = "Throws a wind charge at your feet, then turns itself off", category = ModuleCategory.COMBAT, tier = ModuleTier.BLATANT)
public class ShortWindCharge extends Module {

    public enum Mode {
        /** Turns your real camera down and back up, with a random wait between each step. */
        Legit,
        /** Looks down server-side only and throws in one go. */
        Silent
    }

    private enum Stage { SWITCH, AIM, RETURN, SWITCH_BACK, DONE }

    /** Degrees a tick the camera turns at Smoothing 1. */
    private static final float BASE_SPEED = 40f;
    private static final float AIMED = 1.5f;

    public final EnumProperty<Mode> mode = new EnumProperty<>("Mode", Mode.Legit);
    public final RangeProperty delay = new RangeProperty("Delay (ms)", new RangeValue(0, 200, 30, 50, 1));
    public final BooleanProperty switchBack = new BooleanProperty("Switch Back", true);
    public final BooleanProperty autoJump = new BooleanProperty("Auto Jump", true);
    public final DoubleProperty pitch = new DoubleProperty("Pitch", new DoubleValue(30, 90, 40, 1));
    public final DoubleProperty smoothing = new DoubleProperty("Smoothing", new DoubleValue(0.1, 2, 0.5, 0.1));

    private final HotbarUse swap = new HotbarUse();
    private final MSTimer timer = new MSTimer();
    private long nextDelay;
    private Stage stage = Stage.DONE;
    private float startPitch;
    private float throwPitch;

    @Override
    public boolean isSwappingHotbar() {
        return swap.active();
    }

    @Override
    protected void onEnable() {
        if (mc.player == null || mc.gameMode == null || findCharge() == -1) {
            setEnabled(false);
            return;
        }
        startPitch = mc.player.getXRot();
        throwPitch = Mth.clamp((float) pitch.getValue().getInput() - 1f + ThreadLocalRandom.current().nextFloat() * 2f, -90f, 90f);
        stage = Stage.SWITCH;
        reroll();
    }

    @Override
    protected void onDisable() {
        swap.release();
        stage = Stage.DONE;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (mc.gameMode == null || mc.gui.screen() != null || !mc.isWindowActive())
            return;
        boolean silent = mode.getValue() == Mode.Silent;

        switch (stage) {
            case SWITCH -> {
                if (!silent && timer.getTime() < nextDelay)
                    return;
                if (mc.player.isUsingItem())
                    return;
                if (isCharge(mc.player.getMainHandItem())) {
                    next();
                    return;
                }
                int slot = findCharge();
                if (slot == -1) {
                    setEnabled(false);
                    return;
                }
                // without Switch Back the charge just stays selected, so there is nothing to put back
                if (switchBack.getValue())
                    swap.select(slot);
                else
                    mc.player.getInventory().setSelectedSlot(slot);
                next();
            }
            case SWITCH_BACK -> {
                if (!silent && timer.getTime() < nextDelay)
                    return;
                swap.release();
                setEnabled(false);
            }
            case DONE -> setEnabled(false);
            default -> {
            }
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        if (stage != Stage.AIM && stage != Stage.RETURN)
            return;
        if (mode.getValue() == Mode.Silent) {
            // returning is the rotation manager's job once nothing asks for a pitch
            if (stage != Stage.AIM)
                return;
            event.setPitch(throwPitch);
            event.setSpeed(180f);
            event.setPreventDuplicateLook(true);
        } else {
            if (stage == Stage.AIM && timer.getTime() < nextDelay)
                return;
            if (stage == Stage.RETURN && timer.getTime() < nextDelay)
                return;
            event.setPitch(stage == Stage.AIM ? throwPitch : startPitch);
            event.setSpeed((float) smoothing.getValue().getInput() * BASE_SPEED);
        }
        event.setMovementFix(SilentRotationManager.MovementFix.SILENT);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onPost = event -> {
        if (stage != Stage.AIM && stage != Stage.RETURN)
            return;
        boolean silent = mode.getValue() == Mode.Silent;
        // Legit turns the real camera with the committed rotation
        if (!silent && event.isModified())
            mc.player.setXRot(event.getPitch());

        if (stage == Stage.RETURN) {
            if (Math.abs(event.getPitch() - startPitch) <= AIMED)
                next();
            return;
        }
        if (!silent && timer.getTime() < nextDelay)
            return;
        if (Math.abs(event.getPitch() - throwPitch) > AIMED)
            return;
        ItemStack held = mc.player.getMainHandItem();
        if (!isCharge(held) || mc.player.getCooldowns().isOnCooldown(held)) {
            stage = Stage.SWITCH_BACK;
            reroll();
            return;
        }
        if (autoJump.getValue() && mc.player.onGround())
            mc.player.jumpFromGround();
        throwAt(event.getYaw(), event.getPitch());
        if (silent) {
            stage = Stage.SWITCH_BACK;
            reroll();
        } else {
            next();
        }
    };

    /** Uses the charge with the committed rotation, so the packet carries the angle the server saw. */
    private void throwAt(float yaw, float pitch) {
        float realYaw = mc.player.getYRot();
        float realPitch = mc.player.getXRot();
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        try {
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        } finally {
            mc.player.setYRot(realYaw);
            mc.player.setXRot(realPitch);
        }
    }

    private void next() {
        stage = Stage.values()[Math.min(stage.ordinal() + 1, Stage.DONE.ordinal())];
        reroll();
    }

    private void reroll() {
        timer.reset();
        nextDelay = (long) delay.getValue().getRandomInRange();
    }

    private int findCharge() {
        return HotbarUse.find(s -> isCharge(s) && !mc.player.getCooldowns().isOnCooldown(s));
    }

    private static boolean isCharge(ItemStack stack) {
        return stack.is(Items.WIND_CHARGE);
    }
}
