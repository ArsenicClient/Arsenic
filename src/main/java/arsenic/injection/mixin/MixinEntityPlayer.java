package arsenic.injection.mixin;

import arsenic.runtime.hooks.PlayerHooks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hook logic lives in {@link PlayerHooks}, shared with the injected client. */
@Mixin(priority = 995, value = EntityPlayer.class)
public abstract class MixinEntityPlayer {

    @Inject(method = "attackTargetEntityWithCurrentItem", at = @At("HEAD"), cancellable = true)
    public void attackTargetEntityWithCurrentItem(Entity target, CallbackInfo c) {
        if (PlayerHooks.attackTargetEntityWithCurrentItem((EntityPlayer) (Object) this, target))
            c.cancel();
    }
}
