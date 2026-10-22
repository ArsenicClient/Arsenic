package arsenic.injection.mixin;

import arsenic.event.impl.EventRenderThirdPerson;
import arsenic.main.Arsenic;
import arsenic.module.impl.visual.Nametags;
import arsenic.utils.render.ChamsRenderer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.UvMapping;
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

    /** Chams: the model of a target is submitted with the chams render type and colour. */
    @WrapOperation(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V"))
    private void arsenic$chams(SubmitNodeCollector collector, Model<?> model, Object state, PoseStack poseStack, RenderType renderType,
                               int light, int overlay, int tint, UvMapping uvMapping, int outline, Operation<Void> original) {
        LivingEntityRenderState living = (LivingEntityRenderState) state;
        original.call(collector, model, state, poseStack, ChamsRenderer.renderType(living, renderType),
                light, overlay, ChamsRenderer.tint(living, tint), uvMapping, outline);
    }

    /**
     * Shows the silent rotation on the local player's model in third person. Rendering reads a
     * snapshot of the entity, so the rotations are written into the render state rather than
     * swapped on the entity and restored afterwards like on 1.8.
     */
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("RETURN"))
    private void arsenic$thirdPerson(T entity, S state, float partialTicks, CallbackInfo ci) {
        ChamsRenderer.extracted(entity, state);
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
