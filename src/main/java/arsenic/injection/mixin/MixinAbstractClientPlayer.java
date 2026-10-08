package arsenic.injection.mixin;

import arsenic.runtime.hooks.PlayerHooks;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hook logic lives in {@link PlayerHooks}, shared with the injected client. */
@Mixin(AbstractClientPlayer.class)
@SideOnly(Side.CLIENT)
public abstract class MixinAbstractClientPlayer {

    @Inject(method = "getLocationCape", at = @At("HEAD"), cancellable = true)
    private void getCape(CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation cape = PlayerHooks.getLocationCape((AbstractClientPlayer) (Object) this);
        if (cape != null)
            cir.setReturnValue(cape);
    }
}
