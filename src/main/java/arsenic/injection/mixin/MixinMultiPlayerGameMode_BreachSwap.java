package arsenic.injection.mixin;

import arsenic.main.Arsenic;
import arsenic.module.impl.ghost.BreachSwap;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets {@link BreachSwap} put a Breach mace in hand before the attack syncs the carried item and sends the hit. */
@Mixin(MultiPlayerGameMode.class)
public abstract class MixinMultiPlayerGameMode_BreachSwap {

    @Inject(method = "attack", at = @At("HEAD"))
    private void arsenic$breachSwap(Player player, Entity target, CallbackInfo ci) {
        if (Arsenic.getArsenic() == null || Arsenic.getArsenic().getModuleManager() == null)
            return;
        BreachSwap module = Arsenic.getArsenic().getModuleManager().getModuleByClass(BreachSwap.class);
        if (module != null && module.isEnabled())
            module.onAttack(player, target);
    }
}
