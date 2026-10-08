package arsenic.injection.mixin;

import arsenic.runtime.hooks.RenderHooks;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hook logic lives in {@link RenderHooks}, shared with the injected client. */
@Mixin(value = RendererLivingEntity.class, priority = 1111)
public abstract class MixinRendererLivingEntity<T extends EntityLivingBase> {

    @Inject(method = "canRenderName(Lnet/minecraft/entity/EntityLivingBase;)Z", at = @At("HEAD"), cancellable = true)
    private void onCanRenderName(T entity, CallbackInfoReturnable<Boolean> cir) {
        int result = RenderHooks.canRenderName((RendererLivingEntity<?>) (Object) this, entity);
        if (result >= 0)
            cir.setReturnValue(result != 0);
    }

    @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V", at = @At("HEAD"))
    private void doRenderHead(T entity, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci) {
        RenderHooks.doRenderHead((RendererLivingEntity<?>) (Object) this, entity, x, y, z, entityYaw, partialTicks);
    }

    @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V", at = @At("RETURN"))
    private void doRenderReturn(T entity, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci) {
        RenderHooks.doRenderReturn((RendererLivingEntity<?>) (Object) this, entity, x, y, z, entityYaw, partialTicks);
    }

    @Inject(method = "renderModel(Lnet/minecraft/entity/EntityLivingBase;FFFFFF)V", at = @At("HEAD"))
    private void chamsModelPre(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                               float netHeadYaw, float headPitch, float scale, CallbackInfo ci) {
        RenderHooks.renderModelHead((RendererLivingEntity<?>) (Object) this, entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
    }

    @Inject(method = "renderModel(Lnet/minecraft/entity/EntityLivingBase;FFFFFF)V", at = @At("RETURN"))
    private void chamsModelPost(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                float netHeadYaw, float headPitch, float scale, CallbackInfo ci) {
        RenderHooks.renderModelReturn((RendererLivingEntity<?>) (Object) this, entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
    }
}
