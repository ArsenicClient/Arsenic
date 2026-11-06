package arsenic.command.impl;

import arsenic.command.Command;
import arsenic.command.CommandInfo;
import arsenic.utils.java.FakeLobby;
import arsenic.utils.java.FrameProfiler;
import arsenic.utils.minecraft.PlayerUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static arsenic.utils.java.JavaUtils.autoCompleteHelper;

@CommandInfo(name = "profile", args = { "seconds/stop/bots" }, aliases = { "prof" },
        help = "times every listener and render pass for a while, then shows what costs frame time")
public class ProfileCommand extends Command {

    private static final int DEFAULT_SECONDS = 10;
    private static final int MAX_SECONDS = 300;
    private static final int MAX_BOTS = 200;

    @Override
    public void execute(String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("bots")) {
            bots(args.length > 1 ? args[1] : "40");
            return;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("stop")) {
            if (FrameProfiler.isRunning())
                FrameProfiler.stop();
            else
                PlayerUtils.addWaterMarkedMessageToChat("§cthe profiler isn't running");
            return;
        }
        if (FrameProfiler.isRunning()) {
            PlayerUtils.addWaterMarkedMessageToChat("§cthe profiler is already running §7- .profile stop ends it now");
            return;
        }

        int seconds = DEFAULT_SECONDS;
        if (args.length > 0) {
            try {
                seconds = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                PlayerUtils.addWaterMarkedMessageToChat("§c" + args[0] + " isn't a number of seconds");
                return;
            }
        }
        seconds = Math.max(1, Math.min(MAX_SECONDS, seconds));
        FrameProfiler.start(seconds);
        PlayerUtils.addWaterMarkedMessageToChat("§aprofiling for " + seconds + "s §7- play normally, the report comes after");
    }

    /** Client-side fake players to profile a busy lobby anywhere, even in singleplayer. */
    private void bots(String arg) {
        if (arg.equalsIgnoreCase("clear")) {
            PlayerUtils.addWaterMarkedMessageToChat("§aremoved " + FakeLobby.clear() + " fake players");
            return;
        }
        int count;
        try {
            count = Math.max(1, Math.min(MAX_BOTS, Integer.parseInt(arg)));
        } catch (NumberFormatException e) {
            PlayerUtils.addWaterMarkedMessageToChat("§c" + arg + " isn't a number of players");
            return;
        }
        PlayerUtils.addWaterMarkedMessageToChat("§aspawned " + FakeLobby.spawn(count)
                + " fake players §7- .profile bots clear removes them");
    }

    @Override
    public List<String> getAutoComplete(String[] args) {
        if (args.length <= 1)
            return autoCompleteHelper(new ArrayList<>(Arrays.asList("10", "30", "stop", "bots")), args.length == 0 ? "" : args[0]);
        if (args[0].equalsIgnoreCase("bots"))
            return autoCompleteHelper(new ArrayList<>(Arrays.asList("20", "40", "80", "clear")), args[1]);
        return new ArrayList<>();
    }
}
