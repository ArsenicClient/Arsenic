package arsenic.command.impl;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import arsenic.command.Command;
import arsenic.command.CommandInfo;
import arsenic.gui.themes.Theme;
import arsenic.main.Arsenic;
import arsenic.utils.minecraft.PlayerUtils;

import static arsenic.utils.java.JavaUtils.autoCompleteHelper;

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
            return;
        }
        PlayerUtils.addWaterMarkedMessageToChat(args[0] + " is not a valid argument (try 'load')");
    }

    @Override
    protected List<String> getAutoComplete(String str, int arg, List<String> list) {
        if (arg == 0)
            return autoCompleteHelper(Collections.singletonList("load"), str);
        if (arg == 1)
            return autoCompleteHelper(Arsenic.getArsenic().getThemeManager().getContents().stream()
                    .map(Theme::getJsonKey).collect(Collectors.toList()), str);
        return list;
    }
}
