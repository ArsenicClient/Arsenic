package arsenic.command.impl;

import arsenic.command.Command;
import arsenic.command.CommandInfo;
import arsenic.utils.lag.LagManager;
import arsenic.utils.minecraft.PlayerUtils;

@CommandInfo(name = "ping")
public class PingCommand extends Command {

    @Override
    public void execute(String[] args) {
        PlayerUtils.addWaterMarkedMessageToChat("Your ping is " + LagManager.getPing() + "ms. Or " + LagManager.getPing() / 20 + " ticks.");
    }
}
