package arsenic.injection.accessor;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import net.minecraft.network.Packet;

public class InboundHandlerTuplePacketListener {
    private final Packet packet;
    private final GenericFutureListener<? extends Future<? super Void>>[] futureListeners;

    public InboundHandlerTuplePacketListener(Packet p_i45146_1_, GenericFutureListener<? extends Future<? super Void>>... p_i45146_2_) {
        this.packet = p_i45146_1_;
        this.futureListeners = p_i45146_2_;
    }
}