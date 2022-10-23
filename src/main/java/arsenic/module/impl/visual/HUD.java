package arsenic.module.impl.visual;

import org.lwjgl.input.Keyboard;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.utils.interfaces.IFontRenderer;

@ModuleInfo(name = "HUD", category = ModuleCategory.VISUAL, keybind = Keyboard.KEY_Y)
public class HUD extends Module {

    @EventLink
    public final Listener<EventRender2D> onRender2D = event -> {
        IFontRenderer mcFr = (IFontRenderer) mc.fontRendererObj;
        float yOffSet= 0;
        for(Module m : Arsenic.getArsenic().getModuleManager().getEnabledModules()) {
            mcFr.drawString(m.getName(), 0, yOffSet, 0xFFFFFFFF);
            yOffSet += mcFr.getHeight(m.getName());
        }
    };

    @Override
    protected void onEnable() {
        mc.thePlayer.sendChatMessage("enable h");
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        mc.thePlayer.sendChatMessage("disable h");
        super.onDisable();
    }

}
