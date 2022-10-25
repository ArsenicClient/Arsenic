package arsenic.injection.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import arsenic.event.impl.EventLook;
import arsenic.event.impl.EventMove;
import arsenic.main.Arsenic;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.Vec3;

@Mixin(Entity.class)
public abstract class MixinEntity{

    @Shadow
    public float rotationYaw;

    public float cachedYaw;



    @Inject(method = "moveFlying", at = @At("HEAD"))
    public void moveFlyingHead(float p_moveFlying1, float p_moveFlying2, float p_moveFlying3, CallbackInfo ci) {
       if((Object) this == Minecraft.getMinecraft().thePlayer) {
            EventMove e = new EventMove(p_moveFlying1, p_moveFlying2, p_moveFlying3, rotationYaw);
            Arsenic.getArsenic().getEventManager().getBus().post(e);
            p_moveFlying1 = e.getStrafe();
            p_moveFlying2 = e.getForward();
            p_moveFlying3 = e.getFriction();
            cachedYaw = rotationYaw;
            this.rotationYaw = e.getYaw();
        }
    }


    @Inject(method = "moveFlying", at = @At("RETURN"))
    public void moveFlyingReturn(float strafe, float forward, float friction, CallbackInfo ci) {
        if((Object) this == Minecraft.getMinecraft().thePlayer)
            rotationYaw = cachedYaw;
    }



    @Inject(method = "getVectorForRotation", at = @At("HEAD"))
    protected void getVectorForRotation(float p_getVectorForRotation1, float p_getVectorForRotation2, CallbackInfoReturnable<Vec3> cir) {
        if((Object) this == Minecraft.getMinecraft().thePlayer) {
            EventLook e = new EventLook(p_getVectorForRotation1, p_getVectorForRotation2);
            Arsenic.getArsenic().getEventManager().getBus().post(e);
            p_getVectorForRotation1 = e.getPitch();
            p_getVectorForRotation2 = e.getYaw();
        }
    }

}