package arsenic.injection.mixin;

import arsenic.event.impl.EventAttack;
import arsenic.main.Arsenic;
import arsenic.module.impl.ghost.Reach;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Player.class, priority = 995)
public abstract class MixinPlayer {

    @Inject(method = "attack", at = @At("HEAD"))
    private void arsenic$attack(Entity target, CallbackInfo ci) {
        if ((Object) this == Minecraft.getInstance().player)
            Arsenic.getInstance().getEventManager().post(new EventAttack(target));
    }

    /** Reach used to replace the whole of getMouseOver; the range is an attribute now. */
    @Inject(method = "entityInteractionRange", at = @At("RETURN"), cancellable = true)
    private void arsenic$reach(CallbackInfoReturnable<Double> cir) {
        if ((Object) this != Minecraft.getInstance().player)
            return;
        Reach reach = Arsenic.getArsenic().getModuleManager().getModuleByClass(Reach.class);
        if (reach != null && reach.isEnabled())
            cir.setReturnValue(Math.max(cir.getReturnValue(), reach.getReach()));
    }
}
