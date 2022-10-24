package arsenic.module.impl.misc;

import org.lwjgl.input.Keyboard;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRotate;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;

@ModuleInfo(name = "TestRotation", category = ModuleCategory.MISC, keybind = Keyboard.KEY_C)
public class TestRotation extends Module {

    @EventLink
    public final Listener<EventRotate> onKeyPress = event -> {
      event.setYaw(event.getYaw() + 50);
    };

    @Override
    protected void onEnable() {
        mc.thePlayer.sendChatMessage("enable rot");
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        mc.thePlayer.sendChatMessage("disable rot");
        super.onDisable();
    }

}
