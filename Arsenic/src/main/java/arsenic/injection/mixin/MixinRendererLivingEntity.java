package arsenic.injection.mixin;

import arsenic.event.impl.EventRenderThirdPerson;
import arsenic.main.Arsenic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RendererLivingEntity.class, priority = 1111)
public abstract class MixinRendererLivingEntity<T extends EntityLivingBase> extends Render<T> {

    T cEntity;

    private float cYawH, cPYawH, cYawO, cPYawO, cPitch, cPPitch;

    private final Minecraft mc = Minecraft.getMinecraft();

    private EventRenderThirdPerson thirdPersonEvent;

    protected MixinRendererLivingEntity(RenderManager renderManager) {
        super(renderManager);
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
