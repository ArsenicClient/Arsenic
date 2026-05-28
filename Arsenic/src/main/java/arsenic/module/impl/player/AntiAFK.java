package arsenic.module.impl.player;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.timer.MSTimer;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

@ModuleInfo(name = "AntiAFK", category = ModuleCategory.PLAYER)
public class AntiAFK extends Module {

    public final DoubleProperty delay = new DoubleProperty("Delay (s)", new DoubleValue(5, 300, 30, 1));
    public final EnumProperty<Action> mode = new EnumProperty<>("Action", Action.Jump);
    public final BooleanProperty rotate = new BooleanProperty("Rotate", true);

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
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), true);
                break;
            case Forward:
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), true);
                break;
            case Backward:
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), true);
                break;
            case Strafe:
                KeyBinding.setKeyBindState(
                        mc.thePlayer.ticksExisted % 2 == 0 ? mc.gameSettings.keyBindRight.getKeyCode() : mc.gameSettings.keyBindLeft.getKeyCode(),
                        true
                );
                break;
        }

        if (rotate.getValue()) {
            mc.thePlayer.rotationYaw += mc.thePlayer.ticksExisted % 2 == 0 ? 15 : -15;
        }

        releaseTimer.reset();
    };

    private void releaseAction() {
        if (!actionHeld) return;
        switch (currentAction) {
            case Jump:
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
                break;
            case Forward:
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
                break;
            case Backward:
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), false);
                break;
            case Strafe:
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), false);
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(), false);
                break;
        }
        actionHeld = false;
        currentAction = null;
        actionTimer.reset();
    }

    private boolean isPlayerActive() {
        if (mc.thePlayer.motionX != 0 || mc.thePlayer.motionZ != 0 || mc.thePlayer.motionY != 0) return true;
        if (Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode()) ||
                Keyboard.isKeyDown(mc.gameSettings.keyBindBack.getKeyCode()) ||
                Keyboard.isKeyDown(mc.gameSettings.keyBindLeft.getKeyCode()) ||
                Keyboard.isKeyDown(mc.gameSettings.keyBindRight.getKeyCode()) ||
                Keyboard.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode()) ||
                Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode()) ||
                Keyboard.isKeyDown(mc.gameSettings.keyBindSprint.getKeyCode())) return true;
        if (Mouse.isButtonDown(0) || Mouse.isButtonDown(1) || Mouse.isButtonDown(2)) return true;
        return false;
    }

    public enum Action {
        Jump, Forward, Backward, Strafe
    }
}
