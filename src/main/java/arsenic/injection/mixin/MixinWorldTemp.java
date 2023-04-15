package arsenic.injection.mixin;

import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(World.class)
public class MixinWorldTemp {

    @Inject(method = "playSoundEffect", at = @At("HEAD"))
    public void playSound(double x, double y, double z, String soundName, float volume, float pitch, CallbackInfo ci) {
        System.out.println(soundName + " " + volume + " " + pitch);
    }
}
