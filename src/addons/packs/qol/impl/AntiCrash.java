import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import net.minecraft.network.play.server.S2APacketParticles;
import net.minecraft.network.play.server.S3FPacketCustomPayload;

/**
 * Drops incoming packets known to crash or freeze a client: custom payloads far bigger than any real plugin message, and
 * particle bursts with absurd counts. Only the incoming side is touched. The limits are generous, and nothing else is changed.
 */
@ModuleInfo(name = "AntiCrash", description = "Drops incoming packets known to crash clients (oversized payloads, particle floods)", category = ModuleCategory.PLAYER)
public class AntiCrash extends Module {

    public final DoubleProperty maxPayload = new DoubleProperty("Max Payload (KB)", new DoubleValue(8, 1024, 64, 8));
    public final DoubleProperty maxParticles = new DoubleProperty("Max Particles", new DoubleValue(100, 10000, 1000, 100));

    @EventLink
    public final Listener<EventPacket.Incoming.Pre> onPacket = event -> {
        if (event.getPacket() instanceof S3FPacketCustomPayload) {
            int bytes = ((S3FPacketCustomPayload) event.getPacket()).getBufferData().readableBytes();
            if (bytes > maxPayload.getValue().getInput() * 1024) event.cancel();
        } else if (event.getPacket() instanceof S2APacketParticles) {
            if (((S2APacketParticles) event.getPacket()).getParticleCount() > maxParticles.getValue().getInput()) event.cancel();
        }
    };
}
