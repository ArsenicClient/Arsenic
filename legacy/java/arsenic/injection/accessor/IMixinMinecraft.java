package arsenic.injection.accessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.Timer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface IMixinMinecraft {
    @Accessor
    Timer getTimer();
    @Accessor("framebufferMc")
    Framebuffer getFramebufferMc();
    @Invoker("clickMouse")
    void leftClick();
}
