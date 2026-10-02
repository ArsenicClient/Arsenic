package arsenic.injection.mixin;

import arsenic.main.Arsenic;
import arsenic.module.impl.visual.FullBright;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * FullBright's gamma mode. The brightness option is clamped to 0..1 now, so instead of writing
 * a huge value into the setting like 1.8 did, the value is raised where the lightmap reads it.
 */
@Mixin(LightmapRenderStateExtractor.class)
public abstract class MixinLightmapRenderStateExtractor {

    // the first Double#floatValue in extract() is options.gamma().get().floatValue()
    @ModifyExpressionValue(method = "extract", at = @At(value = "INVOKE", target = "Ljava/lang/Double;floatValue()F", ordinal = 0))
    private float arsenic$fullBright(float brightness) {
        FullBright fullBright = Arsenic.getArsenic().getModuleManager().getModuleByClass(FullBright.class);
        return fullBright != null && fullBright.isGammaActive() ? 1000f : brightness;
    }
}
