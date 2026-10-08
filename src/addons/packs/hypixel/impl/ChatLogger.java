import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.utils.java.FileUtils;
import net.minecraft.network.play.server.S02PacketChat;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Writes every chat line to a daily file, chatlogs/chat-YYYY-MM-DD.txt, in the Arsenic folder. It is off until you enable
 * it. Chat holds other players' messages, so keep the logs private.
 */
@ModuleInfo(name = "ChatLogger", description = "Writes chat to a daily log file in the Arsenic folder", category = ModuleCategory.PLAYER)
public class ChatLogger extends Module {

    private final ConcurrentLinkedQueue<String> lines = new ConcurrentLinkedQueue<>();

    @EventLink
    public final Listener<EventPacket.Incoming.Post> onPacket = event -> {
        if (event.getPacket() instanceof S02PacketChat) {
            lines.add(((S02PacketChat) event.getPacket()).getChatComponent().getUnformattedText());
        }
    };

    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (lines.isEmpty()) return;
        File dir = new File(FileUtils.getArsenicFolderDirAsFile(), "chatlogs");
        dir.mkdirs();
        File file = new File(dir, "chat-" + LocalDate.now() + ".txt");
        String stamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        try (PrintWriter out = new PrintWriter(new FileWriter(file, true))) {
            String line;
            while ((line = lines.poll()) != null) out.println("[" + stamp + "] " + line);
        } catch (IOException e) {
            lines.clear();
        }
    };
}
