package arsenic.injection.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import arsenic.event.impl.EventLook;
import arsenic.event.impl.EventMove;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.Vec3;

@Mixin(Entity.class)
public abstract class MixinEntity{

    @Shadow
    public float rotationYaw;

    public float cachedYaw;



    @Inject(method = "moveFlying", at = @At("HEAD"))
    public void moveFlyingHead(float strafe, float forward, float friction, CallbackInfo ci) {
       if((Object) this == Minecraft.getMinecraft().thePlayer) {
            EventMove e = new EventMove(strafe, forward, friction, rotationYaw);
            strafe = e.getStrafe();
            forward = e.getForward();
            friction = e.getFriction();
            this.rotationYaw = e.getYaw();
            cachedYaw = e.getYaw();
        }
    }


    @Inject(method = "moveFlying", at = @At("RETURN"))
    public void moveFlyingReturn(float strafe, float forward, float friction, CallbackInfo ci) {
        if((Object) this == Minecraft.getMinecraft().thePlayer)
            rotationYaw = cachedYaw;
    }



    @Inject(method = "getVectorForRotation", at = @At("HEAD"))
    protected void getVectorForRotation(float pitch, float yaw, CallbackInfoReturnable<Vec3> cir) {
        if((Object) this == Minecraft.getMinecraft().thePlayer) {
            EventLook e = new EventLook(pitch, yaw);
            pitch = e.getPitch();
            yaw = e.getYaw();
        }
    }

}