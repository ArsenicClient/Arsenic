package arsenic.module.impl.movement;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.injection.accessor.IMixinKeyBinding;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;

@ModuleInfo(name = "Sprint", category = ModuleCategory.MOVEMENT)
public class Sprint extends Module {

    @EventLink
    public final Listener<EventTick> onKeyPress = event -> {
        ((IMixinKeyBinding) mc.gameSettings.keyBindSprint).setPressed(true);
    };

}