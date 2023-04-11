package arsenic.module.impl.world;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.utils.minecraft.PlayerUtils;
import net.minecraft.client.settings.KeyBinding;

import java.util.concurrent.ExecutorService;


@ModuleInfo(name = "SafeWalk", category = ModuleCategory.WORLD)
public class SafeWalk extends Module {
    //ill add the delays some day I just want it to work for now

    public final EnumProperty<sMode> mode = new EnumProperty<>("Mode: ", sMode.S_SHIFT);
    public final BooleanProperty onGround = new BooleanProperty("OnGround", false);

    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if(mode.getValue() == sMode.S_SHIFT)
            setShift(PlayerUtils.playerOverAir() && mc.thePlayer.onGround);
    };
    public boolean mixinResult(boolean flag) {
        if(flag)
            return true;
        if(mc.thePlayer.onGround || !onGround.getValue())
            if(mode.getValue() == sMode.NO_SHIFT)
                return true;

        return false;
    }
    @Override
    public void onDisable() {
        setShift(false);
    }
    private void setShift(boolean sh) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), sh);
    }

    public enum sMode {
        S_SHIFT,
        F_SHIFT,
        NO_SHIFT,
    }
}
