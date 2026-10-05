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
import net.minecraft.network.play.server.S29PacketSoundEffect;

import java.util.Locale;

@Mixin(NetworkManager.class)
public class MixinNetworkManager {

    @Inject(method = "sendPacket(Lnet/minecraft/network/Packet;)V", at = @At("HEAD"), cancellable = true)
    public void sendPacketHead(Packet p_sendPacket_1_, CallbackInfo ci) {
        EventPacket e = new EventPacket.OutGoing(p_sendPacket_1_);
        Arsenic.getArsenic().getEventManager().post(e);
        p_sendPacket_1_ = e.getPacket();
        if (e.isCancelled())
            ci.cancel();
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/Packet;)V", at = @At("HEAD"), cancellable = true)
    public void receivePacketHead(ChannelHandlerContext p_channelRead0_1_, Packet p_channelRead0_2_, CallbackInfo ci) {
        // servers can probe for the client by playing sounds from our resource domain
        if (p_channelRead0_2_ instanceof S29PacketSoundEffect) {
            String name = ((S29PacketSoundEffect) p_channelRead0_2_).getSoundName();
            if (name != null && name.trim().toLowerCase(Locale.ROOT).startsWith("arsenic:")) {
                ci.cancel();
                return;
            }
        }
        EventPacket e = new EventPacket.Incoming.Pre(p_channelRead0_2_);

        Arsenic.getArsenic().getEventManager().post(e);

        p_channelRead0_2_ = e.getPacket();
        if (e.isCancelled())
            ci.cancel();
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/Packet;)V", at = @At("RETURN"))
    public void receivePacketReturn(ChannelHandlerContext p_channelRead0_1_, Packet p_channelRead0_2_, CallbackInfo ci) {
        Arsenic.getArsenic().getEventManager().post(new EventPacket.Incoming.Post(p_channelRead0_2_));
    }
}
