package arsenic.injection.mixin;

import arsenic.main.Arsenic;
import arsenic.module.impl.ghost.SwapHit;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets {@link SwapHit} take over a left click on a player and land it with a sword. */
@Mixin(Minecraft.class)
public abstract class MixinMinecraft_SwapHit {

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void arsenic$swapHit(CallbackInfoReturnable<Boolean> cir) {
        if (Arsenic.getArsenic() == null || Arsenic.getArsenic().getModuleManager() == null)
            return;
        SwapHit module = Arsenic.getArsenic().getModuleManager().getModuleByClass(SwapHit.class);
        if (module != null && module.isEnabled() && module.interceptAttack())
            cir.setReturnValue(false);
    }
}
