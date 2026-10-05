package arsenic.module.impl.player;

import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.TextProperty;

@ModuleInfo(name = "NameHider", category = ModuleCategory.RENDER, hidden = true)
public class NameHider extends Module {

    public final TextProperty name = new TextProperty("Name", "ArsenicClient");

    public static String format(String text) {
        if (text == null || text.isEmpty() || mc.player == null) {
            return text;
        }

        NameHider module = Arsenic.getInstance().getModuleManager().getModuleByClass(NameHider.class);
        if (module == null || !module.isEnabled()) {
            return text;
        }

        String realName = mc.player.getName().getString();
        String replacement = module.name.getValue().replace('&', '§');
        if (realName == null || realName.isEmpty() || replacement.isEmpty() || !text.contains(realName)) {
            return text;
        }

        return text.replace(realName, replacement);
    }
}
