package arsenic.injection.mixin;

import arsenic.gui.ArsenicSplash;
import arsenic.gui.click.GuiStyle;
import arsenic.main.Arsenic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Mojang loading screen is replaced by {@link ArsenicSplash}. The timing is the vanilla overlay's: it fades in
 * over the screen on a resource reload, holds while loading, then fades out over the menu and removes itself.
 */
@Mixin(LoadingOverlay.class)
public abstract class MixinLoadingOverlay {

    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private ReloadInstance reload;
    @Shadow @Final private boolean fadeIn;
    @Shadow private long fadeOutStart;
    @Shadow private long fadeInStart;

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void arsenic$splash(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        if (Arsenic.getArsenic() == null || !GuiStyle.customMenus())
            return;
        ci.cancel();

        long now = Util.getMillis();
        if (fadeIn && fadeInStart == -1L)
            fadeInStart = now;
        float fadeOutAnim = fadeOutStart > -1L ? (now - fadeOutStart) / 1000f : -1f;
        float fadeInAnim = fadeInStart > -1L ? (now - fadeInStart) / 500f : -1f;

        float alpha;
        if (fadeOutAnim >= 1f) {
            if (minecraft.gui.screen() != null)
                minecraft.gui.screen().extractRenderStateWithTooltipAndSubtitles(graphics, 0, 0, a);
            alpha = 1f - Mth.clamp(fadeOutAnim - 1f, 0f, 1f);
        } else if (fadeIn) {
            if (minecraft.gui.screen() != null && fadeInAnim < 1f)
                minecraft.gui.screen().extractRenderStateWithTooltipAndSubtitles(graphics, mouseX, mouseY, a);
            alpha = Mth.clamp(fadeInAnim, 0.15f, 1f);
        } else {
            ARGB.setVector4fFromARGB32(minecraft.gameRenderer.gameRenderState().guiRenderState.clearColorOverride,
                    ArsenicSplash.background());
            alpha = 1f;
        }

        boolean done = fadeOutStart > -1L;
        ArsenicSplash.draw(graphics, done ? 1f : reload.getActualProgress(), done ? "Ready" : "Loading resources", alpha);

        if (fadeOutAnim >= 2f) {
            minecraft.gui.setOverlay(null);
            ArsenicSplash.reset();
        }
    }
}
