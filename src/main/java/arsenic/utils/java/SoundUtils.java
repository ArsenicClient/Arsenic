package arsenic.utils.java;

import arsenic.main.Arsenic;
import arsenic.module.impl.visual.ClickGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.util.ResourceLocation;

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

    private static volatile long lastGuiSound = 0L;
    private static volatile long lastSlide = 0L;
    /** How often the slider tone re-triggers so it reads as one continuous ring. */
    private static final long SLIDE_INTERVAL_MS = 50L;

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
    //  Chord voicings - every UI action gets its own chord, all of them
    //  diatonic to C major. Indices map into CMAJ (0=C 1=D 2=E 3=F 4=G
    //  5=A 6=B 7=C'). Simultaneous notes = a chord rather than a bleep.
    // ---------------------------------------------------------------

    /** I  - C major (C E G): bright and resolved. Turning something ON. */
    private static final int[] CHORD_ENABLE   = { 0, 2, 4 };
    /** vi - A minor (A C E): softer, settling. Turning something OFF. */
    private static final int[] CHORD_DISABLE  = { 2, 5, 7 };
    /** V  - G major (D G B): forward motion. Switching category. */
    private static final int[] CHORD_CATEGORY = { 1, 4, 6 };
    /** IV - F major (F A C): opening up. Expanding a module / settings. */
    private static final int[] CHORD_OPEN     = { 3, 5, 7 };
    /** ii - D minor (D F A): a small turn. Cycling an enum option. */
    private static final int[] CHORD_ENUM     = { 1, 3, 5 };
    /** iii- E minor (E G B): contemplative. Setting / clearing a keybind. */
    private static final int[] CHORD_KEYBIND  = { 2, 4, 6 };
    /** Neutral open fifth (C G): generic click that isn't any of the above. */
    private static final int[] CHORD_CLICK    = { 0, 4 };

    private static boolean guiSoundsEnabled() {
        try {
            ClickGui cg = Arsenic.getArsenic().getModuleManager().getModuleByClass(ClickGui.class);
            return cg == null || cg.sounds.getValue();
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Plays a whole chord as one gesture. The debounce is checked once for the
     * chord (not per note) so nested click dispatch collapses to a single
     * chord, but the individual notes all sound together.
     */
    private static void chord(int[] degrees) {
        if (!guiSoundsEnabled())
            return;
        long now = System.currentTimeMillis();
        if (now - lastGuiSound < DEBOUNCE_MS)
            return;
        lastGuiSound = now;
        for (int d : degrees) {
            int idx = Math.max(0, Math.min(CMAJ.length - 1, d));
            playEvent(CMAJ[idx], 1.0f);
        }
    }

    // ---------------------------------------------------------------
    //  Semantic, per-action chords
    // ---------------------------------------------------------------

    /** Turning a module/button ON. */
    public static void chordEnable()   { chord(CHORD_ENABLE); }
    /** Turning a module/button OFF. */
    public static void chordDisable()  { chord(CHORD_DISABLE); }
    /** Switching the selected category (or search). */
    public static void chordCategory() { chord(CHORD_CATEGORY); }
    /** Opening/expanding a module panel or settings. */
    public static void chordOpen()     { chord(CHORD_OPEN); }
    /** Cycling through an enum property. */
    public static void chordEnum()     { chord(CHORD_ENUM); }
    /** Setting or clearing a keybind. */
    public static void chordKeybind()  { chord(CHORD_KEYBIND); }
    /** Generic click that doesn't have a more specific meaning. */
    public static void chordClick()    { chord(CHORD_CLICK); }

    /** Plays a single scale degree (0..7) as a chord of one note. */
    public static void note(int degree) {
        chord(new int[]{ degree });
    }

    // ---------------------------------------------------------------
    //  Back-compat aliases (kept so existing call sites keep working)
    // ---------------------------------------------------------------

    /** @deprecated use {@link #chordClick()}. */
    public static void cmajStep()          { chordClick(); }
    /** @deprecated use {@link #chordEnable()}. */
    public static void cmajUp()            { chordEnable(); }
    /** @deprecated use {@link #chordDisable()}. */
    public static void cmajDown()          { chordDisable(); }
    /** @deprecated use a semantic chord method. */
    public static void cmajTone(int degree){ chordKeybind(); }

    /**
     * A near-continuous ringing tone for slider drags. Pitch tracks the slider
     * position: the min end rings at C, the max end rings at the C one octave
     * above (pitch 1.0 -> 2.0). Re-triggered on a short interval so it reads as
     * one sustained, sliding tone rather than discrete ticks. Uses its own
     * throttle so it isn't swallowed by the chord debounce.
     *
     * @param fraction slider position, 0 (min) .. 1 (max)
     */
    public static void slide(float fraction) {
        if (!guiSoundsEnabled())
            return;
        long now = System.currentTimeMillis();
        if (now - lastSlide < SLIDE_INTERVAL_MS)
            return;
        lastSlide = now;
        float f = Math.max(0f, Math.min(1f, fraction));
        // C at the min bound, up a full octave to C at the max bound
        playEvent(CMAJ[0], 1.0f + f);
    }

    /** Soft high tick for hovers, typing and slider drags (pitched up). */
    public static void tick() {
        if (!guiSoundsEnabled())
            return;
        long now = System.currentTimeMillis();
        if (now - lastGuiSound < DEBOUNCE_MS)
            return;
        lastGuiSound = now;
        playEvent("tick", 1.2f);
    }
}
