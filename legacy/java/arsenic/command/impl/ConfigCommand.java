package arsenic.command.impl;

import arsenic.command.Command;
import arsenic.command.CommandInfo;
import arsenic.config.ConfigManager;
import arsenic.main.Arsenic;
import arsenic.utils.minecraft.PlayerUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static arsenic.utils.java.JavaUtils.autoCompleteHelper;

@CommandInfo(name = "config", args = { "list/load/save/delete/rename/reset", "config name" }, aliases = { "c" }, help = "load, save and manage configs", minArgs = 1)
public class ConfigCommand extends Command {

    private static final List<String> SUBCOMMANDS = Arrays.asList("list", "load", "save", "delete", "rename", "reset");

    @Override
    public void execute(String[] args) {
        ConfigManager configs = Arsenic.getArsenic().getConfigManager();
        String sub = args[0].toLowerCase();
        // names can have spaces, so everything after the subcommand is the name
        String name = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim() : "";
        try {
            switch (sub) {
                case "list":
                    configs.reloadConfigs();
                    PlayerUtils.addWaterMarkedMessageToChat("Configs (" + configs.getConfigList().size() + "): "
                            + String.join(", ", configs.getConfigList()) + "  §7(using " + configs.getCurrentName() + ")");
                    break;
                case "load":
                    if (needsName(name)) break;
                    if (configs.isCurrent(name)) {
                        PlayerUtils.addWaterMarkedMessageToChat("Already using " + configs.getCurrentName());
                    } else if (configs.loadConfig(name)) {
                        PlayerUtils.addWaterMarkedMessageToChat("Loaded " + configs.getCurrentName());
                    } else {
                        PlayerUtils.addWaterMarkedMessageToChat("There is no config named \"" + name + "\"");
                    }
                    break;
                case "save":
                    if (name.isEmpty()) {
                        configs.saveConfig();
                        PlayerUtils.addWaterMarkedMessageToChat("Saved " + configs.getCurrentName());
                    } else {
                        boolean existed = configs.exists(name) && !configs.isCurrent(name);
                        String saved = configs.saveAs(name, true);
                        PlayerUtils.addWaterMarkedMessageToChat((existed ? "Overwrote " : "Saved ") + saved + " and switched to it");
                    }
                    break;
                case "delete":
                    if (needsName(name)) break;
                    configs.deleteConfig(name);
                    PlayerUtils.addWaterMarkedMessageToChat("Deleted " + name);
                    break;
                case "rename":
                    rename(configs, Arrays.copyOfRange(args, 1, args.length));
                    break;
                case "reset":
                    configs.resetCurrentConfig();
                    PlayerUtils.addWaterMarkedMessageToChat("Reset " + configs.getCurrentName() + " to defaults");
                    break;
                default:
                    PlayerUtils.addWaterMarkedMessageToChat(args[0] + " is not a valid argument (" + String.join(", ", SUBCOMMANDS) + ")");
                    break;
            }
        } catch (IllegalArgumentException e) {
            PlayerUtils.addWaterMarkedMessageToChat(e.getMessage());
        }
    }

    /** ".config rename <old> <new>": the longest start of the words that names a config is the old name, the rest the new one. */
    private void rename(ConfigManager configs, String[] words) {
        for (int split = words.length - 1; split >= 1; split--) {
            String oldName = String.join(" ", Arrays.copyOfRange(words, 0, split));
            if (configs.exists(oldName)) {
                String newName = configs.renameConfig(oldName, String.join(" ", Arrays.copyOfRange(words, split, words.length)));
                PlayerUtils.addWaterMarkedMessageToChat("Renamed " + oldName + " to " + newName);
                return;
            }
        }
        PlayerUtils.addWaterMarkedMessageToChat("Usage: .config rename <config> <new name>");
    }

    private static boolean needsName(String name) {
        if (!name.isEmpty())
            return false;
        PlayerUtils.addWaterMarkedMessageToChat("I need the name of the config");
        return true;
    }

    @Override
    public List<String> getAutoComplete(String[] args) {
        String current = args[args.length - 1];
        if (args.length <= 1)
            return autoCompleteHelper(SUBCOMMANDS, current);

        String sub = args[0].toLowerCase();
        if (args.length == 2 && (sub.equals("load") || sub.equals("delete") || sub.equals("rename") || sub.equals("save")))
            return autoCompleteHelper(Arsenic.getArsenic().getConfigManager().getConfigList(), current);
        return Collections.emptyList();
    }
}
