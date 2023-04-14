package arsenic.injection.accessor;

import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.network.NetHandlerPlayClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerControllerMP.class)
public interface IMixinPlayerControllerMp {
    @Accessor
    NetHandlerPlayClient getNetClientHandler();

}
