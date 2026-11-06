package arsenic.injection.mixin;

import arsenic.module.impl.blatant.AutoMace;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** AutoMace (Expand Hitbox): grows the pick radius around the player you are falling onto. */
@Mixin(Entity.class)
public abstract class MixinEntity_AutoMace {

    @Inject(method = "getPickRadius", at = @At("RETURN"), cancellable = true)
    private void arsenic$autoMacePickRadius(CallbackInfoReturnable<Float> cir) {
        float extra = AutoMace.extraPickRadius((Entity) (Object) this);
        if (extra > 0f)
            cir.setReturnValue(cir.getReturnValueF() + extra);
    }
}
