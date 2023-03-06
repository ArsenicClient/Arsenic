package arsenic.utils.minecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

public class PlayerUtils {

    private PlayerUtils() { throw new IllegalStateException("Utility class should not be initialised");}

    private static Minecraft mc = Minecraft.getMinecraft();

    public static void addMessageToChat(String msg) {
        mc.thePlayer.addChatMessage(new ChatComponentText(msg));
    }

    public static void addWaterMarkedMessageToChat(String msg) {
        addMessageToChat("§7[§cA§7]§r " + msg);
    }
}
