import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventFov;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;

/**
 * Sets the camera's field of view. Uses one value normally and another while you are sprinting, so the sprint zoom
 * can be softened or removed. Affects the camera only, not the held items. Off until the module is enabled.
 */
@ModuleInfo(name = "CustomFov", description = "Sets the camera field of view, with a separate value while sprinting", category = ModuleCategory.RENDER)
public class CustomFov extends Module {

    public final DoubleProperty fov = new DoubleProperty("FOV", new DoubleValue(30, 110, 70, 1));
    public final BooleanProperty useSprint = new BooleanProperty("Separate Sprint FOV", true);
    public final DoubleProperty sprintFov = new DoubleProperty("Sprint FOV", new DoubleValue(30, 110, 70, 1));

    @EventLink
    public final Listener<EventFov> onFov = event -> {
        if (mc.thePlayer != null && mc.thePlayer.isSprinting() && useSprint.getValue()) {
            event.setFov((float) sprintFov.getValue().getInput());
        } else {
            event.setFov((float) fov.getValue().getInput());
        }
    };
}
