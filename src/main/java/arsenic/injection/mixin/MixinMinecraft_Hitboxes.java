package arsenic.injection.mixin;

import arsenic.module.impl.ghost.Hitboxes;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hitboxes (GrimVulcan mode): takes over a left click that missed only by the expansion. */
@Mixin(Minecraft.class)
public abstract class MixinMinecraft_Hitboxes {

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void arsenic$hitboxesRedirect(CallbackInfoReturnable<Boolean> cir) {
        if (Hitboxes.interceptAttack())
            cir.setReturnValue(false);
    }
}
