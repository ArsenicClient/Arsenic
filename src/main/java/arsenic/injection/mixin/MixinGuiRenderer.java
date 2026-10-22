package arsenic.injection.mixin;

import arsenic.utils.render.BlurBackdrop;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
public abstract class MixinGuiRenderer {

    /** The main target holds only the world here: take the blurred copy HUD panels show through. */
    @Inject(method = "draw", at = @At("HEAD"))
    private void arsenic$blurBackdrop(CallbackInfo ci) {
        BlurBackdrop.process();
    }
}
