package arsenic.injection.mixin;

import arsenic.gui.ArsenicSplash;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.SplashProgress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SplashProgress.class, remap = false)
public class MixinSplashProgress {

    @Inject(method = "start", at = @At("HEAD"), cancellable = true, remap = false)
    private static void arsenic$start(CallbackInfo ci) {
        ArsenicSplash.start();
        ci.cancel();
    }

    @Inject(method = "pause", at = @At("HEAD"), cancellable = true, remap = false)
    private static void arsenic$pause(CallbackInfo ci) {
        ArsenicSplash.pause();
        ci.cancel();
    }

    @Inject(method = "resume", at = @At("HEAD"), cancellable = true, remap = false)
    private static void arsenic$resume(CallbackInfo ci) {
        ArsenicSplash.resume();
        ci.cancel();
    }

    @Inject(method = "finish", at = @At("HEAD"), cancellable = true, remap = false)
    private static void arsenic$finish(CallbackInfo ci) {
        ArsenicSplash.finish();
        ci.cancel();
    }

    @Inject(method = "drawVanillaScreen", at = @At("HEAD"), cancellable = true, remap = false)
    private static void arsenic$noVanillaScreen(TextureManager renderEngine, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "clearVanillaResources", at = @At("HEAD"), cancellable = true, remap = false)
    private static void arsenic$noVanillaClear(TextureManager renderEngine, ResourceLocation mojangLogo, CallbackInfo ci) {
        ci.cancel();
    }
}
