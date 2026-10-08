import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import org.lwjgl.opengl.Display;

/**
 * Lowers the frame limit while the game window is not focused, and puts your limit back when it is focused again. Saves
 * CPU and GPU time while you are alt-tabbed.
 */
@ModuleInfo(name = "BackgroundFps", description = "Lowers the frame limit while the game window is unfocused", category = ModuleCategory.PLAYER)
public class BackgroundFps extends Module {

    public final DoubleProperty limit = new DoubleProperty("Background Limit", new DoubleValue(5, 60, 10, 1));

    private int savedLimit = -1;

    @Override
    protected void onDisable() {
        restore();
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        boolean focused = Display.isActive();
        if (!focused && savedLimit == -1) {
            savedLimit = mc.gameSettings.limitFramerate;
            mc.gameSettings.limitFramerate = (int) limit.getValue().getInput();
        } else if (focused && savedLimit != -1) {
            restore();
        }
    };

    private void restore() {
        if (savedLimit != -1 && mc.gameSettings != null) mc.gameSettings.limitFramerate = savedLimit;
        savedLimit = -1;
    }
}
