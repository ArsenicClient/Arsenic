package arsenic.command.impl;

import arsenic.command.Command;
import arsenic.command.CommandInfo;
import arsenic.utils.minecraft.PacketUtil;
import arsenic.utils.minecraft.PlayerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;

@CommandInfo(name = "ping")
public class PingCommand extends Command {

    @Override
    public void execute(String[] args) {

        PlayerUtils.addWaterMarkedMessageToChat("Your ping is " + PacketUtil.getPlayerPing() + "ms. Or " + PacketUtil.getPlayerPingAsTicks() + " ticks.");
    }
}
