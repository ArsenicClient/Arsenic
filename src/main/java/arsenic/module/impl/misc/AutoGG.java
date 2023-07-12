package arsenic.module.impl.misc;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.module.Module;
import net.minecraft.network.play.server.S02PacketChat;

public class AutoGG extends Module {
    @EventLink
    public final Listener<EventPacket.Incoming> onPacket = event -> {
        // Credits geuxy
        if (event.getPacket() instanceof S02PacketChat) {
            String unformattedText = (((S02PacketChat) event.getPacket()).getChatComponent().getUnformattedText());

            String[] look = {
                    "You won! Want to play again? Click here! ",
                    "You lost! Want to play again? Click here! ",
                    "You died! Want to play again? Click here! ",
            };

            if(unformattedText == null) {
                return;
            }

            for(String s : look) {
                if (unformattedText.contains(s)) {
                    mc.thePlayer.sendChatMessage("gg");
                }
            }
        }
    };
}
