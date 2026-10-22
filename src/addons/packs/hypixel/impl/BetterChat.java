import arsenic.module.ModuleTier;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.chat.Component;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Adds a timestamp in front of each chat line, and a marker on lines that mention your name. The original text and its
 * colours are kept: the prefix is added in front of the server's own component. Action bar text is left alone.
 *
 * Searchable history and restyled chat lines need a chat render hook, which the client does not have yet.
 */
@ModuleInfo(name = "BetterChat", description = "Chat timestamps, and a marker on lines that mention your name", category = ModuleCategory.RENDER, tier = ModuleTier.QOL)
public class BetterChat extends Module {

    public final BooleanProperty timestamps = new BooleanProperty("Timestamps", true);
    public final BooleanProperty highlightMentions = new BooleanProperty("Mention Marker", true);

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    @EventLink
    public final Listener<EventPacket.Incoming.Pre> onPacket = event -> {
        if (!(event.getPacket() instanceof ClientboundSystemChatPacket)) return;
        ClientboundSystemChatPacket chat = (ClientboundSystemChatPacket) event.getPacket();
        if (chat.overlay()) return;                       // action bar

        StringBuilder prefix = new StringBuilder();
        if (timestamps.getValue()) prefix.append("§8[").append(LocalTime.now().format(TIME)).append("] §r");
        if (highlightMentions.getValue() && mentionsMe(chat.content())) prefix.append("§e◆ §r");
        if (prefix.length() == 0) return;

        net.minecraft.network.chat.MutableComponent head = Component.literal(prefix.toString());
        head.append(chat.content());
        event.setPacket(new ClientboundSystemChatPacket(head, chat.overlay()));
    };

    private boolean mentionsMe(Component component) {
        if (mc.player == null) return false;
        return component.getString().toLowerCase(Locale.ROOT).contains(mc.player.getName().getString().toLowerCase(Locale.ROOT));
    }
}
