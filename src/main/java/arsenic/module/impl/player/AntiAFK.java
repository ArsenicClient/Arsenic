package arsenic.module.impl.player;

import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.EnumProperty;
import arsenic.utils.timer.MSTimer;
import net.minecraft.client.KeyMapping;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

@ModuleInfo(name = "AntiAFK", category = ModuleCategory.PLAYER)
public class AntiAFK extends Module {
    /** Seconds between actions. */
    public final DoubleProperty delay = new DoubleProperty("Delay (s)", new DoubleValue(5, 300, 30, 1));


    public final EnumProperty<Action> mode = new EnumProperty<>("Action", Action.Jump);

    private final MSTimer actionTimer = new MSTimer();
    private final MSTimer releaseTimer = new MSTimer();
    private Action currentAction;
    private boolean actionHeld;

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (isPlayerActive()) {
            releaseAction();
            actionTimer.reset();
            return;
        }

        if (!actionTimer.hasTimeElapsed((long) delay.getValue().getInput() * 1000)) return;

        if (actionHeld) {
            if (releaseTimer.hasTimeElapsed(500)) {
                releaseAction();
            }
            return;
        }

        currentAction = mode.getValue();
        actionHeld = true;

        switch (mode.getValue()) {
            case Jump:
                KeyMapping.setKeyBindState(mc.options.keyBindJump.getKeyCode(), true);
                break;
            case Forward:
                KeyMapping.setKeyBindState(mc.options.keyBindForward.getKeyCode(), true);
                break;
            case Backward:
                KeyMapping.setKeyBindState(mc.options.keyBindBack.getKeyCode(), true);
                break;
            case Strafe:
                KeyMapping.setKeyBindState(
                        mc.player.tickCount % 2 == 0 ? mc.options.keyBindRight.getKeyCode() : mc.options.keyBindLeft.getKeyCode(),
                        true
                );
                break;
        }

        mc.player.rotationYaw += mc.player.tickCount % 2 == 0 ? 15 : -15;

        releaseTimer.reset();
    };

    private void releaseAction() {
        if (!actionHeld) return;
        switch (currentAction) {
            case Jump:
                KeyMapping.setKeyBindState(mc.options.keyBindJump.getKeyCode(), false);
                break;
            case Forward:
                KeyMapping.setKeyBindState(mc.options.keyBindForward.getKeyCode(), false);
                break;
            case Backward:
                KeyMapping.setKeyBindState(mc.options.keyBindBack.getKeyCode(), false);
                break;
            case Strafe:
                KeyMapping.setKeyBindState(mc.options.keyBindLeft.getKeyCode(), false);
                KeyMapping.setKeyBindState(mc.options.keyBindRight.getKeyCode(), false);
                break;
        }
        actionHeld = false;
        currentAction = null;
        actionTimer.reset();
    }

    private boolean isPlayerActive() {
        if (mc.player.motionX != 0 || mc.player.motionZ != 0 || mc.player.motionY != 0) return true;
        if (Keyboard.isKeyDown(mc.options.keyBindForward.getKeyCode()) ||
                Keyboard.isKeyDown(mc.options.keyBindBack.getKeyCode()) ||
                Keyboard.isKeyDown(mc.options.keyBindLeft.getKeyCode()) ||
                Keyboard.isKeyDown(mc.options.keyBindRight.getKeyCode()) ||
                Keyboard.isKeyDown(mc.options.keyBindJump.getKeyCode()) ||
                Keyboard.isKeyDown(mc.options.keyBindSneak.getKeyCode()) ||
                Keyboard.isKeyDown(mc.options.keyBindSprint.getKeyCode())) return true;
        if (Mouse.isButtonDown(0) || Mouse.isButtonDown(1) || Mouse.isButtonDown(2)) return true;
        return false;
    }

    public enum Action {
        Jump, Forward, Backward, Strafe
    }
}
