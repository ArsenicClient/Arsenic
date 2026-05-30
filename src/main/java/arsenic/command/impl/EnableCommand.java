package arsenic.command.impl;

import java.util.List;
import java.util.stream.Collectors;

import arsenic.command.Command;
import arsenic.command.CommandInfo;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.utils.minecraft.PlayerUtils;

import static arsenic.utils.java.JavaUtils.autoCompleteHelper;

@CommandInfo(name = "enable", args = { "module" }, aliases = { "e" }, help = "enables a module", minArgs = 1)
public class EnableCommand extends Command {

    @Override
    public void execute(String[] args) {
        Module module = Arsenic.getArsenic().getModuleManager().getModuleByName(args[0]);
        if (module == null) {
            PlayerUtils.addWaterMarkedMessageToChat(args[0] + " is not a valid module");
            return;
        }
        module.setEnabled(true);
        PlayerUtils.addWaterMarkedMessageToChat(module.getName() + " is now §aenabled");
        Arsenic.getArsenic().getConfigManager().saveConfig();
    }

    @Override
    protected List<String> getAutoComplete(String str, int arg, List<String> list) {
        // only suggest modules that aren't already enabled
        return arg == 0 ? autoCompleteHelper(Arsenic.getArsenic().getModuleManager().getModules().stream()
                .filter(m -> !m.isEnabled()).map(Module::getName).collect(Collectors.toList()), str) : list;
    }
}
