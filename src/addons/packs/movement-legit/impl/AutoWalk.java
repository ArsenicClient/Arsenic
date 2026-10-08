import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventMovementInput;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;

/**
 * Holds the forward movement until you turn the module off. Pressing back cancels it while you hold that key. It only
 * sets the forward input the keyboard would give; the camera and the rest of the movement are left alone.
 */
@ModuleInfo(name = "AutoWalk", description = "Holds forward until you turn it off, or press back", category = ModuleCategory.MOVEMENT)
public class AutoWalk extends Module {

    public final BooleanProperty sprint = new BooleanProperty("Sprint", true);

    @RequiresPlayer
    @EventLink
    public final Listener<EventMovementInput> onInput = event -> {
        if (mc.currentScreen != null || mc.gameSettings.keyBindBack.isKeyDown()) return;
        event.setSpeed(1f);
    };
}
