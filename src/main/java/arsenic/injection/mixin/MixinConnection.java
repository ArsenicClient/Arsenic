package arsenic.injection.mixin;

import arsenic.event.impl.EventPacket;
import arsenic.main.Arsenic;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class MixinConnection {

    @Shadow
    private volatile PacketListener packetListener;

    @Shadow
    public abstract void send(Packet<?> packet, io.netty.channel.ChannelFutureListener listener, boolean flush);

    @Unique
    private boolean arsenic$resending;

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("HEAD"), cancellable = true)
    private void arsenic$send(Packet<?> packet, io.netty.channel.ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        if (arsenic$resending)
            return;
        EventPacket e = new EventPacket.OutGoing(packet);
        Arsenic.getArsenic().getEventManager().post(e);
        if (e.isCancelled()) {
            ci.cancel();
        } else if (e.getPacket() != packet) {
            // a listener swapped the packet: send the replacement instead, without re-firing
            ci.cancel();
            arsenic$resending = true;
            try {
                send(e.getPacket(), listener, flush);
            } finally {
                arsenic$resending = false;
            }
        }
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void arsenic$receive(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
        // servers can probe for the client by playing sounds from our resource namespace
        if (arsenic$isOwnSound(packet)) {
            ci.cancel();
            return;
        }
        EventPacket e = new EventPacket.Incoming.Pre(packet);
        Arsenic.getArsenic().getEventManager().post(e);
        if (e.isCancelled()) {
            ci.cancel();
        } else if (e.getPacket() != packet && packetListener != null) {
            ci.cancel();
            arsenic$handle(e.getPacket(), packetListener);
        }
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At("RETURN"))
    private void arsenic$receivePost(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
        Arsenic.getArsenic().getEventManager().post(new EventPacket.Incoming.Post(packet));
    }

    @Unique
    private static boolean arsenic$isOwnSound(Packet<?> packet) {
        Holder<SoundEvent> sound = packet instanceof ClientboundSoundPacket p ? p.getSound()
                : packet instanceof ClientboundSoundEntityPacket p ? p.getSound() : null;
        return sound != null && "arsenic".equals(sound.value().location().getNamespace());
    }

    @Unique
    @SuppressWarnings("unchecked")
    private static <T extends PacketListener> void arsenic$handle(Packet<T> packet, PacketListener listener) {
        try {
            packet.handle((T) listener);
        } catch (net.minecraft.server.RunningOnDifferentThreadException ignored) {
            // re-queued onto the main thread by the handler itself, as vanilla does
        }
    }
}
