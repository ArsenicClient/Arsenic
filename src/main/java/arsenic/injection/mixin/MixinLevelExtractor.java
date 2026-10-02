package arsenic.injection.mixin;

import arsenic.event.impl.EventRenderWorldLast;
import arsenic.main.Arsenic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * World-space drawing. The frame's main-thread gizmo collector is open while the level is
 * extracted, and the gizmos are drained right after this method, so anything drawn from
 * {@link EventRenderWorldLast} shows up in the same frame.
 */
@Mixin(LevelExtractor.class)
public abstract class MixinLevelExtractor {

    @Inject(method = "extractGizmos", at = @At("HEAD"))
    private void arsenic$renderWorld(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null)
            return;
        Arsenic.getArsenic().getEventManager().post(
                new EventRenderWorldLast(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false)));
    }
}
