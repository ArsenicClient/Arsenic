package arsenic.command.impl;

import arsenic.command.Command;
import arsenic.command.CommandInfo;
import arsenic.module.impl.world.BuildBattle;
import arsenic.utils.minecraft.BuildShots;
import arsenic.utils.minecraft.PlayerUtils;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static arsenic.utils.java.JavaUtils.autoCompleteHelper;

/**
 * Screenshots every schematic under a folder (default: the schematics folder) on the plot around 0,0 of this
 * singleplayer world: {@code .buildshot [folder] [redo]}. See {@link BuildShots}.
 */
@CommandInfo(name = "buildshot", args = { "folder", "redo" }, help = "screenshots schematics in game for scoring (singleplayer)")
public class BuildShotCommand extends Command {

    @Override
    public void execute(String[] args) {
        boolean redo = args.length > 0 && args[args.length - 1].equalsIgnoreCase("redo");
        String[] folderArgs = redo ? Arrays.copyOf(args, args.length - 1) : args;
        File folder = folderArgs.length == 0 ? BuildBattle.folder() : new File(String.join(" ", folderArgs).trim());
        if (!folder.isDirectory()) {
            PlayerUtils.addWaterMarkedMessageToChat("no folder " + folder.getPath());
            return;
        }
        PlayerUtils.addWaterMarkedMessageToChat(BuildShots.start(folder, redo));
    }

    @Override
    protected List<String> getAutoComplete(String str, int arg, List<String> list) {
        return arg == 1 ? autoCompleteHelper(List.of("redo"), str) : list;
    }
}
