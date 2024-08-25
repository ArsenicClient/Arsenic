package arsenic.injection.mixin;

import arsenic.event.impl.EventJump;
import arsenic.main.Arsenic;
import arsenic.module.impl.movement.NoJumpDelay;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
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

    @Shadow
    public abstract PotionEffect getActivePotionEffect(Potion potionIn);

    @Shadow
    public abstract boolean isPotionActive(Potion potionIn);

    /**
     * @author CosmicSC
     * @reason JumpFix
     */
    @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
    protected void jump(CallbackInfo ci) {
        final EventJump e = new EventJump(this.rotationYaw, this.getJumpUpwardsMotion());
        Arsenic.getInstance().getEventManager().post(e);

        if (e.isCancelled()) return;

        this.motionY = e.getMotion();
        if (this.isPotionActive(Potion.jump)) {
            this.motionY += ((float) (this.getActivePotionEffect(Potion.jump).getAmplifier() + 1) * 0.1F);
        }

        if (this.isSprinting()) {
            float f = e.getYaw() * 0.017453292F;
            this.motionX -= MathHelper.sin(f) * 0.2F;
            this.motionZ += MathHelper.cos(f) * 0.2F;
        }

        this.isAirBorne = true;
        ci.cancel();
    }

    @Inject(method = "onLivingUpdate", at = @At("HEAD"))
    private void headLiving(CallbackInfo callbackInfo) {
        if (Arsenic.getInstance().getModuleManager().getModuleByClass(NoJumpDelay.class).isEnabled())
            jumpTicks = 0;
    }
}
