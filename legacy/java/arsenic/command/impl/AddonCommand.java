package arsenic.command.impl;

import arsenic.addon.AddonManager;
import arsenic.addon.AddonTemplate;
import arsenic.command.Command;
import arsenic.command.CommandInfo;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.utils.minecraft.PlayerUtils;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static arsenic.utils.java.JavaUtils.autoCompleteHelper;

@CommandInfo(name = "addon", args = { "new|reload|list|folder", "name", "category" }, aliases = { "addons" },
        help = "manages .java addons in the Arsenic/addons folder", minArgs = 1)
public class AddonCommand extends Command {

    private static final List<String> ACTIONS = Arrays.asList("new", "reload", "list", "folder");

    @Override
    public void execute(String[] args) {
        AddonManager addons = Arsenic.getArsenic().getAddonManager();
        switch (args[0].toLowerCase()) {
            case "new":
                create(addons, args);
                break;
            case "reload":
                reload(addons);
                break;
            case "list":
                list(addons);
                break;
            case "folder":
                PlayerUtils.addWaterMarkedMessageToChat(addons.getDirectory().getAbsolutePath());
                break;
            default:
                PlayerUtils.addWaterMarkedMessageToChat("Usage: .addon <" + String.join("|", ACTIONS) + ">");
        }
    }

    private void create(AddonManager addons, String[] args) {
        if (args.length < 2) {
            PlayerUtils.addWaterMarkedMessageToChat("Usage: .addon new <name> [category]");
            return;
        }
        ModuleCategory category = ModuleCategory.PLAYER;
        if (args.length > 2) {
            category = Arrays.stream(ModuleCategory.values()).filter(c -> c != ModuleCategory.SEARCH)
                    .filter(c -> c.name().equalsIgnoreCase(args[2])).findFirst().orElse(null);
            if (category == null) {
                PlayerUtils.addWaterMarkedMessageToChat(args[2] + " is not a valid category");
                return;
            }
        }
        try {
            File file = AddonTemplate.create(addons.getDirectory(), args[1], category);
            PlayerUtils.addWaterMarkedMessageToChat("Created " + file.getAbsolutePath());
            PlayerUtils.addWaterMarkedMessageToChat("Edit it, then run .addon reload");
        } catch (Exception e) {
            PlayerUtils.addWaterMarkedMessageToChat("§c" + e.getMessage());
        }
    }

    private void reload(AddonManager addons) {
        int count = addons.reload();
        PlayerUtils.addWaterMarkedMessageToChat("Loaded " + count + " addon module(s)");
        addons.getErrors().forEach(e -> PlayerUtils.addMessageToChat("§c" + e));
    }

    private void list(AddonManager addons) {
        List<Module> modules = addons.getLoadedModules();
        PlayerUtils.addWaterMarkedMessageToChat("Addon modules (" + modules.size() + "):");
        modules.forEach(m -> PlayerUtils.addWaterMarkedMessageToChat((m.isEnabled() ? "§a" : "§c") + m.getName()));
    }

    @Override
    protected List<String> getAutoComplete(String str, int arg, List<String> list) {
        if (arg == 0)
            return autoCompleteHelper(ACTIONS, str);
        if (arg == 2)
            return autoCompleteHelper(Arrays.stream(ModuleCategory.values()).filter(c -> c != ModuleCategory.SEARCH)
                    .map(c -> c.name().toLowerCase()).collect(Collectors.toList()), str);
        return list;
    }
}
