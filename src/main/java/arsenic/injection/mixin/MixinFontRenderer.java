package arsenic.injection.mixin;

import arsenic.runtime.hooks.RenderHooks;
import net.minecraft.client.gui.FontRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Hook logic lives in {@link RenderHooks}, shared with the injected client. */
@Mixin(priority = 1111, value = FontRenderer.class)
public abstract class MixinFontRenderer {

    @ModifyVariable(method = "renderString", at = @At("HEAD"), argsOnly = true)
    private String formatRenderedText(String text) {
        return RenderHooks.renderStringText((FontRenderer) (Object) this, text, 0f, 0f, 0, false);
    }
}
