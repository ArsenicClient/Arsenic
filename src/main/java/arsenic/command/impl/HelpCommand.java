package arsenic.command.impl;

import java.util.Arrays;
import java.util.List;

import arsenic.command.Command;
import arsenic.command.CommandInfo;
import arsenic.main.Arsenic;
import arsenic.utils.minecraft.PlayerUtils;

@CommandInfo(name = "help", args = { "command" }, help = "shows help for commands")
public class HelpCommand extends Command {

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            execute(new String[] { "help" });
            return;
        }

        Command command = Arsenic.getArsenic().getCommandManager().getCommandByName(args[0]);
        if (command != null) {
            PlayerUtils.addMessageToChat("---------------");
            PlayerUtils.addWaterMarkedMessageToChat(command.getName() + "'s info:");
            PlayerUtils.addWaterMarkedMessageToChat("Description: " + command.getHelp());
            PlayerUtils.addWaterMarkedMessageToChat("Aliases: " + Arrays.toString(command.getAliases()));
            StringBuilder bobTheBuilder = new StringBuilder("." + command.getName());
            for (String arg : command.getArgs()) { bobTheBuilder.append(" <" + arg + ">");}
            PlayerUtils.addWaterMarkedMessageToChat("Usage: " + bobTheBuilder.toString());
            PlayerUtils.addMessageToChat("---------------");
        }
    }

    @Override
    protected List<String> getAutoComplete(String str, int arg, List<String> list) {
        return arg == 0 ? Arsenic.getArsenic().getCommandManager().getClosestCommandName(str) : list;
    }
}
