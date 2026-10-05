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

import static arsenic.utils.java.JavaUtils.autoCompleteHelper;

@CommandInfo(name = "config", args = { "save/load/list/delete", "config name" }, aliases = { "c" }, help = "helps you manipulate configs", minArgs = 1)
public class ConfigCommand extends Command {
    ArrayList<String> args = new ArrayList<>(Arrays.asList("list", "load", "save", "delete"));
    @Override
    public void execute(String[] args) {
        ConfigManager configManager = Arsenic.getArsenic().getConfigManager();
        switch(args[0].toLowerCase()) {
            case "load":
                if (args.length == 1) {
                    PlayerUtils.addWaterMarkedMessageToChat("I need the name of the config");
                    break;
                }
                String lastConfig = configManager.getCurrentConfig().getName();
                try {
                    configManager.saveConfig();
                    configManager.loadConfig(args[1]);
                    PlayerUtils.addWaterMarkedMessageToChat("loaded " + args[1]);
                } catch (NullPointerException e) {
                    configManager.loadConfig(lastConfig);
                    PlayerUtils.addWaterMarkedMessageToChat(args[1] + " does not exist");
                }
                break;
            case "list":
                PlayerUtils.addWaterMarkedMessageToChat("Configs that are available: ");
                PlayerUtils.addWaterMarkedMessageToChat(configManager.getConfigList());
                break;
            case "save":
                if (args.length == 1) {
                    PlayerUtils.addWaterMarkedMessageToChat("I need the name of the config");
                    break;
                }
                try {
                    String prevConfig = configManager.getCurrentConfig().getName();
                    configManager.createConfig(args[1]);
                    PlayerUtils.addWaterMarkedMessageToChat("created/saved " + args[1]);
                    configManager.loadConfig(prevConfig);
                } catch (ArrayIndexOutOfBoundsException r){
                    PlayerUtils.addWaterMarkedMessageToChat("could not create/save a config with the name "+ args[1]);
                }
                break;
            case "delete":
                if (args.length == 1) {
                    PlayerUtils.addWaterMarkedMessageToChat("I need the name of the config");
                    break;
                }
                if (!configManager.getConfigList().contains(args[1])) {
                    PlayerUtils.addWaterMarkedMessageToChat(args[1] + " does not exist");
                    break;
                }
                if (configManager.getCurrentConfig().getName().equalsIgnoreCase(args[1])) {
                    PlayerUtils.addWaterMarkedMessageToChat("you can't delete the config you're currently using");
                    break;
                }
                configManager.deleteConfig(args[1]);
                PlayerUtils.addWaterMarkedMessageToChat("deleted " + args[1]);
                break;
            default:
                PlayerUtils.addWaterMarkedMessageToChat( args[0] + " is not a valid argument");
                break;
        }
    }

    @Override
    public List<String> getAutoComplete(String[] args) {
        String current = args[args.length - 1];
        if (args.length <= 1)
            return autoCompleteHelper(this.args, current);

        String sub = args[0].toLowerCase();
        if (sub.equals("load") || sub.equals("delete"))
            return autoCompleteHelper(new ArrayList<>(Arsenic.getArsenic().getConfigManager().getConfigList()), current);
        return Collections.emptyList();
    }
}
