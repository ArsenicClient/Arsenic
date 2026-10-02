package arsenic.injection.mixin;

import arsenic.event.impl.EventGameLoop;
import arsenic.event.impl.EventRunTick;
import arsenic.main.Arsenic;
import arsenic.module.ModuleManager;
import arsenic.module.impl.ghost.Clicker;
import arsenic.module.impl.ghost.HitSelect;
import arsenic.module.impl.ghost.Hitflick;
import arsenic.module.impl.ghost.NoHitDelay;
import arsenic.module.impl.player.FastPlace;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Minecraft.class, priority = 1111)
public abstract class MixinMinecraft {

    @Shadow
    private int rightClickDelay;
    @Shadow
    public int missTime;
    @Shadow
    @Final
    public Options options;
    @Shadow
    public HitResult hitResult;

    @Inject(method = "tick", at = @At("HEAD"))
    private void arsenic$tickHead(CallbackInfo ci) {
        Arsenic.getInstance().getEventManager().post(new EventGameLoop());
        Arsenic.getInstance().getEventManager().post(new EventRunTick());
    }

    /**
     * Swallows the player's own attack/use presses while a silent rotation asked for input to be
     * blocked. The original {@code consumeClick()} is still called so the queued press count is
     * drained - otherwise every press held back would fire in a burst once blocking ends. Direct
     * client-side attacks (KillAura, Clicker, ...) go through other paths and are unaffected.
     */
    @Redirect(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;consumeClick()Z"))
    private boolean arsenic$consumeClick(KeyMapping keyMapping) {
        if (!shouldBlockInput(keyMapping))
            return keyMapping.consumeClick();
        while (keyMapping.consumeClick()) { }
        return false;
    }

    /** Same as above for the held-down reads - continuous block breaking and item-use repeat. */
    @Redirect(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDown()Z"))
    private boolean arsenic$isDown(KeyMapping keyMapping) {
        return keyMapping.isDown() && !shouldBlockInput(keyMapping);
    }

    private boolean shouldBlockInput(KeyMapping keyMapping) {
        if (keyMapping != options.keyAttack && keyMapping != options.keyUse)
            return false;
        return Arsenic.getArsenic().getSilentRotationManager().isBlockingUserInput();
    }

    @Inject(method = "shouldEntityAppearGlowing", at = @At("RETURN"), cancellable = true)
    private void arsenic$espGlow(net.minecraft.world.entity.Entity entity, CallbackInfoReturnable<Boolean> cir) {
        arsenic.module.impl.visual.ESP esp = Arsenic.getArsenic().getModuleManager().getModuleByClass(arsenic.module.impl.visual.ESP.class);
        if (esp != null && esp.shouldGlow(entity))
            cir.setReturnValue(true);
    }

    @Inject(method = "startUseItem", at = @At("RETURN"))
    private void arsenic$fastPlace(CallbackInfo ci) {
        FastPlace fastPlace = Arsenic.getArsenic().getModuleManager().getModuleByClass(FastPlace.class);
        if (fastPlace == null || !fastPlace.isEnabled())
            return;
        rightClickDelay = fastPlace.getTickDelay();
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void arsenic$startAttack(CallbackInfoReturnable<Boolean> cir) {
        ModuleManager modules = Arsenic.getArsenic().getModuleManager();

        // better hitreg: a miss no longer locks out the next click for 10 ticks
        if (modules.getModuleByClass(NoHitDelay.class).isEnabled() || modules.getModuleByClass(Clicker.class).isEnabled())
            this.missTime = 0;

        if (!(hitResult instanceof EntityHitResult entityHit))
            return;
        Entity target = entityHit.getEntity();

        // HitSelect holds the whole click - attack, swing and the punch packet - so a held hit
        // stays invisible to the server instead of desyncing the client from it.
        HitSelect hitSelect = modules.getModuleByClass(HitSelect.class);
        if (hitSelect != null && hitSelect.shouldBlock(target)) {
            cir.setReturnValue(false);
            return;
        }

        Hitflick hitflick = modules.getModuleByClass(Hitflick.class);
        if (hitflick != null && hitflick.isEnabled() && hitflick.shouldFlick() && hitflick.armFlick(target)) {
            // Only swallow the real hit once the flick actually armed - Void mode can decline
            // (no angle empties into the void), and the attack must go through normally then.
            this.hitResult = BlockHitResult.miss(hitResult.getLocation(), entityHit.getEntity().getDirection(), entityHit.getEntity().blockPosition());
        }
    }
}
