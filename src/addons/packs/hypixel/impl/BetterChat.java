import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Adds a timestamp in front of each chat line, and a marker on lines that mention your name. The original text and its
 * colours are kept: the prefix is added in front of the server's own component. Action bar text is left alone.
 *
 * Searchable history and restyled chat lines need a chat render hook, which the client does not have yet.
 */
@ModuleInfo(name = "BetterChat", description = "Chat timestamps, and a marker on lines that mention your name", category = ModuleCategory.RENDER)
public class BetterChat extends Module {

    public final BooleanProperty timestamps = new BooleanProperty("Timestamps", true);
    public final BooleanProperty highlightMentions = new BooleanProperty("Mention Marker", true);

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    @EventLink
    public final Listener<EventPacket.Incoming.Pre> onPacket = event -> {
        if (!(event.getPacket() instanceof S02PacketChat)) return;
        S02PacketChat chat = (S02PacketChat) event.getPacket();
        if (chat.getType() == 2) return;                       // action bar

        StringBuilder prefix = new StringBuilder();
        if (timestamps.getValue()) prefix.append("§8[").append(LocalTime.now().format(TIME)).append("] §r");
        if (highlightMentions.getValue() && mentionsMe(chat.getChatComponent())) prefix.append("§e◆ §r");
        if (prefix.length() == 0) return;

        IChatComponent head = new ChatComponentText(prefix.toString());
        head.appendSibling(chat.getChatComponent());
        event.setPacket(new S02PacketChat(head, chat.getType()));
    };

    private boolean mentionsMe(IChatComponent component) {
        if (mc.thePlayer == null || mc.thePlayer.getName() == null) return false;
        return component.getUnformattedText().toLowerCase(Locale.ROOT).contains(mc.thePlayer.getName().toLowerCase(Locale.ROOT));
    }
}
