package arsenic.injection.accessor;

import net.minecraft.entity.Entity;
import net.minecraft.util.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Entity.class)
public interface IMixinEntity {

    @Invoker
    Vec3 invokeGetVectorForRotation(float p_getVectorForRotation_1_, float p_getVectorForRotation_2_);

}
