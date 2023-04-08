package arsenic.module.impl.world;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.utils.minecraft.PlayerUtils;
import net.minecraft.client.settings.KeyBinding;


@ModuleInfo(name = "BridgeAssist",category = ModuleCategory.WORLD)
public class BridgeAssist extends Module {
    //ill add the delays some day I just want it to work for now
    @EventLink
    public final Listener<EventTick> onTick = event -> setShift(PlayerUtils.playerOverAir() && mc.thePlayer.onGround);
    @Override
    public void onDisable() {
        setShift(false);
    }
    private void setShift(boolean sh) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), sh);
    }
}
