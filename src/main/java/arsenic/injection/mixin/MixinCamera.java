package arsenic.injection.mixin;

import arsenic.event.impl.EventFov;
import arsenic.main.Arsenic;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Posts {@link EventFov} for the world's field of view; the held items use their own, which is left alone. */
@Mixin(Camera.class)
public abstract class MixinCamera {

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void arsenic$fov(float partialTicks, CallbackInfoReturnable<Float> cir) {
        EventFov event = new EventFov(cir.getReturnValueF());
        Arsenic.getArsenic().getEventManager().getBus().post(event);
        if (event.isModified())
            cir.setReturnValue(event.getFov());
    }
}
