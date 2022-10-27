package arsenic.injection.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import arsenic.event.impl.EventMove;
import arsenic.main.Arsenic;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;

@Mixin(Entity.class)
public abstract class MixinEntity {

    @Shadow
    public float rotationYaw;

    @Shadow
    public float prevRotationYaw;

    @Shadow
    public float rotationPitch;

    @Shadow
    public float prevRotationPitch;

    public float cachedYawM, cachedYawL, cachedPrevYawL, cachedPitchL, cachedPrevPitchL;



    @Inject(method = "moveFlying", at = @At("HEAD"))
    public void moveFlyingHead(float p_moveFlying1, float p_moveFlying2, float p_moveFlying3, CallbackInfo ci) {
       if((Object) this == Minecraft.getMinecraft().thePlayer) {
            EventMove e = new EventMove(p_moveFlying1, p_moveFlying2, p_moveFlying3, rotationYaw);
            Arsenic.getArsenic().getEventManager().post(e);
            p_moveFlying1 = e.getStrafe();
            p_moveFlying2 = e.getForward();
            p_moveFlying3 = e.getFriction();
            cachedYawM = rotationYaw;
            this.rotationYaw = e.getYaw();
        }
    }


    @Inject(method = "moveFlying", at = @At("RETURN"))
    public void moveFlyingReturn(float strafe, float forward, float friction, CallbackInfo ci) {
        if((Object) this == Minecraft.getMinecraft().thePlayer)
            rotationYaw = cachedYawM;
    }


 /*
    @Inject(method = "getLook", at = @At("HEAD"))
    public void getLookHead(CallbackInfo ci) {
        Minecraft.getMinecraft().thePlayer.sendChatMessage("breuh");
        if((Object) this == Minecraft.getMinecraft().thePlayer) {
            EventLook e = new EventLook(rotationPitch, prevRotationPitch, rotationYaw, prevRotationYaw);
            Arsenic.getArsenic().getEventManager().post(e);

            cachedPitchL = rotationPitch;
            cachedPrevPitchL = prevRotationPitch;
            cachedYawL = rotationYaw;
            cachedPrevYawL = prevRotationYaw;

            rotationPitch = e.getPitch();
            prevRotationPitch  = e.getPrevPitch();
            rotationYaw = e.getYaw();
            prevRotationYaw = e.getPrevYaw();
        }
    }

    @Inject(method = "getLook", at = @At("RETURN"))
    public void getLookReturn( CallbackInfoReturnable<Vec3> cir) {
        if((Object) this == Minecraft.getMinecraft().thePlayer) {
            rotationPitch = cachedPitchL;
            prevRotationPitch = cachedPrevPitchL;
            rotationYaw = cachedYawL;
            prevRotationYaw = cachedPrevYawL;
        }
    } */

    /*
    @Inject(method = "getVectorForRotation", at = @At("HEAD"))
    public void getVectorForRotation(float p_getVectorForRotation1, float p_getVectorForRotation2, CallbackInfoReturnable<Vec3> cir) {
        if((Object) this == Minecraft.getMinecraft().thePlayer) {
            EventLook e = new EventLook(p_getVectorForRotation1, p_getVectorForRotation2);
            Arsenic.getArsenic().getEventManager().post(e);
            p_getVectorForRotation1 = e.getPitch();
            p_getVectorForRotation2 = e.getYaw();
            for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
                System.out.println(ste + "\n");
            }
        }
    } */
}