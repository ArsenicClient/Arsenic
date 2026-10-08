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
 * After a game ends, waits for the chosen delay and sends /play with the mode you set (for example bedwars_eight_one).
 * The end is spotted from the same chat lines as AutoGG. Sends once per game, and re-arms after 60 seconds.
 * The queue timer shown in some other clients is not included.
 */
@ModuleInfo(name = "AutoPlay", description = "After a game ends, queues the mode you chose with /play", category = ModuleCategory.PLAYER)
public class AutoPlay extends Module {

    public final TextProperty mode = new TextProperty("Mode", "bedwars_eight_one", 60);
    public final TextProperty endTriggers = new TextProperty("End Triggers", "1st Killer,Winning Team,Winner:,won the game", 200);
    public final DoubleProperty delay = new DoubleProperty("Delay (s)", new DoubleValue(2, 30, 8, 1));

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
            if (!mode.getValue().trim().isEmpty()) mc.thePlayer.sendChatMessage("/play " + mode.getValue().trim());
        }
    };
}
