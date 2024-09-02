package arsenic.injection.mixin;

import cc.polyfrost.oneconfig.config.Config;
import org.spongepowered.asm.mixin.Mixin;


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
