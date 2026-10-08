import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventMouse;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.utils.java.FileUtils;
import arsenic.utils.timer.MSTimer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Developer tool. Records your real left and right clicks to clicks-YYYY-MM-DD.csv in the Arsenic folder: time, button,
 * press or release, and the gap since the previous click. Use it to tune Clicker's CPS and delays against real play.
 */
@ModuleInfo(name = "ClickRecorder", description = "Developer tool: records your real click timings to a CSV file", category = ModuleCategory.PLAYER, tier = ModuleTier.DEV)
public class ClickRecorder extends Module {

    private final ConcurrentLinkedQueue<String> rows = new ConcurrentLinkedQueue<>();
    private final MSTimer sinceClick = MSTimer.expired();

    @EventLink
    public final Listener<EventMouse.Down> onDown = event -> {
        if (event.button > 1) return;
        long gap = sinceClick.getTime();
        sinceClick.reset();
        rows.add(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")) + ","
                + (event.button == 0 ? "left" : "right") + ",press," + gap);
    };

    @EventLink
    public final Listener<EventMouse.Up> onUp = event -> {
        if (event.button > 1) return;
        rows.add(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")) + ","
                + (event.button == 0 ? "left" : "right") + ",release,0");
    };

    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (rows.isEmpty()) return;
        File dir = new File(FileUtils.getArsenicFolderDirAsFile(), "recordings");
        dir.mkdirs();
        File file = new File(dir, "clicks-" + LocalDate.now() + ".csv");
        boolean fresh = !file.exists();
        try (PrintWriter out = new PrintWriter(new FileWriter(file, true))) {
            if (fresh) out.println("time,button,action,gap_ms");
            String row;
            while ((row = rows.poll()) != null) out.println(row);
        } catch (IOException e) {
            rows.clear();
        }
    };
}
