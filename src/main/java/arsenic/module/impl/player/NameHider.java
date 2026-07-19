package arsenic.module.impl.player;

import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.StringProperty;

@ModuleInfo(name = "NameHider", category = ModuleCategory.PLAYER, hidden = true, dev = false)
public class NameHider extends Module {

    public static final StringProperty customName = new StringProperty("ArsenicClient");

    /**
     * Called from MixinFontRenderer for every string that gets rendered.
     * Resolves the real name live (so it always matches, no stale/null cache),
     * only acts while the module is enabled, and uses a literal replace so
     * regex/colour-code characters in either name don't break or crash it.
     */
    public static String format(String text) {
        if (text == null || text.isEmpty() || mc.thePlayer == null) {
            return text;
        }

        NameHider module = Arsenic.getInstance().getModuleManager().getModuleByClass(NameHider.class);
        if (module == null || !module.isEnabled()) {
            return text;
        }

        String realName = mc.thePlayer.getName();
        String replacement = customName.getValue();
        if (realName == null || realName.isEmpty() || replacement == null || !text.contains(realName)) {
            return text;
        }

        // Literal replacement of every occurrence, preserving any surrounding
        // colour/formatting codes (unlike replaceAll, which treats args as regex).
        return text.replace(realName, replacement);
    }
}
