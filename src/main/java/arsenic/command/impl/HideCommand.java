package arsenic.command.impl;

import java.util.List;
import java.util.stream.Collectors;

import arsenic.command.Command;
import arsenic.command.CommandInfo;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.utils.minecraft.PlayerUtils;

import static arsenic.utils.java.JavaUtils.autoCompleteHelper;

@CommandInfo(name = "hide", args = { "module" }, aliases = { "unhide" }, help = "hides or unhides a module from the arraylist", minArgs = 1)
public class HideCommand extends Command {

    @Override
    public void execute(String[] args) {
        Module module = Arsenic.getArsenic().getModuleManager().getModuleByName(args[0]);
        if (module == null) {
            PlayerUtils.addWaterMarkedMessageToChat(args[0] + " is not a valid module");
            return;
        }
        module.setHidden(!module.isHidden());
        PlayerUtils.addWaterMarkedMessageToChat(module.getName() + " is now " + (module.isHidden() ? "§chidden" : "§avisible"));
        Arsenic.getArsenic().getConfigManager().saveConfig();
    }

    @Override
    protected List<String> getAutoComplete(String str, int arg, List<String> list) {
        return arg == 0 ? autoCompleteHelper(Arsenic.getArsenic().getModuleManager().getModules().stream()
                .map(Module::getName).collect(Collectors.toList()), str) : list;
    }
}
