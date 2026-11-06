package arsenic.injection.mixin;

import arsenic.main.Arsenic;
import arsenic.module.impl.ghost.AntiAction;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets {@link AntiAction} drop a right or left click before vanilla handles it. */
@Mixin(Minecraft.class)
public abstract class MixinMinecraft_AntiAction {

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void arsenic$antiActionUse(CallbackInfo ci) {
        AntiAction module = module();
        if (module != null && module.shouldBlockUse())
            ci.cancel();
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void arsenic$antiActionAttack(CallbackInfoReturnable<Boolean> cir) {
        AntiAction module = module();
        if (module != null && module.shouldBlockAttack())
            cir.setReturnValue(false);
    }

    private static AntiAction module() {
        if (Arsenic.getArsenic() == null || Arsenic.getArsenic().getModuleManager() == null)
            return null;
        AntiAction module = Arsenic.getArsenic().getModuleManager().getModuleByClass(AntiAction.class);
        return module != null && module.isEnabled() ? module : null;
    }
}
