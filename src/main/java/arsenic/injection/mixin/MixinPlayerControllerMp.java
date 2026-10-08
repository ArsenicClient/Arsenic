package arsenic.injection.mixin;

import arsenic.runtime.hooks.PlayerHooks;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hook logic lives in {@link PlayerHooks}, shared with the injected client. */
@Mixin(PlayerControllerMP.class)
public class MixinPlayerControllerMp {

    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    private void arsenic$hitSelect(EntityPlayer playerIn, Entity targetEntity, CallbackInfo ci) {
        if (PlayerHooks.attackEntityHead((PlayerControllerMP) (Object) this, playerIn, targetEntity))
            ci.cancel();
    }
}
