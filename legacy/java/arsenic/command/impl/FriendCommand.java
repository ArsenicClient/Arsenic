package arsenic.command.impl;

import arsenic.command.Command;
import arsenic.command.CommandInfo;
import arsenic.config.FriendManager;
import arsenic.main.Arsenic;
import arsenic.utils.minecraft.PlayerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static arsenic.utils.java.JavaUtils.autoCompleteHelper;

@CommandInfo(name = "friend", args = { "add/remove/list/clear", "name" }, aliases = { "friends", "f" },
        help = "manages players KillAura ignores and ESP draws in the friend colour", minArgs = 1)
public class FriendCommand extends Command {

    @Override
    public void execute(String[] args) {
        FriendManager friends = Arsenic.getArsenic().getFriendManager();
        String action = args[0].toLowerCase();

        switch (action) {
            case "add":
            case "remove":
            case "del":
                if (args.length < 2) {
                    PlayerUtils.addWaterMarkedMessageToChat("Usage: .friend " + action + " <name>");
                    return;
                }
                String name = args[1];
                if (action.equals("add")) {
                    PlayerUtils.addWaterMarkedMessageToChat(friends.add(name)
                            ? "§a" + name + "§r is now a friend"
                            : name + " is already a friend");
                } else {
                    PlayerUtils.addWaterMarkedMessageToChat(friends.remove(name)
                            ? "§c" + name + "§r is no longer a friend"
                            : name + " isn't a friend");
                }
                Arsenic.getArsenic().getConfigManager().saveClientConfig();
                break;

            case "list":
                List<String> list = friends.getFriends();
                PlayerUtils.addWaterMarkedMessageToChat(list.isEmpty()
                        ? "You have no friends added"
                        : "Friends (" + list.size() + "): §a" + String.join("§r, §a", list));
                break;

            case "clear":
                friends.clear();
                Arsenic.getArsenic().getConfigManager().saveClientConfig();
                PlayerUtils.addWaterMarkedMessageToChat("Cleared all friends");
                break;

            default:
                PlayerUtils.addWaterMarkedMessageToChat("Unknown action. Correct usage is:");
                PlayerUtils.addWaterMarkedMessageToChat(getUsage());
        }
    }

    @Override
    public List<String> getAutoComplete(String[] args) {
        if (args.length <= 1)
            return autoCompleteHelper(Arrays.asList("add", "remove", "list", "clear"), args.length == 0 ? "" : args[0]);
        if (args.length != 2)
            return new ArrayList<>();
        String typed = args[1];
        FriendManager friends = Arsenic.getArsenic().getFriendManager();
        if (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("del"))
            return autoCompleteHelper(friends.getFriends(), typed);
        if (args[0].equalsIgnoreCase("add"))
            return autoCompleteHelper(onlinePlayers().stream()
                    .filter(n -> !friends.isFriend(n)).collect(Collectors.toList()), typed);
        return new ArrayList<>();
    }

    private static List<String> onlinePlayers() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getNetHandler() == null)
            return new ArrayList<>();
        return mc.getNetHandler().getPlayerInfoMap().stream()
                .map(NetworkPlayerInfo::getGameProfile)
                .map(profile -> profile.getName())
                .filter(n -> mc.thePlayer == null || !n.equalsIgnoreCase(mc.thePlayer.getName()))
                .collect(Collectors.toList());
    }
}
