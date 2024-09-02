package arsenic.injection.mixin;

import cc.polyfrost.oneconfig.internal.assets.Colors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.*;

@Mixin(Colors.class)
public class MixinColors {

    @Shadow @Mutable
    private static int PRIMARY_400;

    @Shadow @Mutable
    private static int PRIMARY_500;

    @Shadow @Mutable
    private static int PRIMARY_600;

    @Shadow @Mutable
    private static int PRIMARY_700;

    @Shadow @Mutable
    private static int PRIMARY_700_80;

    @Shadow @Mutable
    private static int PRIMARY_800;



    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void modifyPrimary500(CallbackInfo ci) {
        PRIMARY_400 = new Color(0xFFDD425E).brighter().getRGB();
        PRIMARY_500 = 0xFFDD425E;
        PRIMARY_600 = new Color(0xFFDD425E).darker().getRGB();
        PRIMARY_700 = new Color(0xFFDD425E).darker().darker().getRGB();
        PRIMARY_700_80 = new Color(0xFFDD425E).darker().darker().darker().getRGB();
        PRIMARY_800 = new Color(0xFFDD425E).darker().darker().darker().darker().getRGB();
    }

}
