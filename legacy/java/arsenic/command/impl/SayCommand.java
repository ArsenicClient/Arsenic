package arsenic.command.impl;

import arsenic.command.Command;
import arsenic.command.CommandInfo;
import arsenic.utils.minecraft.PlayerUtils;
import net.minecraft.client.Minecraft;

@CommandInfo(name = "say", args = { "message" }, aliases = { "chat" }, help = "sends a message in chat to the server", minArgs = 1)
public class SayCommand extends Command {

    @Override
    public void execute(String[] args) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            PlayerUtils.addWaterMarkedMessageToChat("you need to be in a world to send a message");
            return;
        }
        // sendChatMessage on the player sends straight to the server, bypassing the command hook
        mc.thePlayer.sendChatMessage(String.join(" ", args));
    }
}
