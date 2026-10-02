package arsenic.command.impl;

import java.util.List;
import java.util.stream.Collectors;

import arsenic.command.Command;
import arsenic.command.CommandInfo;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.utils.minecraft.PlayerUtils;

import static arsenic.utils.java.JavaUtils.autoCompleteHelper;

@CommandInfo(name = "disable", args = { "module" }, aliases = { "d" }, help = "disables a module", minArgs = 1)
public class DisableCommand extends Command {

    @Override
    public void execute(String[] args) {
        Module module = Arsenic.getArsenic().getModuleManager().getModuleByName(args[0]);
        if (module == null) {
            PlayerUtils.addWaterMarkedMessageToChat(args[0] + " is not a valid module");
            return;
        }
        module.setEnabled(false);
        PlayerUtils.addWaterMarkedMessageToChat(module.getName() + " is now §cdisabled");
        Arsenic.getArsenic().getConfigManager().saveConfig();
    }

    @Override
    protected List<String> getAutoComplete(String str, int arg, List<String> list) {
        // only suggest modules that are currently enabled
        return arg == 0 ? autoCompleteHelper(Arsenic.getArsenic().getModuleManager().getEnabledModules().stream()
                .map(Module::getName).collect(Collectors.toList()), str) : list;
    }
}
