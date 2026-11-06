package arsenic.injection.mixin;

import arsenic.main.Arsenic;
import arsenic.module.impl.ghost.TotemHit;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets {@link TotemHit} swap a totem-in-hand hit on a player to the hotbar sword. */
@Mixin(MultiPlayerGameMode.class)
public abstract class MixinMultiPlayerGameMode_TotemHit {

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void arsenic$totemHit(Player player, Entity target, CallbackInfo ci) {
        if (Arsenic.getArsenic() == null || Arsenic.getArsenic().getModuleManager() == null)
            return;
        TotemHit module = Arsenic.getArsenic().getModuleManager().getModuleByClass(TotemHit.class);
        if (module != null && module.isEnabled() && module.redirectAttack(player, target))
            ci.cancel();
    }
}
