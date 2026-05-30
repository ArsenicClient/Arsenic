package arsenic.command.impl;

import java.util.List;
import java.util.stream.Collectors;

import arsenic.command.Command;
import arsenic.command.CommandInfo;
import arsenic.command.CommandUtils;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.property.Property;
import arsenic.utils.minecraft.PlayerUtils;

import static arsenic.utils.java.JavaUtils.autoCompleteHelper;

@CommandInfo(name = "settings", args = { "module" }, aliases = { "ss", "info" }, help = "lists a module's settings and their current values", minArgs = 1)
public class SettingsCommand extends Command {

    @Override
    public void execute(String[] args) {
        Module module = Arsenic.getArsenic().getModuleManager().getModuleByName(args[0]);
        if (module == null) {
            PlayerUtils.addWaterMarkedMessageToChat(args[0] + " is not a valid module");
            return;
        }

        PlayerUtils.addMessageToChat("---------------");
        PlayerUtils.addWaterMarkedMessageToChat(module.getName() + " settings:");
        List<Property<?>> props = CommandUtils.getSettableProperties(module);
        if (props.isEmpty()) {
            PlayerUtils.addWaterMarkedMessageToChat("this module has no settings");
        } else {
            for (Property<?> p : props)
                PlayerUtils.addWaterMarkedMessageToChat("§7" + CommandUtils.displayName(p) + "§r: " + CommandUtils.describe(p));
        }
        PlayerUtils.addMessageToChat("---------------");
    }

    @Override
    protected List<String> getAutoComplete(String str, int arg, List<String> list) {
        return arg == 0 ? autoCompleteHelper(Arsenic.getArsenic().getModuleManager().getModules().stream()
                .map(Module::getName).collect(Collectors.toList()), str) : list;
    }
}
