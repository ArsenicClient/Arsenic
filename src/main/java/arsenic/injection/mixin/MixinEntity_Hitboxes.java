package arsenic.injection.mixin;

import arsenic.module.impl.ghost.Hitboxes;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hitboxes (None mode): grows the radius the crosshair pick adds around an entity's box. */
@Mixin(Entity.class)
public abstract class MixinEntity_Hitboxes {

    @Inject(method = "getPickRadius", at = @At("RETURN"), cancellable = true)
    private void arsenic$hitboxesPickRadius(CallbackInfoReturnable<Float> cir) {
        float extra = Hitboxes.extraPickRadius((Entity) (Object) this);
        if (extra > 0f)
            cir.setReturnValue(cir.getReturnValueF() + extra);
    }
}
