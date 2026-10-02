package arsenic.injection.mixin;

import arsenic.event.impl.EventJump;
import arsenic.main.Arsenic;
import arsenic.module.impl.movement.NoJumpDelay;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity extends Entity {

    @Shadow
    private int noJumpDelay;

    @Shadow
    protected abstract float getJumpPower();

    public MixinLivingEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    /**
     * @author CosmicSC
     * @reason JumpFix - the sprint-jump boost is applied along the silent yaw.
     */
    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    private void arsenic$jump(CallbackInfo ci) {
        if ((Object) this != Minecraft.getInstance().player)
            return;
        final EventJump e = new EventJump(this.getYRot(), this.getJumpPower());
        Arsenic.getInstance().getEventManager().post(e);
        if (e.isCancelled())
            return;

        float jumpPower = e.getMotion();
        if (jumpPower > 1.0E-5F) {
            Vec3 movement = this.getDeltaMovement();
            this.setDeltaMovement(movement.x, Math.max(jumpPower, movement.y), movement.z);
            if (this.isSprinting()) {
                float angle = e.getYaw() * Mth.DEG_TO_RAD;
                this.addDeltaMovement(new Vec3(-Mth.sin(angle) * 0.2, 0.0, Mth.cos(angle) * 0.2));
            }
            this.needsSync = true;
        }
        ci.cancel();
    }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void arsenic$noJumpDelay(CallbackInfo ci) {
        if ((Object) this != Minecraft.getInstance().player)
            return;
        NoJumpDelay noJumpDelay = Arsenic.getInstance().getModuleManager().getModuleByClass(NoJumpDelay.class);
        if (noJumpDelay != null && noJumpDelay.isEnabled())
            this.noJumpDelay = 0;
    }
}
