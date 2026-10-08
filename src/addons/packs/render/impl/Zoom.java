import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventFov;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.TextProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import org.lwjgl.input.Keyboard;

/**
 * While the zoom key is held, the camera narrows to the zoom FOV. Enable the module to arm it; release the key to see
 * normal view again. The key is a name such as C, Z or LSHIFT; with no valid key, or while a screen is open, zoom does nothing.
 */
@ModuleInfo(name = "Zoom", description = "Hold a key to narrow the camera field of view", category = ModuleCategory.RENDER)
public class Zoom extends Module {

    public final TextProperty key = new TextProperty("Zoom Key", "C", 16);
    public final DoubleProperty zoomFov = new DoubleProperty("Zoom FOV", new DoubleValue(10, 60, 30, 1));

    @EventLink
    public final Listener<EventFov> onFov = event -> {
        if (mc.currentScreen != null) return;
        int code = Keyboard.getKeyIndex(key.getValue().trim().toUpperCase());
        if (code == Keyboard.KEY_NONE || !Keyboard.isKeyDown(code)) return;
        event.setFov((float) zoomFov.getValue().getInput());
    };
}
