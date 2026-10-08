package arsenic.injection.mixin;

import arsenic.runtime.hooks.RenderHooks;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.scoreboard.ScoreObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hook logic lives in {@link RenderHooks}, shared with the injected client. */
@Mixin(priority = 1111, value = GuiIngame.class)
public class MixinGuiIngame {

    @Inject(method = "renderGameOverlay", at = @At("RETURN"))
    private void arsenic$fadeIntoGame(float partialTicks, CallbackInfo ci) {
        RenderHooks.renderGameOverlayReturn((GuiIngame) (Object) this, partialTicks);
    }

    @Inject(method = "renderScoreboard", at = @At("HEAD"), cancellable = true)
    private void arsenic$replaceScoreboard(ScoreObjective objective, ScaledResolution sr, CallbackInfo ci) {
        if (RenderHooks.renderScoreboardHead((GuiIngame) (Object) this, objective, sr))
            ci.cancel();
    }

    @Inject(method = "renderTooltip", at = @At("RETURN"))
    private void renderTooltip(ScaledResolution sr, float partialTicks, CallbackInfo ci) {
        RenderHooks.renderTooltipReturn((GuiIngame) (Object) this, sr, partialTicks);
    }
}
