package arsenic.injection.mixin;

import arsenic.main.Arsenic;
import arsenic.module.impl.ghost.GhostPlace;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets {@link GhostPlace} turn a right click on an enemy into a block placement behind them. */
@Mixin(Minecraft.class)
public abstract class MixinMinecraft_GhostPlace {

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void arsenic$ghostPlace(CallbackInfo ci) {
        if (Arsenic.getArsenic() == null || Arsenic.getArsenic().getModuleManager() == null)
            return;
        GhostPlace module = Arsenic.getArsenic().getModuleManager().getModuleByClass(GhostPlace.class);
        if (module != null && module.isEnabled() && module.handleUse())
            ci.cancel();
    }
}
