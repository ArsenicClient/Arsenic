package arsenic.module.impl.misc.chargetp;

import arsenic.main.Arsenic;
import arsenic.utils.minecraft.PlayerUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;

public class CustomNetworkHandler extends NetworkManager {
    public CustomNetworkHandler(EnumPacketDirection packetDirection) {
        super(packetDirection);
    }

    @Override
    public void sendPacket(Packet packetIn) {
        super.sendPacket(packetIn);
    }

    @Override
    public void sendPacket(Packet packetIn, GenericFutureListener<? extends Future<? super Void>> listener, GenericFutureListener<? extends Future<? super Void>>... listeners) {
        super.sendPacket(packetIn, listener, listeners);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext p_channelRead0_1_, Packet p_channelRead0_2_) throws Exception {
        Arsenic.getArsenic().getModuleManager().getModuleByClass(ChargeTp.class).addToPacketList(p_channelRead0_2_);
        super.channelRead0(p_channelRead0_1_, p_channelRead0_2_);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        super.channelRead(ctx, msg);
    }
}
