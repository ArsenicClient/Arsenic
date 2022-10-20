package arsenic.injection.accessor;

import net.minecraft.client.settings.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = KeyBinding.class)
public interface IMixinKeyBinding {

    @Accessor
    boolean isPressed();

    @Accessor
    void setPressed(boolean pressed);

}
