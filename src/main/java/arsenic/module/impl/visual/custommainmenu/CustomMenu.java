package arsenic.module.impl.visual.custommainmenu;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventDisplayGuiScreen;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import net.minecraft.client.gui.GuiMainMenu;


@ModuleInfo(name = "CustomMainMenu", category = ModuleCategory.SETTINGS, hidden = true, enabled = true)
public class CustomMenu extends Module {

    private final Screen screen = new Screen();

    @EventLink
    private final Listener<EventDisplayGuiScreen> displayGuiScreenEvent = event -> {
        if(event.getGuiScreen() instanceof GuiMainMenu)
            mc.displayGuiScreen(screen);
    };

}
