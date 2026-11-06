package arsenic.injection.mixin;

import arsenic.main.Arsenic;
import arsenic.module.impl.blatant.SilentAim;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets {@link SilentAim} take over a missed left click and stop a sword from mining while it has a target. */
@Mixin(Minecraft.class)
public abstract class MixinMinecraft_SilentAim {

    private static SilentAim arsenic$silentAim() {
        if (Arsenic.getArsenic() == null || Arsenic.getArsenic().getModuleManager() == null)
            return null;
        return Arsenic.getArsenic().getModuleManager().getModuleByClass(SilentAim.class);
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void arsenic$silentAimClick(CallbackInfoReturnable<Boolean> cir) {
        SilentAim module = arsenic$silentAim();
        if (module != null && module.interceptClick())
            cir.setReturnValue(false);
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void arsenic$silentAimMining(boolean leftClick, CallbackInfo ci) {
        if (!leftClick)
            return;
        SilentAim module = arsenic$silentAim();
        if (module != null && module.blockMining())
            ci.cancel();
    }
}
