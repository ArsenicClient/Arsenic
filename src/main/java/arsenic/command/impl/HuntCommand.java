package arsenic.command.impl;

import arsenic.command.Command;
import arsenic.command.CommandInfo;
import arsenic.main.Arsenic;
import arsenic.module.impl.player.AutoHunt;
import arsenic.utils.minecraft.PlayerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static arsenic.utils.java.JavaUtils.autoCompleteHelper;

@CommandInfo(name = "hunt", args = { "name/stop" }, help = "sets who AutoHunt chases and turns it on, or stops it", minArgs = 1)
public class HuntCommand extends Command {

    @Override
    public void execute(String[] args) {
        AutoHunt hunt = Arsenic.getArsenic().getModuleManager().getModuleByClass(AutoHunt.class);
        if (args[0].equalsIgnoreCase("stop")) {
            hunt.setEnabled(false);
            PlayerUtils.addWaterMarkedMessageToChat("Stopped hunting");
            return;
        }
        AutoHunt.setHuntName(args[0]);
        if (hunt.isEnabled())
            PlayerUtils.addWaterMarkedMessageToChat("Now hunting §c" + args[0]);
        else
            hunt.setEnabled(true);
    }

    @Override
    protected List<String> getAutoComplete(String str, int arg, List<String> list) {
        if (arg != 0)
            return list;
        Minecraft mc = Minecraft.getInstance();
        List<String> names = new ArrayList<>();
        names.add("stop");
        if (mc.getConnection() != null) {
            names.addAll(mc.getConnection().getOnlinePlayers().stream()
                    .map(PlayerInfo::getProfile)
                    .map(profile -> profile.name())
                    .filter(n -> mc.player == null || !n.equalsIgnoreCase(mc.player.getName().getString()))
                    .collect(Collectors.toList()));
        }
        return autoCompleteHelper(names, str);
    }
}
