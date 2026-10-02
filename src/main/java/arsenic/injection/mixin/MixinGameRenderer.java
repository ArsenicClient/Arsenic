package arsenic.injection.mixin;

import arsenic.main.Arsenic;
import arsenic.module.impl.visual.NoHurtCam;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void arsenic$noHurtCam(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
        NoHurtCam noHurtCam = Arsenic.getArsenic().getModuleManager().getModuleByClass(NoHurtCam.class);
        if (noHurtCam != null && noHurtCam.isEnabled())
            ci.cancel();
    }
}
