package arsenic.injection.mixin;

import arsenic.runtime.hooks.PlayerHooks;
import net.minecraft.util.MovementInputFromOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hook logic lives in {@link PlayerHooks}, shared with the injected client. */
@Mixin(value = MovementInputFromOptions.class, priority = 1111)
public class MixinMovementInputFromOptions {

    @Inject(method = "updatePlayerMoveState", at = @At(value = "RETURN"))
    public void updatePlayerMoveState(CallbackInfo ci) {
        PlayerHooks.updatePlayerMoveStateReturn((MovementInputFromOptions) (Object) this);
    }
}
