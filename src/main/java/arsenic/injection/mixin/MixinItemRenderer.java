package arsenic.injection.mixin;

import arsenic.utils.minecraft.PlayerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.ItemRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ItemRenderer.class, priority = 1111)
public class MixinItemRenderer {

    @Shadow @Final private Minecraft mc;

    @Inject(method = "renderItemInFirstPerson", at = @At("HEAD"))
    public void renderItemInFirstPerson(float partialTicks, CallbackInfo ci) {
        if(mc.gameSettings.keyBindUseItem.isKeyDown()) //very shitty autoblock animation
            GlStateManager.translate(0.25F * mc.thePlayer.swingProgress, -0.1F * mc.thePlayer.swingProgress, 0f);
    }
}
