package arsenic.command.impl;

import arsenic.command.Command;
import arsenic.command.CommandInfo;
import arsenic.config.ConfigManager;
import arsenic.main.Arsenic;
import arsenic.utils.minecraft.PlayerUtils;

@CommandInfo(name = "config", args = { "config", "cfg" }, aliases = { "c" }, help = "lets you save/load configs", minArgs = 2)
public class ConfigCommand extends Command {

    @Override
    public void execute(String[] args) {
        ConfigManager configManager = Arsenic.getArsenic().getConfigManager();
        if (args[0].equalsIgnoreCase("load")){
            try {
                configManager.loadConfig(args[1]);
                PlayerUtils.addWaterMarkedMessageToChat( "loaded " + args[1]);
            } catch (NullPointerException e){
                PlayerUtils.addWaterMarkedMessageToChat(args[1] + " does not exist");
            }
        } else if (args[0].equalsIgnoreCase("save") || args[0].equalsIgnoreCase("create")){
            configManager.createConfig(args[1]);
            PlayerUtils.addWaterMarkedMessageToChat( "created/saved " + args[1]);
        } else {
            PlayerUtils.addWaterMarkedMessageToChat( args[0] + " is not a valid argument");
        }
    }
}
