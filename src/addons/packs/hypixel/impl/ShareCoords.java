import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.ButtonProperty;
import arsenic.module.property.impl.TextProperty;

/**
 * Sends your coordinates to chat when you press the button in the settings. Nothing is sent automatically. The prefix
 * decides where the line goes; the default "/ac " sends it to all chat, and team chat needs its own prefix.
 */
@ModuleInfo(name = "ShareCoords", description = "Sends your coordinates to chat when you press its button", category = ModuleCategory.PLAYER)
public class ShareCoords extends Module {

    public final TextProperty prefix = new TextProperty("Prefix", "/ac ", 32);
    public final ButtonProperty send = new ButtonProperty("Send Coordinates", this::sendNow);

    private void sendNow() {
        if (mc.thePlayer == null) return;
        mc.thePlayer.sendChatMessage(prefix.getValue() + "x " + (int) mc.thePlayer.posX + ", y "
                + (int) mc.thePlayer.posY + ", z " + (int) mc.thePlayer.posZ);
    }
}
