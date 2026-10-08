package arsenic.injection.mixin;

import arsenic.runtime.hooks.PlayerHooks;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hook logic lives in {@link PlayerHooks}, shared with the injected client. */
@SideOnly(Side.CLIENT)
@Mixin(value = World.class, priority = 1111)
public class MixinWorld {

    @Inject(method = "spawnEntityInWorld", at = @At("HEAD"))
    public void spawnEntityInWorld(Entity entityIn, CallbackInfoReturnable<Boolean> cir) {
        PlayerHooks.spawnEntityInWorldHead((World) (Object) this, entityIn);
    }
}
