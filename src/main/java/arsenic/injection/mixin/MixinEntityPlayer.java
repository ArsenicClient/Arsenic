package arsenic.injection.mixin;

import arsenic.event.impl.EventAttack;
import arsenic.main.Arsenic;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityPlayer.class)
public class MixinEntityPlayer {

    @Inject(method = "attackTargetEntityWithCurrentItem", at = @At("HEAD"))
    public void attackTargetEntityWithCurrentItem(Entity targetEntity, CallbackInfo ci) {
        Arsenic.getInstance().getEventManager().post(new EventAttack(targetEntity));
    }

}
