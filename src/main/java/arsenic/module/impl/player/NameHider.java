package arsenic.module.impl.player;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.StringProperty;

@ModuleInfo(name = "NameHider", category = ModuleCategory.PLAYER, hidden = true, dev = true)
public class NameHider extends Module {

    public static final StringProperty customName = new StringProperty("ArsenicClient");
    private static String originalName;
    public static final StringProperty playerName = new StringProperty("_Jxx");

    @Override
    protected void onEnable() {
        if (mc.thePlayer != null) {
            originalName = mc.thePlayer.getDisplayName().getUnformattedText();
            playerName.setValue(originalName);
        }
    }

    public static String format(String text) {
        if (originalName != null) {
            return text.replaceAll(originalName, customName.getValue());
        }
        return text;
    }

    @Override
    protected void onDisable() {
        originalName = null;
    }
}
