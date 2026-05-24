package arsenic.module.impl.player;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventUpdate;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import net.minecraft.util.ChatComponentText;

@ModuleInfo(name = "RageQuit", category = ModuleCategory.PLAYER)
public class RageQuit extends Module {

    public final DoubleProperty health = new DoubleProperty("Health", new DoubleValue(1, 20, 6, 1));

    private boolean triggered;

    @Override
    protected void onEnable() {
        triggered = false;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventUpdate.Pre> onUpdate = event -> {
        if (triggered) return;

        if (mc.thePlayer.getHealth() <= health.getValue().getInput()) {
            triggered = true;
            mc.getNetHandler().getNetworkManager().closeChannel(new ChatComponentText(""));
        }
    };
}
