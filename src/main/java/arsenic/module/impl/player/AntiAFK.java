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
import arsenic.utils.io.Keys;
import com.mojang.blaze3d.platform.InputConstants;
import arsenic.utils.io.Keys;

@ModuleInfo(name = "AntiAFK", category = ModuleCategory.PLAYER)
public class AntiAFK extends Module {
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
                mc.options.keyJump.setDown(true);
                break;
            case Forward:
                mc.options.keyUp.setDown(true);
                break;
            case Backward:
                mc.options.keyDown.setDown(true);
                break;
            case Strafe:
                (mc.player.tickCount % 2 == 0 ? mc.options.keyRight : mc.options.keyLeft).setDown(true);
                break;
        }

        mc.player.setYRot(mc.player.getYRot() + (mc.player.tickCount % 2 == 0 ? 15 : -15));

        releaseTimer.reset();
    };

    private void releaseAction() {
        if (!actionHeld) return;
        switch (currentAction) {
            case Jump:
                mc.options.keyJump.setDown(false);
                break;
            case Forward:
                mc.options.keyUp.setDown(false);
                break;
            case Backward:
                mc.options.keyDown.setDown(false);
                break;
            case Strafe:
                mc.options.keyLeft.setDown(false);
                mc.options.keyRight.setDown(false);
                break;
        }
        actionHeld = false;
        currentAction = null;
        actionTimer.reset();
    }

    private boolean isPlayerActive() {
        if (mc.player.getDeltaMovement().x != 0 || mc.player.getDeltaMovement().z != 0 || mc.player.getDeltaMovement().y != 0) return true;
        if (Keys.isPhysicallyDown(mc.options.keyUp) ||
                Keys.isPhysicallyDown(mc.options.keyDown) ||
                Keys.isPhysicallyDown(mc.options.keyLeft) ||
                Keys.isPhysicallyDown(mc.options.keyRight) ||
                Keys.isPhysicallyDown(mc.options.keyJump) ||
                Keys.isPhysicallyDown(mc.options.keyShift) ||
                Keys.isPhysicallyDown(mc.options.keySprint)) return true;
        if (Keys.isMouseDown(0) || Keys.isMouseDown(1) || Keys.isMouseDown(2)) return true;
        return false;
    }

    public enum Action {
        Jump, Forward, Backward, Strafe
    }
}
