package arsenic.module.impl.visual;

import arsenic.event.impl.EventRender2D;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.utils.font.TTFontRenderer;
import arsenic.utils.interfaces.IFontRenderer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import org.lwjgl.input.Keyboard;

@ModuleInfo(name = "HUD", category = ModuleCategory.VISUAL, keybind = Keyboard.KEY_Y)
public class HUD extends Module {

    @EventLink
    public final Listener<EventRender2D> onRender2D = event -> {

        IFontRenderer mcFr = (IFontRenderer) mc.fontRendererObj;
        TTFontRenderer ttfFr = Arsenic.getInstance().getFonts().FR;

        mcFr.drawStringWithShadow("Test123", 4, 4, -1);
        ttfFr.drawStringWithShadow("Test123", 4, 16, -1);

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
