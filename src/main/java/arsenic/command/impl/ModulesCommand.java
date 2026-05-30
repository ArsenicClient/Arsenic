package arsenic.command.impl;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import arsenic.command.Command;
import arsenic.command.CommandInfo;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.utils.minecraft.PlayerUtils;

import static arsenic.utils.java.JavaUtils.autoCompleteHelper;

@CommandInfo(name = "modules", args = { "category" }, aliases = { "list" }, help = "lists modules, optionally filtered by category", minArgs = 0)
public class ModulesCommand extends Command {

    @Override
    public void execute(String[] args) {
        Collection<Module> modules;
        String header;

        if (args.length == 0) {
            modules = Arsenic.getArsenic().getModuleManager().getModules();
            header = "All modules";
        } else {
            ModuleCategory category = matchCategory(args[0]);
            if (category == null) {
                PlayerUtils.addWaterMarkedMessageToChat(args[0] + " is not a valid category");
                PlayerUtils.addWaterMarkedMessageToChat("categories: " + String.join(", ", categoryNames()));
                return;
            }
            modules = Arsenic.getArsenic().getModuleManager().getModulesByCategory(category);
            header = category.getName() + " modules";
        }

        PlayerUtils.addMessageToChat("---------------");
        PlayerUtils.addWaterMarkedMessageToChat(header + " (" + modules.size() + "):");
        modules.stream().sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName())).forEach(m ->
                PlayerUtils.addWaterMarkedMessageToChat((m.isEnabled() ? "§a" : "§c") + m.getName()));
        PlayerUtils.addMessageToChat("---------------");
    }

    private ModuleCategory matchCategory(String name) {
        for (ModuleCategory category : ModuleCategory.values())
            if (category.getName().equalsIgnoreCase(name) || category.name().equalsIgnoreCase(name))
                return category;
        return null;
    }

    private List<String> categoryNames() {
        return Arrays.stream(ModuleCategory.values()).map(ModuleCategory::getName).collect(Collectors.toList());
    }

    @Override
    protected List<String> getAutoComplete(String str, int arg, List<String> list) {
        return arg == 0 ? autoCompleteHelper(categoryNames(), str) : list;
    }
}
