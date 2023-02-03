package arsenic.command.impl;

import arsenic.command.Command;
import arsenic.command.CommandInfo;
import arsenic.main.Arsenic;
import arsenic.utils.minecraft.PlayerUtils;

import java.util.Arrays;

@CommandInfo(name = "help", args = {"command"}, help = "shows help for commands")
public class HelpCommand extends Command {

    @Override
    public void execute(String[] args) {
        if(args.length == 0) {
            execute(new String[]{"help"});
            return;
        }
        Command command = Arsenic.getArsenic().getCommandManager().getCommandByName(args[0]);
        if (command != null) {
            PlayerUtils.addMessageToChat("---------------");
            PlayerUtils.addWaterMarkedMessageToChat(command.getName() + "'s info:");
            PlayerUtils.addWaterMarkedMessageToChat("Description: " + command.getHelp());
            PlayerUtils.addWaterMarkedMessageToChat("Aliases: " + Arrays.toString(command.getAliases()));
            String str =  "." + command.getName();
            for(String arg : command.getArgs()) {
                str += " <" + arg + ">";
            }
            PlayerUtils.addWaterMarkedMessageToChat("Usage: " + str);
            PlayerUtils.addMessageToChat("---------------");
        }
    }

    @Override
    public String getAutoComplete(String str, int arg) {
        if(arg == 0) {
            return Arsenic.getArsenic().getCommandManager().getClosestCommandName(str);
        }
        return "";
    }
}
