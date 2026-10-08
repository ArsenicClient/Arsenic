package arsenic.injection.mixin;

import arsenic.runtime.hooks.EntityHooks;
import net.minecraft.entity.Entity;
import net.minecraft.util.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hook logic lives in {@link EntityHooks}, shared with the injected client. */
@Mixin(Entity.class)
public abstract class MixinEntity {

    @Inject(method = "moveFlying", at = @At("HEAD"), cancellable = true)
    private void moveFlyingHead(float strafe, float forward, float friction, CallbackInfo ci) {
        if (EntityHooks.moveFlyingHead((Entity) (Object) this, strafe, forward, friction))
            ci.cancel();
    }

    @ModifyVariable(method = "setAngles", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float aimAssistYaw(float yaw) {
        return EntityHooks.setAnglesYaw((Entity) (Object) this, yaw, 0f);
    }

    @ModifyVariable(method = "setAngles", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private float aimAssistPitch(float pitch) {
        return EntityHooks.setAnglesPitch((Entity) (Object) this, 0f, pitch);
    }

    @ModifyVariable(method = "rayTrace", at = @At("STORE"), ordinal = 1)
    public Vec3 rayTrace(Vec3 vec31) {
        return EntityHooks.rayTraceLook((Entity) (Object) this, vec31);
    }
}
