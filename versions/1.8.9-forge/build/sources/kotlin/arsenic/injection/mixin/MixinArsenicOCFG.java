package arsenic.injection.mixin;

import arsenic.module.ArsenicOCFG;
import cc.polyfrost.oneconfig.config.Config;
import cc.polyfrost.oneconfig.config.data.Mod;
import cc.polyfrost.oneconfig.config.elements.OptionPage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;


@Mixin(Config.class)
public abstract class MixinArsenicOCFG {

    /*
    @Shadow
    protected abstract void generateOptionList(Object instance, OptionPage page, Mod mod, boolean migrate);

    @Redirect(method = "initialize", at = @At(value = "INVOKE", target = "Lcc/polyfrost/oneconfig/config/Config;generateOptionList(Ljava/lang/Object;Lcc/polyfrost/oneconfig/config/elements/OptionPage;Lcc/polyfrost/oneconfig/config/data/Mod;Z)V", ordinal = 0))
    public void initialize(Config configInstance, Object instance, OptionPage page, Mod mod, boolean migrate) {
        System.out.println(configInstance instanceof ArsenicOCFG);
        System.out.println("Mixin Here");
        generateOptionList(instance, page, mod, migrate);
    }*/

}
