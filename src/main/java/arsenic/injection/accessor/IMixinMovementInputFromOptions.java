package arsenic.injection.accessor;

import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.MovementInputFromOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MovementInputFromOptions.class)
public interface IMixinMovementInputFromOptions {

    @Accessor
    GameSettings getGameSettings();

}
