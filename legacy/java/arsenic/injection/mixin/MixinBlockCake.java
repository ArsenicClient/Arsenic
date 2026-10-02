package arsenic.injection.mixin;

import arsenic.main.Arsenic;
import arsenic.module.impl.player.FastCake;
import net.minecraft.block.BlockCake;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BlockCake.class)
public class MixinBlockCake {

    @Redirect(method = "eatCake", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/EntityPlayer;canEat(Z)Z"))
    private boolean arsenic$bypassHungerCheck(EntityPlayer player, boolean ignoreHunger) {
        FastCake fastCake = Arsenic.getArsenic().getModuleManager().getModuleByClass(FastCake.class);
        if (fastCake != null && fastCake.isEnabled())
            return true;
        return player.canEat(ignoreHunger);
    }
}
