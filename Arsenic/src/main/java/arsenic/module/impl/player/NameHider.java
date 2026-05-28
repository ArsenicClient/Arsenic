package arsenic.module.impl.player;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.StringProperty;

@ModuleInfo(name = "NameHider", category = ModuleCategory.PLAYER)
public class NameHider extends Module {

    public final StringProperty customName = new StringProperty("Hidden");

    private String originalName;

    @Override
    protected void onEnable() {
        if (mc.thePlayer != null) {
            originalName = mc.thePlayer.getDisplayName().getUnformattedText();
        }
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (!mc.thePlayer.getDisplayName().getUnformattedText().equals(customName.getValue())) {
            originalName = mc.thePlayer.getDisplayName().getUnformattedText();
        }
    };

    public String format(String text) {
        if (mc.thePlayer != null) {
            text = text.replace(originalName != null ? originalName : mc.thePlayer.getName(), customName.getValue());
        }
        return text;
    }

    @Override
    protected void onDisable() {
        originalName = null;
    }
}
