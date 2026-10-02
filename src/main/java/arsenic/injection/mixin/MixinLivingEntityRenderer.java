package arsenic.injection.mixin;

import arsenic.event.impl.EventRenderThirdPerson;
import arsenic.main.Arsenic;
import arsenic.module.impl.visual.Nametags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LivingEntityRenderer.class, priority = 1111)
public abstract class MixinLivingEntityRenderer<T extends LivingEntity, S extends LivingEntityRenderState> {

    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true)
    private void arsenic$nametags(T entity, double distanceToCameraSq, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof Player) || entity == Minecraft.getInstance().player)
            return;
        Nametags nametags = Arsenic.getArsenic().getModuleManager().getModuleByClass(Nametags.class);
        if (nametags != null && nametags.isEnabled())
            cir.setReturnValue(false);
    }

    /**
     * Shows the silent rotation on the local player's model in third person. Rendering reads a
     * snapshot of the entity, so the rotations are written into the render state rather than
     * swapped on the entity and restored afterwards like on 1.8.
     */
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("RETURN"))
    private void arsenic$thirdPerson(T entity, S state, float partialTicks, CallbackInfo ci) {
        if (entity != Minecraft.getInstance().player)
            return;
        EventRenderThirdPerson event = new EventRenderThirdPerson(entity.getYRot(), entity.getXRot(), entity.yRotO, entity.xRotO);
        Arsenic.getArsenic().getEventManager().post(event);
        if (!event.getAccepted())
            return;
        float yaw = Mth.rotLerp(partialTicks, event.getPrevYaw(), event.getYaw());
        state.bodyRot = yaw;
        state.yRot = 0;
        state.xRot = Mth.lerp(partialTicks, event.getPrevPitch(), event.getPitch());
    }
}
