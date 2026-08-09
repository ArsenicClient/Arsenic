package arsenic.injection.mixin;

import arsenic.event.impl.EventRenderThirdPerson;
import arsenic.main.Arsenic;
import arsenic.module.impl.visual.Nametags;
import arsenic.utils.render.ChamsRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RendererLivingEntity.class, priority = 1111)
public abstract class MixinRendererLivingEntity<T extends EntityLivingBase> extends Render<T> {

    T cEntity;

    private float cYawH, cPYawH, cYawO, cPYawO, cPitch, cPPitch;

    private final Minecraft mc = Minecraft.getMinecraft();

    private EventRenderThirdPerson thirdPersonEvent;

    protected MixinRendererLivingEntity(RenderManager renderManager) {
        super(renderManager);
    }


    @Inject(method = "canRenderName(Lnet/minecraft/entity/EntityLivingBase;)Z", at = @At("HEAD"), cancellable = true)
    private void onCanRenderName(T entity, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof EntityPlayer) || entity == mc.thePlayer)
            return;
        Nametags nametags = Arsenic.getArsenic().getModuleManager().getModuleByClass(Nametags.class);
        if (nametags != null && nametags.isEnabled())
            cir.setReturnValue(false);
    }

    /**
     * Chams. These two are the same injection points Forge exposes as RenderLivingEvent.Pre and
     * .Post, so the depth offset wraps the whole entity render; the flat colour is applied around
     * renderModel instead, which keeps it off nametags and the armour layers.
     */
    @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V", at = @At("HEAD"))
    private void chamsPre(T entity, double x, double y, double z, float entityYaw, float partialTicks,
                          CallbackInfo ci) {
        ChamsRenderer.pre(entity);
    }

    @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V", at = @At("RETURN"))
    private void chamsPost(T entity, double x, double y, double z, float entityYaw, float partialTicks,
                           CallbackInfo ci) {
        ChamsRenderer.post(entity);
    }

    @Inject(method = "renderModel(Lnet/minecraft/entity/EntityLivingBase;FFFFFF)V", at = @At("HEAD"))
    private void chamsModelPre(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                               float netHeadYaw, float headPitch, float scale, CallbackInfo ci) {
        ChamsRenderer.beginModel(entity);
    }

    @Inject(method = "renderModel(Lnet/minecraft/entity/EntityLivingBase;FFFFFF)V", at = @At("RETURN"))
    private void chamsModelPost(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                float netHeadYaw, float headPitch, float scale, CallbackInfo ci) {
        ChamsRenderer.endModel();
    }

    @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V", at = @At("HEAD"))
    public void doRender(T entity, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci) {
        if(entity != mc.thePlayer)
            return;
        thirdPersonEvent = new EventRenderThirdPerson(entity.rotationYaw, entity.rotationPitch, entity.prevRotationYaw, entity.prevRotationPitch);
        Arsenic.getArsenic().getEventManager().post(thirdPersonEvent);
        if(!thirdPersonEvent.getAccepted())
            return;
        cYawH = entity.rotationYawHead;
        cPYawH = entity.prevRotationYawHead;
        cYawO = entity.renderYawOffset;
        cPYawO = entity.prevRenderYawOffset;
        cPitch = entity.rotationPitch;
        cPPitch = entity.prevRotationPitch;
        entity.rotationYawHead = thirdPersonEvent.getYaw();
        entity.prevRotationYawHead = thirdPersonEvent.getPrevYaw();
        entity.renderYawOffset = thirdPersonEvent.getYaw();
        entity.prevRenderYawOffset = thirdPersonEvent.getPrevYaw();
        entity.rotationPitch = thirdPersonEvent.getPitch();
        entity.prevRotationPitch = thirdPersonEvent.getPrevPitch();
    }

    @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V", at = @At("RETURN"))
    public void doRenderReturn(T entity, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci) {
        if(entity != mc.thePlayer || !thirdPersonEvent.getAccepted())
            return;
        entity.rotationYawHead = cYawH;
        entity.prevRotationYawHead = cPYawH;
        entity.renderYawOffset = cYawO;
        entity.prevRenderYawOffset = cPYawO;
        entity.rotationPitch = cPitch;
        entity.prevRotationPitch = cPPitch;
    }



}
