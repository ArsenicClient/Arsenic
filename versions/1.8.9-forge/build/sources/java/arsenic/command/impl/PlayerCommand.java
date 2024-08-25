package arsenic.command.impl;

import arsenic.command.Command;
import arsenic.command.CommandInfo;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static arsenic.utils.java.JavaUtils.autoCompleteHelper;

@CommandInfo(name = "addPlayerEntity", minArgs = 2)
public class PlayerCommand extends Command {

    ArrayList<String> args = new ArrayList<>(Arrays.asList("spawn", "remove"));
    private final Minecraft mc = Minecraft.getMinecraft();
    EntityOtherPlayerMP fakePlayer;

    @Override
    public void execute(String[] args) {
        if (args[0].equalsIgnoreCase("spawn")) {
            fakePlayer = new EntityOtherPlayerMP(mc.theWorld, new GameProfile(UUID.randomUUID(),args[1]));
            fakePlayer.setPosition(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
            mc.theWorld.addEntityToWorld(-1337, fakePlayer);
        }
        if (args[0].equalsIgnoreCase("remove")) {
            if (fakePlayer != null) {
                mc.theWorld.removeEntityFromWorld(fakePlayer.getEntityId());
                fakePlayer = null;
            }
        }
    }

    @Override
    protected List<String> getAutoComplete(String str, int arg, List<String> list) {
        return arg == 0 ? autoCompleteHelper(args, str) : list;
    }
}
