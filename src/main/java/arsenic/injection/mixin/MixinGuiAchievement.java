package arsenic.injection.mixin;

import arsenic.runtime.hooks.GuiHooks;
import net.minecraft.client.gui.achievement.GuiAchievement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Never draws the "Achievement get!" toast in the top right. */
@Mixin(GuiAchievement.class)
public abstract class MixinGuiAchievement {

    @Inject(method = "updateAchievementWindow", at = @At("HEAD"), cancellable = true)
    private void arsenic$noToast(CallbackInfo ci) {
        if (GuiHooks.updateAchievementWindow((GuiAchievement) (Object) this))
            ci.cancel();
    }
}
