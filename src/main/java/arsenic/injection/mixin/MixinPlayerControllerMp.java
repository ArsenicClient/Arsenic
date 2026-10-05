package arsenic.injection.mixin;

import arsenic.main.Arsenic;
import arsenic.module.impl.ghost.HitSelect;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerControllerMP.class)
public class MixinPlayerControllerMp {

    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    private void arsenic$hitSelect(EntityPlayer playerIn, Entity targetEntity, CallbackInfo ci) {
        HitSelect hitSelect = Arsenic.getArsenic().getModuleManager().getModuleByClass(HitSelect.class);
        if (hitSelect != null && hitSelect.shouldBlock(targetEntity))
            ci.cancel();
    }
}
