import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.java.FileUtils;
import net.minecraft.network.Packet;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Developer tool. Writes the packets you send and receive, with their field values, to a daily file in
 * packetlogs/ in the Arsenic folder, so you can see what the server gets when you look into a flag. It never changes or
 * drops a packet. Movement packets are very frequent, so they are skipped by default.
 */
@ModuleInfo(name = "PacketLogger", description = "Developer tool: logs sent and received packets to a file", category = ModuleCategory.PLAYER, tier = ModuleTier.DEV)
public class PacketLogger extends Module {

    public final BooleanProperty outgoing = new BooleanProperty("Outgoing", true);
    public final BooleanProperty incoming = new BooleanProperty("Incoming", true);
    public final BooleanProperty skipMovement = new BooleanProperty("Skip Movement", true);

    private static final int MAX_LINE = 400;

    private final ConcurrentLinkedQueue<String> lines = new ConcurrentLinkedQueue<>();

    @EventLink
    public final Listener<EventPacket.OutGoing> onOut = event -> {
        if (outgoing.getValue()) record("OUT", event.getPacket());
    };

    @EventLink
    public final Listener<EventPacket.Incoming.Post> onIn = event -> {
        if (incoming.getValue()) record("IN ", event.getPacket());
    };

    private void record(String dir, Packet<?> packet) {
        if (packet == null) return;
        String name = packet.getClass().getSimpleName();
        if (skipMovement.getValue() && name.startsWith("C03")) return;
        StringBuilder sb = new StringBuilder(dir).append(' ').append(name).append(' ');
        for (Field f : packet.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            try {
                f.setAccessible(true);
                sb.append(f.getName()).append('=').append(f.get(packet)).append(' ');
            } catch (Exception ignored) {
            }
            if (sb.length() > MAX_LINE) break;
        }
        if (sb.length() > MAX_LINE) sb.setLength(MAX_LINE);
        lines.add(sb.toString());
    }

    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (lines.isEmpty()) return;
        File dir = new File(FileUtils.getArsenicFolderDirAsFile(), "packetlogs");
        dir.mkdirs();
        File file = new File(dir, "packets-" + LocalDate.now() + ".txt");
        String stamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
        try (PrintWriter out = new PrintWriter(new FileWriter(file, true))) {
            String line;
            while ((line = lines.poll()) != null) out.println(stamp + " " + line);
        } catch (IOException e) {
            lines.clear();
        }
    };
}
