package arsenic.injection.mixin;

import arsenic.event.impl.EventMovementInput;
import arsenic.main.Arsenic;
import net.minecraft.util.MovementInput;
import net.minecraft.util.MovementInputFromOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MovementInputFromOptions.class, priority = 1111)
public class MixinMovementInputFromOptions extends MovementInput{

    @Inject(method = "updatePlayerMoveState", at = @At(value = "HEAD"), cancellable = true)
    public void updatePlayerMoveState(CallbackInfo ci) {
        EventMovementInput event = new EventMovementInput();
        Arsenic.getArsenic().getEventManager().post(event);
        if(event.isCancelled()) {
            moveStrafe = 0.0F;
            moveForward = 0.0F;
            ci.cancel();
        }
    }
}
