import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.utils.java.FileUtils;
import arsenic.utils.rotations.RotationUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Developer tool. Records how far your real camera turned each tick (yaw and pitch change) to rotations-YYYY-MM-DD.csv,
 * only while it moves. Compare it with the silent rotation's turn speed and jitter so the silent rotations look like
 * your own hand. It only reads the camera; it never changes a rotation.
 */
@ModuleInfo(name = "RotationRecorder", description = "Developer tool: records your real camera turns to a CSV file", category = ModuleCategory.PLAYER, tier = ModuleTier.DEV)
public class RotationRecorder extends Module {

    private final ConcurrentLinkedQueue<String> rows = new ConcurrentLinkedQueue<>();

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        float dYaw = RotationUtils.getYawDifference(mc.thePlayer.rotationYaw, mc.thePlayer.prevRotationYaw);
        float dPitch = mc.thePlayer.rotationPitch - mc.thePlayer.prevRotationPitch;
        if (Math.abs(dYaw) < 0.01f && Math.abs(dPitch) < 0.01f) return;
        rows.add(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")) + ","
                + String.format("%.3f,%.3f", dYaw, dPitch));
    };

    @EventLink
    public final Listener<EventTick> onFlush = event -> {
        if (rows.isEmpty()) return;
        File dir = new File(FileUtils.getArsenicFolderDirAsFile(), "recordings");
        dir.mkdirs();
        File file = new File(dir, "rotations-" + LocalDate.now() + ".csv");
        boolean fresh = !file.exists();
        try (PrintWriter out = new PrintWriter(new FileWriter(file, true))) {
            if (fresh) out.println("time,delta_yaw,delta_pitch");
            String row;
            while ((row = rows.poll()) != null) out.println(row);
        } catch (IOException e) {
            rows.clear();
        }
    };
}
