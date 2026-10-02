package arsenic.injection.mixin;

import arsenic.main.Arsenic;
import arsenic.module.impl.ghost.HitSelect;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets HitSelect hold back a hit at its true source.
 * <p>
 * {@code attack} sends the attack packet and then runs the client-side attack. Cancelling at HEAD
 * suppresses both together, so a held hit never desyncs the client from the server. Every attack
 * path - the vanilla left click, KillAura, Hitflick, AntiFireball - routes through here.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MixinMultiPlayerGameMode {

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void arsenic$hitSelect(Player player, Entity target, CallbackInfo ci) {
        HitSelect hitSelect = Arsenic.getArsenic().getModuleManager().getModuleByClass(HitSelect.class);
        if (hitSelect != null && hitSelect.shouldBlock(target))
            ci.cancel();
    }
}
