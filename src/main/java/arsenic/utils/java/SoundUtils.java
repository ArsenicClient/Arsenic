package arsenic.utils.java;

import arsenic.main.Arsenic;
import arsenic.module.impl.visual.ClickGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.util.ResourceLocation;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Plays the client's UI sounds through Minecraft's own sound engine
 * (SoundHandler + sounds.json), rather than javax audio clips. This means no
 * decode latency, no threading hitches, and everything obeys the game's
 * master volume slider. All samples live in {@code assets/arsenic/sounds/}
 * and are registered in {@code assets/arsenic/sounds.json}.
 */
public class SoundUtils {

    private static final String DOMAIN = "arsenic";
    private static final long DEBOUNCE_MS = 18L;

    /** C major scale sound events, C4 -> C5. */
    private static final String[] CMAJ = {
            "cmaj0", "cmaj1", "cmaj2", "cmaj3", "cmaj4", "cmaj5", "cmaj6", "cmaj7"
    };

    private static final AtomicInteger STEP = new AtomicInteger(0);
    private static volatile long lastGuiSound = 0L;

    // ---------------------------------------------------------------
    //  Core playback (Minecraft sound engine)
    // ---------------------------------------------------------------

    public static void playSound(String name) {
        playEvent(name, 1.0f);
    }

    /** Kept for API compatibility - the MC engine handles gain via the volume slider. */
    public static void playSound(String name, float volume) {
        playEvent(name, 1.0f);
    }

    /** Plays a registered arsenic sound event at the given pitch through the UI channel. */
    public static void playEvent(String name, float pitch) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.getSoundHandler() == null)
                return;
            mc.getSoundHandler().playSound(
                    PositionedSoundRecord.create(new ResourceLocation(DOMAIN, name), pitch));
        } catch (Exception ignored) {
        }
    }

    // ---------------------------------------------------------------
    //  CMaj helpers for the ClickGUI
    // ---------------------------------------------------------------

    private static boolean guiSoundsEnabled() {
        try {
            ClickGui cg = Arsenic.getArsenic().getModuleManager().getModuleByClass(ClickGui.class);
            return cg == null || cg.sounds.getValue();
        } catch (Exception e) {
            return true;
        }
    }

    /** Debounced play so nested click dispatch doesn't stack duplicate notes. */
    private static void guiPlay(String name, float pitch) {
        if (!guiSoundsEnabled())
            return;
        long now = System.currentTimeMillis();
        if (now - lastGuiSound < DEBOUNCE_MS)
            return;
        lastGuiSound = now;
        playEvent(name, pitch);
    }

    /** Plays a specific scale degree (0..7), clamped. */
    public static void note(int degree) {
        int d = Math.max(0, Math.min(CMAJ.length - 1, degree));
        guiPlay(CMAJ[d], 1.0f);
    }

    /** Generic click: next note ascending through the scale, wrapping. */
    public static void cmajStep() {
        int idx = STEP.getAndUpdate(i -> (i + 1) % CMAJ.length);
        guiPlay(CMAJ[idx % CMAJ.length], 1.0f);
    }

    /** Enabling something: step up the scale. */
    public static void cmajUp() {
        int idx = STEP.getAndUpdate(i -> (i + 1) % CMAJ.length);
        guiPlay(CMAJ[idx % CMAJ.length], 1.0f);
    }

    /** Disabling something: step down the scale. */
    public static void cmajDown() {
        int idx = STEP.getAndUpdate(i -> (i - 1 + CMAJ.length) % CMAJ.length);
        guiPlay(CMAJ[(idx + CMAJ.length) % CMAJ.length], 1.0f);
    }

    /** A fixed scale note (e.g. category switch, opening settings). */
    public static void cmajTone(int degree) {
        note(degree);
    }

    /** Soft high tick for hovers and slider drags (pitched up a little). */
    public static void tick() {
        guiPlay("tick", 1.2f);
    }
}
