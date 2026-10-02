package arsenic.injection.accessor;

import net.minecraft.network.play.server.S03PacketTimeUpdate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(S03PacketTimeUpdate.class)
public interface S03PacketTimeUpdateAccessor {
    @Accessor
    long getWorldTime();

    @Accessor
    void setWorldTime(long worldTime);
}
