import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;

/**
 * Turns off view bobbing (the vanilla "View Bobbing" option) while enabled. The previous value is put back when the
 * module is disabled. Nothing is sent to the server, so there is no visual to draw: this is a setting toggle.
 */
@ModuleInfo(name = "NoBob", description = "Turns off view bobbing while enabled", category = ModuleCategory.RENDER)
public class NoBob extends Module {

    private boolean previous;
    private boolean saved;

    @Override
    protected void onEnable() {
        if (mc.gameSettings == null) return;
        previous = mc.gameSettings.viewBobbing;
        saved = true;
        mc.gameSettings.viewBobbing = false;
    }

    @Override
    protected void onDisable() {
        if (saved && mc.gameSettings != null) mc.gameSettings.viewBobbing = previous;
        saved = false;
    }
}
