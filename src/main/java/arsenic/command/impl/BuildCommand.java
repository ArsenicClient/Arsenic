package arsenic.command.impl;

import arsenic.command.Command;
import arsenic.command.CommandInfo;
import arsenic.main.Arsenic;
import arsenic.module.impl.world.BuildBattle;
import arsenic.utils.minecraft.PlayerUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static arsenic.utils.java.JavaUtils.autoCompleteHelper;

/**
 * Starts BuildBattle on a schematic from the schematics folder: {@code .build} or {@code .build latest} for the last
 * one generated, {@code .build <name>}, {@code .build list}, {@code .build stop}.
 */
@CommandInfo(name = "build", args = { "latest|name|list|stop" }, help = "builds a schematic from the schematics folder")
public class BuildCommand extends Command {

    @Override
    public void execute(String[] args) {
        // a module left out of this jar's tier is only kept by class, never by name
        if (Arsenic.getArsenic().getModuleManager().getModuleByName("BuildBattle") == null) {
            PlayerUtils.addWaterMarkedMessageToChat("BuildBattle is not in this jar");
            return;
        }
        BuildBattle module = Arsenic.getArsenic().getModuleManager().getModuleByClass(BuildBattle.class);
        String arg = args.length == 0 ? "latest" : String.join(" ", args).trim();
        if (arg.equalsIgnoreCase("stop")) {
            module.setEnabled(false);
            return;
        }
        if (arg.equalsIgnoreCase("list")) {
            List<String> names = names();
            PlayerUtils.addWaterMarkedMessageToChat(names.isEmpty() ? "no schematics in " + BuildBattle.folder().getPath()
                    : "schematics: " + String.join(", ", names));
            return;
        }
        String name = arg.endsWith(".schem") ? arg.substring(0, arg.length() - 6) : arg;
        if (!new File(BuildBattle.folder(), name + ".schem").isFile()) {
            PlayerUtils.addWaterMarkedMessageToChat("no " + name + ".schem in " + BuildBattle.folder().getPath());
            return;
        }
        module.schematic.setValue(name + ".schem");
        // turning it on again reloads the file and builds from where the player stands
        if (module.isEnabled()) module.setEnabled(false);
        module.setEnabled(true);
    }

    private static List<String> names() {
        File[] files = BuildBattle.folder().listFiles((d, n) -> n.endsWith(".schem"));
        List<String> names = new ArrayList<>();
        if (files == null) return names;
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (File f : files) names.add(f.getName().substring(0, f.getName().length() - 6));
        return names;
    }

    @Override
    protected List<String> getAutoComplete(String str, int arg, List<String> list) {
        if (arg != 0) return list;
        List<String> options = new ArrayList<>(List.of("latest", "list", "stop"));
        for (String n : names()) if (!options.contains(n)) options.add(n);
        return autoCompleteHelper(options, str);
    }
}
