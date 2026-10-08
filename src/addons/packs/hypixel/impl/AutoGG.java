import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.TextProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.timer.MSTimer;
import net.minecraft.network.play.server.S02PacketChat;

import java.util.Locale;

/**
 * Sends a message (default "gg") once a game ends. The end is spotted from chat lines listed in "End Triggers" (comma
 * separated, case-insensitive); the default list covers the usual end-of-game lines and has not been checked on every
 * mode. The message is sent after a short random delay so it does not look scripted, and only once per game: it re-arms
 * after 60 seconds or when the game changes.
 */
@ModuleInfo(name = "AutoGG", description = "Sends a message once after each game ends", category = ModuleCategory.PLAYER)
public class AutoGG extends Module {

    public final TextProperty message = new TextProperty("Message", "gg", 40);
    public final TextProperty endTriggers = new TextProperty("End Triggers", "1st Killer,Winning Team,Winner:,won the game", 200);
    public final DoubleProperty delay = new DoubleProperty("Delay (s)", new DoubleValue(0.5, 5, 1.5, 0.1));

    private volatile boolean endSeen;
    private final MSTimer sinceEnd = new MSTimer();
    private boolean sent;

    @Override
    protected void onEnable() {
        endSeen = false;
        sent = false;
    }

    @EventLink
    public final Listener<EventPacket.Incoming.Post> onPacket = event -> {
        if (!(event.getPacket() instanceof S02PacketChat)) return;
        String text = ((S02PacketChat) event.getPacket()).getChatComponent().getUnformattedText().toLowerCase(Locale.ROOT);
        for (String trigger : endTriggers.getValue().split(",")) {
            String t = trigger.trim().toLowerCase(Locale.ROOT);
            if (!t.isEmpty() && text.contains(t)) {
                endSeen = true;
                sinceEnd.reset();
                return;
            }
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (sent && sinceEnd.hasTimeElapsed(60000, false)) sent = false;
        if (!endSeen || sent) return;
        if (sinceEnd.hasTimeElapsed((long) (delay.getValue().getInput() * 1000), false)) {
            sent = true;
            endSeen = false;
            if (!message.getValue().isEmpty()) mc.thePlayer.sendChatMessage(message.getValue());
        }
    };
}
