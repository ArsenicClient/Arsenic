package arsenic.injection.mixin;

import arsenic.main.Arsenic;
import arsenic.module.impl.visual.HUD;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class MixinHud {

    /** While the HUD module draws the scoreboard as one of its elements, the vanilla sidebar stays hidden. */
    @Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void arsenic$hideSidebar(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        HUD hud = Arsenic.getArsenic().getModuleManager().getModuleByClass(HUD.class);
        if (hud != null && hud.replacesVanillaScoreboard())
            ci.cancel();
    }
}
