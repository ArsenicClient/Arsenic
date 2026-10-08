package arsenic.injection.mixin;

import arsenic.runtime.hooks.PlayerHooks;
import net.minecraft.client.entity.EntityPlayerSP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hook logic lives in {@link PlayerHooks}, shared with the injected client. */
@Mixin(priority = 1111, value = EntityPlayerSP.class)
public abstract class MixinEntityPlayerSP {

    @Inject(method = "onUpdateWalkingPlayer", at = @At("HEAD"), cancellable = true)
    private void onUpdateWalkingPlayerPre(CallbackInfo ci) {
        if (PlayerHooks.onUpdateWalkingPlayerHead((EntityPlayerSP) (Object) this))
            ci.cancel();
    }

    @Inject(method = "onUpdate", at = @At("HEAD"))
    private void onUpdate(CallbackInfo ci) {
        PlayerHooks.onUpdateHead((EntityPlayerSP) (Object) this);
    }

    @Inject(method = "onUpdate", at = @At("RETURN"))
    private void onUpdatePost(CallbackInfo ci) {
        PlayerHooks.onUpdateReturn((EntityPlayerSP) (Object) this);
    }

    @Inject(method = "onLivingUpdate", at = @At("HEAD"))
    public void onLivingUpdate(CallbackInfo ci) {
        PlayerHooks.onLivingUpdateHead((EntityPlayerSP) (Object) this);
    }

    @Inject(method = "swingItem", at = @At("HEAD"), cancellable = true)
    private void arsenic$hitSelectSwing(CallbackInfo ci) {
        if (PlayerHooks.swingItemHead((EntityPlayerSP) (Object) this))
            ci.cancel();
    }

    @Inject(method = "onUpdateWalkingPlayer", at = @At("RETURN"))
    private void onUpdateWalkingPlayerPost(CallbackInfo ci) {
        PlayerHooks.onUpdateWalkingPlayerReturn((EntityPlayerSP) (Object) this);
    }
}
