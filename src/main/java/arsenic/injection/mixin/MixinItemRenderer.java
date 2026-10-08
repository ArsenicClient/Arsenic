package arsenic.injection.mixin;

import arsenic.runtime.hooks.RenderHooks;
import net.minecraft.client.renderer.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hook logic lives in {@link RenderHooks}, shared with the injected client. */
@Mixin(value = ItemRenderer.class, priority = 1111)
public abstract class MixinItemRenderer {

    @Inject(method = "renderItemInFirstPerson", at = @At("HEAD"), cancellable = true)
    public void renderItemInFirstPerson(float partialTicks, CallbackInfo ci) {
        if (RenderHooks.renderItemInFirstPerson((ItemRenderer) (Object) this, partialTicks))
            ci.cancel();
    }
}
