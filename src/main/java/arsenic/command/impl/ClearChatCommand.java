package arsenic.command.impl;

import arsenic.command.Command;
import arsenic.command.CommandInfo;
import net.minecraft.client.Minecraft;

@CommandInfo(name = "clearchat", aliases = { "cc", "clear" }, help = "clears your chat history")
public class ClearChatCommand extends Command {

    @Override
    public void execute(String[] args) {
        Minecraft.getMinecraft().ingameGUI.getChatGUI().clearChatMessages();
    }
}
