package arsenic.injection.mixin;

import arsenic.main.Arsenic;
import arsenic.module.impl.ghost.HitFix;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets {@link HitFix} hold a left click on a player who cannot take the hit yet. */
@Mixin(Minecraft.class)
public abstract class MixinMinecraft_HitFix {

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void arsenic$hitFix(CallbackInfoReturnable<Boolean> cir) {
        if (Arsenic.getArsenic() == null || Arsenic.getArsenic().getModuleManager() == null)
            return;
        HitFix module = Arsenic.getArsenic().getModuleManager().getModuleByClass(HitFix.class);
        if (module != null && module.shouldHoldClick())
            cir.setReturnValue(false);
    }
}
