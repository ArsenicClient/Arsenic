package arsenic.command;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;

public class CommandManager {

    public CommandManager() {

    }

    public void executeCommand(String str) {
        Minecraft.getMinecraft().thePlayer.sendChatMessage("sent command " + str);
    }

    public ArrayList<String> getAutoCompletions(String str) {
        return new ArrayList<String>();
    }



}
