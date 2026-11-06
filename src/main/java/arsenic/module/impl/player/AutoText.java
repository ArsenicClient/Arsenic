package arsenic.module.impl.player;

import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.TextProperty;
import net.minecraft.client.multiplayer.ClientPacketListener;

/** Sends a preset chat message or command once when toggled, then switches itself off (bind it to a key). */
@ModuleInfo(name = "AutoText", description = "Sends a preset chat message or /command when toggled", category = ModuleCategory.PLAYER, tier = ModuleTier.QOL)
public class AutoText extends Module {

    public final TextProperty text = new TextProperty("Text", "gg", 256);

    @Override
    protected void onEnable() {
        ClientPacketListener connection = mc.getConnection();
        String message = text.getValue() == null ? "" : text.getValue().trim();
        if (mc.player != null && connection != null && !message.isEmpty()) {
            if (message.startsWith("/")) {
                String command = message.substring(1).trim();
                if (!command.isEmpty())
                    connection.sendCommand(command);
            } else {
                connection.sendChat(message);
            }
        }
        // One-shot: switch straight back off, like the original
        setEnabled(false);
    }
}
