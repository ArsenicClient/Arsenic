package arsenic.command.impl;

import arsenic.command.Command;
import arsenic.command.CommandInfo;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static arsenic.utils.java.JavaUtils.autoCompleteHelper;

@CommandInfo(name = "addPlayerEntity", minArgs = 2)
public class PlayerCommand extends Command {

    ArrayList<String> args = new ArrayList<>(Arrays.asList("spawn", "remove"));
    private final Minecraft mc = Minecraft.getInstance();
    RemotePlayer fakePlayer;

    @Override
    public void execute(String[] args) {
        if (args[0].equalsIgnoreCase("spawn")) {
            fakePlayer = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(),args[1]));
            fakePlayer.setId(-1337);
            fakePlayer.setPos(mc.player.getX(), mc.player.getY(), mc.player.getZ());
            mc.level.addEntity(fakePlayer);
        }
        if (args[0].equalsIgnoreCase("remove")) {
            if (fakePlayer != null) {
                mc.level.removeEntity(fakePlayer.getId(), net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                fakePlayer = null;
            }
        }
    }

    @Override
    protected List<String> getAutoComplete(String str, int arg, List<String> list) {
        return arg == 0 ? autoCompleteHelper(args, str) : list;
    }
}
