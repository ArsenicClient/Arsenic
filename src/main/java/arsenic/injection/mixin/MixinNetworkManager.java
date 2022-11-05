package arsenic.injection.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import arsenic.event.impl.EventPacket;
import arsenic.main.Arsenic;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;

@Mixin(NetworkManager.class)
public class MixinNetworkManager {

    @Inject(method = "sendPacket(Lnet/minecraft/network/Packet;)V", at = @At("HEAD"), cancellable = true)
    public void sendPacket(Packet p_sendPacket_1_, CallbackInfo ci) {
        EventPacket e = new EventPacket.OutGoing(p_sendPacket_1_);

        Arsenic.getArsenic().getEventManager().post(e);

        p_sendPacket_1_ = e.getPacket();
        if (e.isCancelled())
            ci.cancel();
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/Packet;)V", at = @At("HEAD"), cancellable = true)
    public void receivePacket(ChannelHandlerContext p_channelRead0_1_, Packet p_channelRead0_2_, CallbackInfo ci) {
        EventPacket e = new EventPacket.Incoming(p_channelRead0_2_);

        Arsenic.getArsenic().getEventManager().post(e);

        p_channelRead0_2_ = e.getPacket();
        if (e.isCancelled())
            ci.cancel();
    }

}
