package arsenic.injection.mixin;

import cc.polyfrost.oneconfig.internal.assets.SVGs;
import cc.polyfrost.oneconfig.renderer.asset.SVG;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.*;

@Mixin(SVGs.class)
public class MixinSVGs {

    @Shadow
    @Mutable
    private static SVG ONECONFIG_FULL_DARK;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void modifyPrimary500(CallbackInfo ci) {
        ONECONFIG_FULL_DARK = new SVG("/assets/ocfg/rocfg.svg");
    }

}
