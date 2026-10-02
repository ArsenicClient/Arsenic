package arsenic.injection.mixin;

import arsenic.event.impl.EventMovementInput;
import arsenic.main.Arsenic;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.8's MovementInputFromOptions#updatePlayerMoveState. */
@Mixin(value = KeyboardInput.class, priority = 1111)
public abstract class MixinKeyboardInput extends ClientInput {

    @Inject(method = "tick", at = @At("RETURN"))
    private void arsenic$tick(CallbackInfo ci) {
        EventMovementInput event = new EventMovementInput(moveVector.y, moveVector.x, keyPresses.jump());
        Arsenic.getArsenic().getEventManager().post(event);
        if (event.isCancelled()) {
            moveVector = Vec2.ZERO;
            return;
        }
        moveVector = new Vec2(event.getStrafe(), event.getSpeed()).normalized();
        if (event.isJumping() != keyPresses.jump()) {
            keyPresses = new Input(keyPresses.forward(), keyPresses.backward(), keyPresses.left(), keyPresses.right(),
                    event.isJumping(), keyPresses.shift(), keyPresses.sprint());
        }
    }
}
