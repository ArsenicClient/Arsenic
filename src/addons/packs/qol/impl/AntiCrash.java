import arsenic.module.ModuleTier;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;

/**
 * Drops incoming packets known to crash or freeze a client: custom payloads far bigger than any real plugin message, and
 * particle bursts with absurd counts. Only the incoming side is touched. The limits are generous, and nothing else is changed.
 */
@ModuleInfo(name = "AntiCrash", description = "Drops incoming packets known to crash clients (oversized payloads, particle floods)", category = ModuleCategory.PLAYER, tier = ModuleTier.QOL)
public class AntiCrash extends Module {

    public final DoubleProperty maxParticles = new DoubleProperty("Max Particles", new DoubleValue(100, 10000, 1000, 100));

    @EventLink
    public final Listener<EventPacket.Incoming.Pre> onPacket = event -> {
        // oversized custom payloads are refused by the game's own decoder now, so only particle floods are left to stop
        if (event.getPacket() instanceof ClientboundLevelParticlesPacket particles
                && particles.count() > maxParticles.getValue().getInput())
            event.cancel();
    };
}
