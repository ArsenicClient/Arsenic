package arsenic.module.impl.player;

import arsenic.utils.timer.MSTimer;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.ShowWhen;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.effect.MobEffects;
import arsenic.utils.minecraft.ItemUtils;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Throws a splash healing potion at your feet when your health gets low, or keeps throwing the potion in your hand
 * while you hold one. It looks down silently first and throws once the server sees you looking down.
 */
@ModuleInfo(name = "AutoPot", description = "Throws healing potions at your feet", category = ModuleCategory.PLAYER, tier = ModuleTier.LEGIT)
public class AutoPot extends Module {

    public enum Mode {
        Auto("Auto"), WhileHolding("While Holding");

        private final String display;

        Mode(String display) {
            this.display = display;
        }

        @Override
        public String toString() {
            return display;
        }
    }

    /** How close the silent pitch must get to the target before the potion is thrown. */
    private static final float PITCH_TOLERANCE = 3f;
    /** Gives up on a throw that could not rotate in this time. */
    private static final long ROTATE_TIMEOUT_MS = 500;
    /** Keeps looking down this long after a throw so the view does not snap straight back. */
    private static final long HOLD_AFTER_MS = 100;

    public final EnumProperty<Mode> mode = new EnumProperty<>("Mode", Mode.Auto);

    @ShowWhen(property = "Mode", is = "Auto")
    public final DoubleProperty healthThreshold = new DoubleProperty("Health %", new DoubleValue(1, 100, 40, 1));

    public final DoubleProperty delay = new DoubleProperty("Delay (ms)", new DoubleValue(100, 3000, 500, 50));

    public final DoubleProperty pitch = new DoubleProperty("Pitch", new DoubleValue(50, 90, 90, 1));

    private static final boolean HEAL_ONLY = true;

    private final MSTimer throwTimer = MSTimer.expired();
    private final MSTimer rotateTimer = MSTimer.expired();
    private final MSTimer lookDown = MSTimer.expired();
    /** Hotbar slot of the potion waiting to be thrown, or -1. */
    private int pendingSlot = -1;
    private float targetPitch;
    private boolean shouldLookDown;

    @Override
    protected void onDisable() {
        pendingSlot = -1;
        shouldLookDown = false;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (pendingSlot != -1) {
            if (rotateTimer.getTime() > ROTATE_TIMEOUT_MS) pendingSlot = -1;
            return;
        }
        if (mc.gui.screen() != null || !mc.isWindowActive() || mc.gameMode == null) return;
        if (mc.player.isUsingItem()) return;
        if (!throwTimer.finished((long) delay.getValue().getInput())) return;

        int potSlot;
        if (mode.getValue() == Mode.Auto) {
            float healthPct = (mc.player.getHealth() / mc.player.getMaxHealth()) * 100.0f;
            if (healthPct > healthThreshold.getValue().getInput()) return;
            potSlot = findBestPot();
        } else {
            int held = mc.player.getInventory().getSelectedSlot();
            potSlot = isThrowablePot(mc.player.getInventory().getItem(held)) ? held : -1;
        }
        if (potSlot == -1) return;

        pendingSlot = potSlot;
        // A little jitter so every throw is not at exactly the same pitch.
        targetPitch = Math.min(90f, (float) pitch.getValue().getInput() - ThreadLocalRandom.current().nextFloat());
        rotateTimer.reset();
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onUpdate = event -> {
        if (pendingSlot != -1) {
            event.setSpeed(180);
            event.setPitch(targetPitch);
        } else if (shouldLookDown && lookDown.getTime() < HOLD_AFTER_MS) {
            event.setSpeed(180);
            event.setPitch(targetPitch);
        } else {
            shouldLookDown = false;
        }
    };

    /** Runs before the movement packet, once the silent rotation for this tick is settled. */
    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onRotated = event -> {
        if (pendingSlot == -1 || mc.gameMode == null) return;
        if (Math.abs(event.getPitch() - targetPitch) > PITCH_TOLERANCE) return;

        int slot = pendingSlot;
        pendingSlot = -1;
        if (!isThrowablePot(mc.player.getInventory().getItem(slot))) return;

        int oldSlot = mc.player.getInventory().getSelectedSlot();
        float oldYaw = mc.player.getYRot();
        float oldPitch = mc.player.getXRot();
        mc.player.getInventory().setSelectedSlot(slot);
        // The use-item packet carries the player's rotation, so send it with the silent one and put the view back.
        mc.player.setYRot(event.getYaw());
        mc.player.setXRot(event.getPitch());
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        mc.player.setYRot(oldYaw);
        mc.player.setXRot(oldPitch);
        mc.player.getInventory().setSelectedSlot(oldSlot);

        throwTimer.reset();
        shouldLookDown = true;
        lookDown.reset();
    };

    private boolean isThrowablePot(ItemStack stack) {
        if (!ItemUtils.isSplashPotion(stack)) return false;
        return !HEAL_ONLY || ItemUtils.hasEffect(stack, MobEffects.INSTANT_HEALTH);
    }

    private int findBestPot() {
        for (int i = 0; i < 9; i++) {
            if (isThrowablePot(mc.player.getInventory().getItem(i))) return i;
        }
        return -1;
    }
}
