package arsenic.module.impl.visual.custommainmenu;

import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;


@ModuleInfo(name = "CustomMainMenu", category = ModuleCategory.SETTINGS, hidden = true, enabled = true)
public class CustomMenu extends Module {

    public void display() {
        mc.displayGuiScreen(new Screen());
    }

}
