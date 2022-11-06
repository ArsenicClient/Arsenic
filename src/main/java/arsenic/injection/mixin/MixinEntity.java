package arsenic.injection.mixin;

import arsenic.event.impl.EventMove;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import arsenic.event.impl.EventMove;
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
    private void moveFlyingHead(float p_moveFlying_1_, float p_moveFlying_2_, float p_moveFlying_3_, CallbackInfo ci) {


    @Inject(method = "moveFlying", at = @At("HEAD"))
    public void moveFlyingHead(float p_moveFlying1, float p_moveFlying2, float p_moveFlying3, CallbackInfo ci) {
       if((Object) this == Minecraft.getMinecraft().thePlayer) {
            EventMove e = new EventMove(p_moveFlying_1_, p_moveFlying_2_, p_moveFlying_3_, rotationYaw);
            p_moveFlying_1_ = e.getStrafe();
            p_moveFlying_2_ = e.getForward();
            p_moveFlying_3_ = e.getFriction();
            EventMove e = new EventMove(p_moveFlying1, p_moveFlying2, p_moveFlying3, rotationYaw);
            Arsenic.getArsenic().getEventManager().post(e);
            p_moveFlying1 = e.getStrafe();
            p_moveFlying2 = e.getForward();
            p_moveFlying3 = e.getFriction();
            cachedYawM = rotationYaw;
            args.set(2, e.getFriction());
        }
    }


    @Inject(method = "moveFlying", at = @At("RETURN"))
    public void moveFlyingReturn(float strafe, float forward, float friction, CallbackInfo ci) {
        if((Object) this == Minecraft.getMinecraft().thePlayer)
            rotationYaw = cachedYawM;
    }

}
