package arsenic.injection.accessor;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Queue;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Mixin(NetworkManager.class)
public interface IMixinNetworkManager {
    @Invoker
    void callDispatchPacket(final Packet p_dispatchPacket_1_, final GenericFutureListener<? extends Future<? super Void>>[] p_dispatchPacket_2_);

    @Invoker
    void callFlushOutboundQueue();

    @Accessor
    Queue<InboundHandlerTuplePacketListener> getOutboundPacketsQueue();

    @Accessor
    ReentrantReadWriteLock getField_181680_j(); // ReadWriteLock
}