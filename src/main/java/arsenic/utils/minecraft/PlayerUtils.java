package arsenic.utils.minecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;

public class PlayerUtils {

    private PlayerUtils() { throw new IllegalStateException("Utility class should not be initialised");}

    private static Minecraft mc = Minecraft.getMinecraft();

    public static void addMessageToChat(String msg) {
        mc.thePlayer.addChatMessage(new ChatComponentText(msg));
    }

    public static void addWaterMarkedMessageToChat(String msg) {
        addMessageToChat("§7[§cA§7]§r " + msg);
    }
    public static boolean playerOverAir() {
        return mc.theWorld.isAirBlock(getBlockUnderPlayer());
    }

    public static BlockPos getBlockUnderPlayer() {
        double x = mc.thePlayer.posX;
        double y = mc.thePlayer.posY - 1.0D;
        double z = mc.thePlayer.posZ;
        return new BlockPos(MathHelper.floor_double(x), MathHelper.floor_double(y), MathHelper.floor_double(z));
    }

}
