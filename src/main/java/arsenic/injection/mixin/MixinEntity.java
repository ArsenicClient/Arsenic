package arsenic.injection.mixin;

import arsenic.event.impl.EventMove;
import arsenic.main.Arsenic;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static arsenic.main.MinecraftAPI.cachedYawM;

@Mixin(Entity.class)
public abstract class MixinEntity {

    @Shadow
    public float rotationYaw;

    @Inject(method = "moveFlying", at = @At("HEAD"))
    private void moveFlyingHead(float p_moveFlying_1_, float p_moveFlying_2_, float p_moveFlying_3_, CallbackInfo ci) {
       if((Object) this == Minecraft.getMinecraft().thePlayer) {
            EventMove e = new EventMove(p_moveFlying_1_, p_moveFlying_2_, p_moveFlying_3_, rotationYaw);
            p_moveFlying_1_ = e.getStrafe();
            p_moveFlying_2_ = e.getForward();
            p_moveFlying_3_ = e.getFriction();
            Arsenic.getArsenic().getEventManager().post(e);
            cachedYawM = rotationYaw;
        }
    }

    @Inject(method = "moveFlying", at = @At("RETURN"))
    public void moveFlyingReturn(float strafe, float forward, float friction, CallbackInfo ci) {
        if((Object) this == Minecraft.getMinecraft().thePlayer) {
            rotationYaw = cachedYawM;
        }
    }

}
