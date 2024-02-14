package arsenic.command.impl;

import arsenic.command.Command;
import arsenic.command.CommandInfo;
import arsenic.config.ConfigManager;
import arsenic.main.Arsenic;
import arsenic.utils.minecraft.PlayerUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@CommandInfo(name = "config", args = { "save/load/list", "config name" }, aliases = { "c" }, help = "helps you manipulate configs", minArgs = 1)
public class ConfigCommand extends Command {
    //don't scroll down if you wish to have living braincells
    ArrayList<String> args = new ArrayList<>(Arrays.asList("list", "load", "save"));
    @Override
    public void execute(String[] args) {
        ConfigManager configManager = Arsenic.getArsenic().getConfigManager();
        switch(args[0].toLowerCase()) {
            case "load":
                String lastConfig = configManager.getCurrentConfig().getName(); //gets the last config before trying to load another one
                try {
                    configManager.saveConfig(); // save the current config before we load another one
                    configManager.loadConfig(args[1]);
                    PlayerUtils.addWaterMarkedMessageToChat( "loaded " + args[1]);
                } catch (NullPointerException e){
                    configManager.loadConfig(lastConfig); //applies lastConfig if the passed args were invalid/config was null
                    PlayerUtils.addWaterMarkedMessageToChat(args[1] + " does not exist");
                }
                break;
            case "list":
                PlayerUtils.addWaterMarkedMessageToChat("Configs that are available: ");
                PlayerUtils.addWaterMarkedMessageToChat(configManager.getConfigList());
                break;
            case "save":
                try {
                    String prevConfig = configManager.getCurrentConfig().getName(); //get the current config (before saving)
                    configManager.createConfig(args[1]);
                    PlayerUtils.addWaterMarkedMessageToChat("created/saved " + args[1]);
                    configManager.loadConfig(prevConfig); //load the previous config again cuz config manager just loads the saved config for no reason
                } catch (ArrayIndexOutOfBoundsException r){
                    PlayerUtils.addWaterMarkedMessageToChat("could not create/save a config with the name "+args[1]);
                }
                break;
            default:
                PlayerUtils.addWaterMarkedMessageToChat( args[0] + " is not a valid argument");
                break;
        }
    }

    @Override
    protected List<String> getAutoComplete(String str, int arg, List<String> list) {
        return arg == 0 ? autoCompleteHelper(args, str) : list;
    }
}
