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

/**
 * Lets HitSelect hold back a hit at its true source.
 * <p>
 * {@code attackEntity} queues the {@code C02PacketUseEntity} and then runs the client-side attack.
 * By cancelling at the HEAD we suppress both together, so a held hit never desyncs the client from
 * the server (which is what simulation anticheats such as Grim flag when only the packet is
 * dropped). Every attack path - the vanilla left click, KillAura, Hitflick, AntiFireball - routes
 * through here, so the timing rules apply uniformly.
 */
@Mixin(PlayerControllerMP.class)
public class MixinPlayerControllerMp {

    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    private void arsenic$hitSelect(EntityPlayer playerIn, Entity targetEntity, CallbackInfo ci) {
        HitSelect hitSelect = Arsenic.getArsenic().getModuleManager().getModuleByClass(HitSelect.class);
        if (hitSelect != null && hitSelect.shouldBlock(targetEntity))
            ci.cancel();
    }
}
