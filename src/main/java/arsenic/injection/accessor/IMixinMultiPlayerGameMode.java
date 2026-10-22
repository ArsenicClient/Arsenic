package arsenic.injection.accessor;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Block breaking state (the delay between blocks and how far the current block is mined) and slot syncing. */
@Mixin(MultiPlayerGameMode.class)
public interface IMixinMultiPlayerGameMode {

    @Accessor("destroyDelay")
    int getBlockHitDelay();

    @Accessor("destroyDelay")
    void setBlockHitDelay(int delay);

    @Accessor("destroyProgress")
    float getCurBlockDamageMP();

    @Accessor("destroyProgress")
    void setCurBlockDamageMP(float progress);

    @Accessor("isDestroying")
    boolean isHittingBlock();

    /** Sends the selected hotbar slot now if it changed, as 1.8's syncCurrentPlayItem did. */
    @Invoker("ensureHasSentCarriedItem")
    void syncCurrentPlayItem();
}
