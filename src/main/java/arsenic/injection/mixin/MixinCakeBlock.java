package arsenic.injection.mixin;

import arsenic.main.Arsenic;
import arsenic.module.impl.player.FastCake;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.CakeBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CakeBlock.class)
public class MixinCakeBlock {

    @Redirect(method = "eat", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;canEat(Z)Z"))
    private static boolean arsenic$bypassHungerCheck(Player player, boolean ignoreHunger) {
        FastCake fastCake = Arsenic.getArsenic().getModuleManager().getModuleByClass(FastCake.class);
        if (fastCake != null && fastCake.isEnabled())
            return true;
        return player.canEat(ignoreHunger);
    }
}
