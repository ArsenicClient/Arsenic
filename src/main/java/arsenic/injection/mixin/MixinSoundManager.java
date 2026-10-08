package arsenic.injection.mixin;

import arsenic.utils.render.capture.SoundCapture;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SoundManager;
import net.minecraft.client.audio.SoundPoolEntry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/** Feeds the Recorder's {@link SoundCapture} with what the sound system is told to play. */
@Mixin(SoundManager.class)
public abstract class MixinSoundManager {

    @Shadow
    @Final
    private Map<String, ISound> playingSounds;

    @Shadow
    @Final
    private Map<ISound, String> invPlayingSounds;

    @Shadow
    private Map<ISound, SoundPoolEntry> playingSoundPoolEntries;

    // the sound and its chosen variant are only known once it has been handed to the sound system
    @Inject(method = "playSound", at = @At("RETURN"))
    private void arsenic$capturePlay(ISound sound, CallbackInfo ci) {
        if (!SoundCapture.isActive())
            return;
        String channel = invPlayingSounds.get(sound);
        SoundPoolEntry entry = playingSoundPoolEntries.get(sound);
        if (channel != null && entry != null)
            SoundCapture.onPlay(channel, sound, entry);
    }

    @Inject(method = "stopSound", at = @At("HEAD"))
    private void arsenic$captureStop(ISound sound, CallbackInfo ci) {
        if (!SoundCapture.isActive())
            return;
        String channel = invPlayingSounds.get(sound);
        if (channel != null)
            SoundCapture.onStop(channel);
    }

    @Inject(method = "stopAllSounds", at = @At("HEAD"))
    private void arsenic$captureStopAll(CallbackInfo ci) {
        if (!SoundCapture.isActive())
            return;
        for (String channel : playingSounds.keySet())
            SoundCapture.onStop(channel);
    }

    @Inject(method = "pauseAllSounds", at = @At("HEAD"))
    private void arsenic$capturePause(CallbackInfo ci) {
        SoundCapture.onPause();
    }

    @Inject(method = "resumeAllSounds", at = @At("HEAD"))
    private void arsenic$captureResume(CallbackInfo ci) {
        SoundCapture.onResume();
    }
}
