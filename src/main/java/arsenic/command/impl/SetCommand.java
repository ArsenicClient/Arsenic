package arsenic.command.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import arsenic.command.Command;
import arsenic.command.CommandInfo;
import arsenic.command.CommandUtils;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.utils.minecraft.PlayerUtils;

import static arsenic.utils.java.JavaUtils.autoCompleteHelper;

@CommandInfo(name = "set", args = { "module", "setting", "value" }, aliases = { "s" }, help = "changes a module's setting, e.g. .set Sprint Multiplier 1.5", minArgs = 2)
public class SetCommand extends Command {

    @Override
    public void execute(String[] args) {
        Module module = Arsenic.getArsenic().getModuleManager().getModuleByName(args[0]);
        if (module == null) {
            PlayerUtils.addWaterMarkedMessageToChat(args[0] + " is not a valid module");
            return;
        }

        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        CommandUtils.PropMatch match = CommandUtils.matchProperty(module, rest);
        if (match == null) {
            PlayerUtils.addWaterMarkedMessageToChat(module.getName() + " has no setting called '" + rest[0] + "'");
            PlayerUtils.addWaterMarkedMessageToChat("settings: " + String.join(", ", CommandUtils.getSettingNames(module)));
            return;
        }

        if (match.valueArgs.length == 0) {
            PlayerUtils.addWaterMarkedMessageToChat(CommandUtils.displayName(match.property) + " is currently " + CommandUtils.describe(match.property));
            return;
        }

        PlayerUtils.addWaterMarkedMessageToChat(CommandUtils.applyValue(match.property, match.valueArgs));
        Arsenic.getArsenic().getConfigManager().saveConfig();
    }

    @Override
    public List<String> getAutoComplete(String[] args) {
        String current = args[args.length - 1];

        if (args.length <= 1)
            return autoCompleteHelper(Arsenic.getArsenic().getModuleManager().getModules().stream()
                    .map(Module::getName).collect(Collectors.toList()), current);

        Module module = Arsenic.getArsenic().getModuleManager().getModuleByName(args[0]);
        if (module == null)
            return new ArrayList<>();

        if (args.length == 2)
            return autoCompleteHelper(CommandUtils.getSettingNames(module), current);

        // value position: suggest enum modes / booleans for the named setting
        CommandUtils.PropMatch match = CommandUtils.matchProperty(module, new String[] { args[1] });
        return match != null ? autoCompleteHelper(CommandUtils.valueSuggestions(match.property), current) : new ArrayList<>();
    }
}
