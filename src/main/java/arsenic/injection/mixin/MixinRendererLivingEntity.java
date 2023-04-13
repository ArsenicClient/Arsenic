package arsenic.injection.mixin;

import arsenic.event.impl.EventLook;
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
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RendererLivingEntity.class, priority = 1111)
public abstract class MixinRendererLivingEntity<T extends EntityLivingBase> extends Render<T> {

    T cEntity;

    private float cYaw, cPitch, cYawO;

    private Minecraft mc = Minecraft.getMinecraft();

    protected MixinRendererLivingEntity(RenderManager renderManager) {
        super(renderManager);
    }


    @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V", at = @At("HEAD"))
    public void doRender(T entity, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci) {
        if(entity != mc.thePlayer)
            return;
        EventRenderThirdPerson event = new EventRenderThirdPerson(entity.rotationYaw, entity.rotationPitch);
        Arsenic.getArsenic().getEventManager().post(event);
        cYaw = entity.renderYawOffset;
        cYawO = entity.rotationYawHead;
        cPitch = entity.rotationPitch;
        entity.rotationYawHead = event.getYaw();
        entity.prevRotationYawHead = event.getYaw();
        entity.renderYawOffset = event.getYaw();
        entity.prevRenderYawOffset = event.getYaw();
        entity.rotationPitch = event.getPitch();
        entity.prevRotationPitch = event.getPitch();
    }

    @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V", at = @At("RETURN"))
    public void doRenderReturn(T entity, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci) {
        if(entity != mc.thePlayer)
            return;
        entity.renderYawOffset = cYaw;
        entity.prevRenderYawOffset = cYaw;
        entity.rotationYawHead = cYawO;
        entity.prevRotationYawHead = cYawO;
        entity.rotationPitch = cPitch;
        entity.prevRotationPitch = cPitch;
    }



}
