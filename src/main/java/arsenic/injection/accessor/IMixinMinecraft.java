package arsenic.injection.accessor;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface IMixinMinecraft {

    @Invoker("startAttack")
    boolean leftClick();

    @Invoker("startUseItem")
    void rightClick();

    @Accessor("rightClickDelay")
    int getRightClickDelay();

    @Accessor("rightClickDelay")
    void setRightClickDelay(int delay);
}
