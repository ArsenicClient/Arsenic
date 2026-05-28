package arsenic.command.impl;

import arsenic.command.Command;
import arsenic.command.CommandInfo;
import arsenic.gui.themes.Theme;
import arsenic.main.Arsenic;
import arsenic.utils.minecraft.PlayerUtils;

@CommandInfo(name = "theme", args = {"load", "name"}, aliases = { "t" }, help = "loads a cgui theme", minArgs = 2)
public class ThemeCommand extends Command {
    @Override
    public void execute(String[] args) {
        if(args[0].equalsIgnoreCase("load")) {
            Theme theme = Arsenic.getArsenic().getThemeManager().getContentByJsonKey(args[1]);
            if(theme != null) {
                Arsenic.getArsenic().getThemeManager().setCurrentTheme(theme);
                PlayerUtils.addWaterMarkedMessageToChat("Set theme to §d§l" + theme.getJsonKey());
                return;
            }
            PlayerUtils.addWaterMarkedMessageToChat("Could not find §d§l" + args[1] + " theme");
        }
    }
}
