package arsenic.command.impl;

import arsenic.command.Command;
import arsenic.command.CommandInfo;
import arsenic.utils.lag.PingTracker;
import arsenic.utils.minecraft.PlayerUtils;

@CommandInfo(name = "ping")
public class PingCommand extends Command {

    @Override
    public void execute(String[] args) {
        int ping = PingTracker.getPing();
        String source = PingTracker.getSource() == PingTracker.Source.CONFIRMATION
                ? "measured, jitter " + PingTracker.getJitter() + "ms"
                : PingTracker.getSource() == PingTracker.Source.TAB_LIST ? "tab list" : "no data yet, place a block";
        PlayerUtils.addWaterMarkedMessageToChat("Your ping is " + ping + "ms (" + source + "). Or " + ping / 50 + " ticks.");
    }
}
