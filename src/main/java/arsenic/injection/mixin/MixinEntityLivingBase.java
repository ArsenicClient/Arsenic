package arsenic.injection.mixin;

import arsenic.event.impl.EventJump;
import arsenic.main.Arsenic;
import arsenic.module.impl.movement.NoJumpDelay;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityLivingBase.class)
public abstract class MixinEntityLivingBase extends MixinEntity {

    @Shadow
    protected abstract float getJumpUpwardsMotion();

    @Shadow
    private int jumpTicks;

    @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
    private void jump2(CallbackInfo ci) {
        EventJump event = new EventJump(this.rotationYaw, this.getJumpUpwardsMotion());
        if (event.isCancelled())
            ci.cancel();
    }

    @Inject(method = "onLivingUpdate", at = @At("HEAD"))
    private void headLiving(CallbackInfo callbackInfo) {
        if (Arsenic.getInstance().getModuleManager().getModuleByClass(NoJumpDelay.class).isEnabled())
            jumpTicks = 0;
    }
}
