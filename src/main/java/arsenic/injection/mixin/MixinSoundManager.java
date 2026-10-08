package arsenic.injection.mixin;

import arsenic.runtime.hooks.MiscHooks;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Feeds the Recorder's SoundCapture; hook logic lives in {@link MiscHooks}. */
@Mixin(SoundManager.class)
public abstract class MixinSoundManager {

    @Inject(method = "playSound", at = @At("RETURN"))
    private void arsenic$capturePlay(ISound sound, CallbackInfo ci) {
        MiscHooks.playSoundReturn((SoundManager) (Object) this, sound);
    }

    @Inject(method = "stopSound", at = @At("HEAD"))
    private void arsenic$captureStop(ISound sound, CallbackInfo ci) {
        MiscHooks.stopSoundHead((SoundManager) (Object) this, sound);
    }

    @Inject(method = "stopAllSounds", at = @At("HEAD"))
    private void arsenic$captureStopAll(CallbackInfo ci) {
        MiscHooks.stopAllSoundsHead((SoundManager) (Object) this);
    }

    @Inject(method = "pauseAllSounds", at = @At("HEAD"))
    private void arsenic$capturePause(CallbackInfo ci) {
        MiscHooks.pauseAllSoundsHead((SoundManager) (Object) this);
    }

    @Inject(method = "resumeAllSounds", at = @At("HEAD"))
    private void arsenic$captureResume(CallbackInfo ci) {
        MiscHooks.resumeAllSoundsHead((SoundManager) (Object) this);
    }
}
