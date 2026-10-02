package arsenic.injection.mixin;

import arsenic.event.impl.EventPlayerJoinWorld;
import arsenic.main.Arsenic;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientLevel.class, priority = 1111)
public abstract class MixinClientLevel {

    @Inject(method = "addEntity", at = @At("HEAD"))
    private void arsenic$addEntity(Entity entity, CallbackInfo ci) {
        if (entity instanceof Player player)
            Arsenic.getArsenic().getEventManager().post(new EventPlayerJoinWorld(player, player.level()));
    }
}
