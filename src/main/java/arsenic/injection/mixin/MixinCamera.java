package arsenic.injection.mixin;

import arsenic.event.impl.EventFov;
import arsenic.main.Arsenic;
import arsenic.module.impl.visual.Freecam;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Posts {@link EventFov} for the world's field of view (the held items use their own, which is left alone), and moves the
 * camera away from the player while Freecam is on.
 */
@Mixin(Camera.class)
public abstract class MixinCamera {

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    private boolean detached;

    @Inject(method = "update", at = @At("TAIL"))
    private void arsenic$freecam(DeltaTracker deltaTracker, CallbackInfo ci) {
        Freecam freecam = Arsenic.getArsenic().getModuleManager().getModuleByClass(Freecam.class);
        if (freecam == null || !freecam.isActive())
            return;
        Vec3 at = freecam.cameraPos(deltaTracker.getGameTimeDeltaPartialTick(true));
        setPosition(at.x, at.y, at.z);
        // detached cameras draw the player's own model
        detached = true;
    }

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void arsenic$fov(float partialTicks, CallbackInfoReturnable<Float> cir) {
        EventFov event = new EventFov(cir.getReturnValueF());
        Arsenic.getArsenic().getEventManager().getBus().post(event);
        if (event.isModified())
            cir.setReturnValue(event.getFov());
    }
}
