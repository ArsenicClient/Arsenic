package arsenic.module.impl.combat;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventMovementInput;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.PlayerUtils;


@ModuleInfo(name = "AutoSpacer", category = ModuleCategory.OTHER)
public class AutoSpacer extends Module {

    public final DoubleProperty distance = new DoubleProperty("Distance", new DoubleValue(0, 5, 3, 0.1));
    public final DoubleProperty slowDown = new DoubleProperty("Slowdown", new DoubleValue(-1, 1, 0.9, 0.1));

    @EventLink
    public final Listener<EventMovementInput> eventMovementInputListener = event -> {
        if(PlayerUtils.getPlayersWithin(distance.getValue().getInput()).size() != 1)
            return;
        event.setSpeed((float) (slowDown.getValue().getInput() * event.getSpeed()));
    };

}
