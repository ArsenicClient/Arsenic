package arsenic.injection.mixin;

import arsenic.runtime.hooks.MiscHooks;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hook logic lives in {@link MiscHooks}, shared with the injected client. */
@Mixin(NetworkManager.class)
public class MixinNetworkManager {

    @Inject(method = "sendPacket(Lnet/minecraft/network/Packet;)V", at = @At("HEAD"), cancellable = true)
    public void sendPacketHead(Packet packet, CallbackInfo ci) {
        if (MiscHooks.sendPacketHead((NetworkManager) (Object) this, packet))
            ci.cancel();
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/Packet;)V", at = @At("HEAD"), cancellable = true)
    public void receivePacketHead(ChannelHandlerContext ctx, Packet packet, CallbackInfo ci) {
        if (MiscHooks.channelRead0Head((NetworkManager) (Object) this, ctx, packet))
            ci.cancel();
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/Packet;)V", at = @At("RETURN"))
    public void receivePacketReturn(ChannelHandlerContext ctx, Packet packet, CallbackInfo ci) {
        MiscHooks.channelRead0Return((NetworkManager) (Object) this, ctx, packet);
    }
}
