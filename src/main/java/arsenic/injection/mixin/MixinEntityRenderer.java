package arsenic.injection.mixin;

import arsenic.runtime.hooks.RenderHooks;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hook logic lives in {@link RenderHooks}, shared with the injected client. */
@Mixin(value = EntityRenderer.class, priority = 995)
public abstract class MixinEntityRenderer {

    @Inject(method = "renderWorldPass", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/EntityRenderer;renderHand:Z", shift = At.Shift.BEFORE))
    private void renderWorldPass(int pass, float partialTicks, long finishTimeNano, CallbackInfo callbackInfo) {
        RenderHooks.renderWorldPassHand((EntityRenderer) (Object) this, pass, partialTicks, finishTimeNano);
    }

    @Inject(method = "renderWorld", at = @At("HEAD"))
    private void arsenic$silentView(float partialTicks, long finishTimeNano, CallbackInfo ci) {
        RenderHooks.renderWorldHead((EntityRenderer) (Object) this, partialTicks, finishTimeNano);
    }

    @Inject(method = "updateCameraAndRender", at = @At("HEAD"))
    private void arsenic$frameStart(float partialTicks, long nanoTime, CallbackInfo ci) {
        RenderHooks.updateCameraAndRenderHead((EntityRenderer) (Object) this, partialTicks, nanoTime);
    }

    @Inject(method = "updateCameraAndRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiIngame;renderGameOverlay(F)V", shift = At.Shift.AFTER))
    private void arsenic$frameEndAfterHud(float partialTicks, long nanoTime, CallbackInfo ci) {
        RenderHooks.afterRenderGameOverlay((EntityRenderer) (Object) this, partialTicks, nanoTime);
    }

    // frames with no HUD pass (F1 or no world)
    @Inject(method = "updateCameraAndRender", at = @At("RETURN"))
    private void arsenic$frameEnd(float partialTicks, long nanoTime, CallbackInfo ci) {
        RenderHooks.updateCameraAndRenderReturn((EntityRenderer) (Object) this, partialTicks, nanoTime);
    }

    @Inject(method = "getMouseOver", at = @At("HEAD"), cancellable = true)
    private void arsenic$getMouseOver(float partialTicks, CallbackInfo ci) {
        if (RenderHooks.getMouseOver((EntityRenderer) (Object) this, partialTicks))
            ci.cancel();
    }

    @Inject(method = "hurtCameraEffect", at = @At("HEAD"), cancellable = true)
    public void nohurtcam(float partialTicks, CallbackInfo callbackInfo) {
        if (RenderHooks.hurtCameraEffectHead((EntityRenderer) (Object) this, partialTicks))
            callbackInfo.cancel();
    }
}
