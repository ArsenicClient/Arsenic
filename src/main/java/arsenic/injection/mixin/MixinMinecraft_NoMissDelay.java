package arsenic.injection.mixin;

import arsenic.main.Arsenic;
import arsenic.module.impl.ghost.NoMissDelay;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets {@link NoMissDelay} keep the attack cooldown on a missed swing: the cooldown reset, the local swing and the
 * punch packet (which makes the server reset its cooldown too) are all skipped together so client and server agree.
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraft_NoMissDelay {

    private static NoMissDelay arsenic$noMissDelay() {
        if (Arsenic.getArsenic() == null || Arsenic.getArsenic().getModuleManager() == null)
            return null;
        return Arsenic.getArsenic().getModuleManager().getModuleByClass(NoMissDelay.class);
    }

    @Inject(method = "startAttack", at = @At("HEAD"))
    private void arsenic$noMissDelayBegin(CallbackInfoReturnable<Boolean> cir) {
        NoMissDelay module = arsenic$noMissDelay();
        if (module != null)
            module.beginClick();
    }

    @Inject(method = "startAttack", at = @At("RETURN"))
    private void arsenic$noMissDelayEnd(CallbackInfoReturnable<Boolean> cir) {
        NoMissDelay module = arsenic$noMissDelay();
        if (module != null)
            module.endClick();
    }

    @WrapWithCondition(method = "startAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;resetAttackStrengthTicker()V"))
    private boolean arsenic$noMissDelayReset(LocalPlayer player) {
        NoMissDelay module = arsenic$noMissDelay();
        return module == null || !module.isSuppressing();
    }

    @WrapOperation(method = "startAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z"))
    private boolean arsenic$noMissDelaySwing(LocalPlayer player, InteractionHand hand, SwingAnimation animation, boolean flag, Operation<Boolean> original) {
        NoMissDelay module = arsenic$noMissDelay();
        if (module != null && module.isSuppressing())
            return false;
        return original.call(player, hand, animation, flag);
    }

    @WrapWithCondition(method = "startAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private boolean arsenic$noMissDelayPunch(ClientPacketListener connection, Packet<?> packet) {
        if (!(packet instanceof ServerboundPunchPacket))
            return true;
        NoMissDelay module = arsenic$noMissDelay();
        return module == null || !module.isSuppressing();
    }
}
