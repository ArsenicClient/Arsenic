package arsenic.injection.mixin;

import arsenic.event.impl.EventLook;
import arsenic.main.Arsenic;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityLivingBase.class)
public abstract class MixinEntityLivingBase extends Entity {

    public MixinEntityLivingBase(World worldIn) {
        super(worldIn);
    }

    @Inject(method = "getLook", at = @At("HEAD"), cancellable = true)
    public void getLook(float partialTicks, CallbackInfoReturnable<Vec3> cir) {
        if(this != Minecraft.getMinecraft().getRenderViewEntity())
            return;
        EventLook eventLook = new EventLook(rotationYaw, rotationPitch);
        Arsenic.getArsenic().getEventManager().post(eventLook);
        if(!eventLook.hasBeenModified())
            return;
        Vec3 newVec = getVectorForRotation(eventLook.getPitch(), eventLook.getYaw());
        cir.setReturnValue(newVec);
    }
}
