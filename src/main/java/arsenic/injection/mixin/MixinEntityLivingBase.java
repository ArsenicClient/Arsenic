package arsenic.injection.mixin;

import arsenic.runtime.hooks.PlayerHooks;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hook logic lives in {@link PlayerHooks}, shared with the injected client. */
@Mixin(EntityLivingBase.class)
public abstract class MixinEntityLivingBase {

    @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
    protected void jump(CallbackInfo ci) {
        if (PlayerHooks.jumpHead((EntityLivingBase) (Object) this))
            ci.cancel();
    }

    @Inject(method = "onLivingUpdate", at = @At("HEAD"))
    private void headLiving(CallbackInfo callbackInfo) {
        PlayerHooks.livingUpdateHead((EntityLivingBase) (Object) this);
    }
}
