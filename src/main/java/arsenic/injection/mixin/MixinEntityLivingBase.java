package arsenic.injection.mixin;

import arsenic.event.impl.EventLook;
import arsenic.main.Arsenic;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static arsenic.main.MinecraftAPI.*;

@Mixin(EntityLivingBase.class)
public abstract class MixinEntityLivingBase extends Entity {


    public MixinEntityLivingBase(World worldIn) {
        super(worldIn);
    }

    @Inject(method = "getLook", at = @At("HEAD"))
    public void getLookHead(CallbackInfoReturnable<Vec3> ci) {
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
    }
}
