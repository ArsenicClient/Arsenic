package arsenic.command.impl;

import arsenic.command.Command;
import arsenic.command.CommandInfo;
import arsenic.utils.lag.PingTracker;
import arsenic.utils.minecraft.PlayerUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static arsenic.utils.java.JavaUtils.autoCompleteHelper;

@CommandInfo(name = "ping", args = { "lock/unlock", "ms" },
        help = "shows your ping, or locks the ping Arsenic uses to a fixed value")
public class PingCommand extends Command {

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            int ping = PingTracker.getPing();
            PingTracker.Source src = PingTracker.getSource();
            String source = src == PingTracker.Source.LOCKED ? "locked, .ping unlock to measure again"
                    : src == PingTracker.Source.CONFIRMATION ? "measured, jitter " + PingTracker.getJitter() + "ms"
                    : src == PingTracker.Source.TAB_LIST ? "tab list" : "no data yet, place a block";
            PlayerUtils.addWaterMarkedMessageToChat("Your ping is " + ping + "ms (" + source + "). Or " + ping / 50 + " ticks.");
            return;
        }

        switch (args[0].toLowerCase()) {
            case "lock":
                if (args.length < 2) {
                    PlayerUtils.addWaterMarkedMessageToChat("Usage: .ping lock <ms>");
                    return;
                }
                int ms;
                try {
                    ms = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    PlayerUtils.addWaterMarkedMessageToChat(args[1] + " isn't a number");
                    return;
                }
                if (ms < 0 || ms > 5_000) {
                    PlayerUtils.addWaterMarkedMessageToChat("Ping must be between 0 and 5000ms");
                    return;
                }
                PingTracker.lock(ms);
                PlayerUtils.addWaterMarkedMessageToChat("Ping locked to §a" + ms + "ms§r (" + ms / 50 + " ticks)");
                break;

            case "unlock":
                if (!PingTracker.isLocked()) {
                    PlayerUtils.addWaterMarkedMessageToChat("Ping isn't locked");
                    return;
                }
                PingTracker.unlock();
                PlayerUtils.addWaterMarkedMessageToChat("Ping unlocked, using the measured value again");
                break;

            default:
                PlayerUtils.addWaterMarkedMessageToChat("Unknown action. Correct usage is:");
                PlayerUtils.addWaterMarkedMessageToChat(getUsage());
        }
    }

    @Override
    public List<String> getAutoComplete(String[] args) {
        if (args.length <= 1)
            return autoCompleteHelper(Arrays.asList("lock", "unlock"), args.length == 0 ? "" : args[0]);
        return new ArrayList<>();
    }
}
