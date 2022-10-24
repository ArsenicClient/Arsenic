package arsenic.injection.mixin;

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
public abstract class MixinEntity{



    @Shadow
    public float rotationYaw;

    public float cachedYaw;


    @ModifyArgs(at = @At(value = "INVOKE"), method = "moveFlying")
    private void moveFlying(Args args) {
       if((Object) this == Minecraft.getMinecraft().thePlayer) {
            EventMove e = new EventMove(args.get(0), args.get(1), args.get(3), rotationYaw);
            args.set(0, e.getStrafe());
            args.set(1, e.getForward());
            args.set(2, e.getFriction());
            this.rotationYaw = e.getYaw();
            cachedYaw = e.getYaw();
        }
    }

    @Inject(method = "moveFlying", at = @At("RETURN"))
    public void moveFlying(float strafe, float forward, float friction, CallbackInfo ci) {
        if((Object) this == Minecraft.getMinecraft().thePlayer)
            rotationYaw = cachedYaw;
    }

}
