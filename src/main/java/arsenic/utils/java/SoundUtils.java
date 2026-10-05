package arsenic.utils.java;

import arsenic.main.Arsenic;
import arsenic.gui.click.GuiStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.util.ResourceLocation;

public class SoundUtils {

    private static final String DOMAIN = "arsenic";
    private static final long DEBOUNCE_MS = 18L;

    private static final String[] CMAJ = {
            "cmaj0", "cmaj1", "cmaj2", "cmaj3", "cmaj4", "cmaj5", "cmaj6", "cmaj7"
    };

    private static volatile long lastGuiSound = 0L;
    private static volatile long lastSlide = 0L;
    private static final long SLIDE_INTERVAL_MS = 50L;


    public static void playSound(String name) {
        playEvent(name, 1.0f);
    }

    public static void playSound(String name, float volume) {
        playEvent(name, 1.0f);
    }

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


    private static final int[] CHORD_ENABLE   = { 0, 2, 4 };
    private static final int[] CHORD_DISABLE  = { 2, 5, 7 };
    private static final int[] CHORD_CATEGORY = { 1, 4, 6 };
    private static final int[] CHORD_OPEN     = { 3, 5, 7 };
    private static final int[] CHORD_ENUM     = { 1, 3, 5 };
    private static final int[] CHORD_KEYBIND  = { 2, 4, 6 };
    private static final int[] CHORD_CLICK    = { 0, 4 };

    private static boolean guiSoundsEnabled() {
        try {
            return GuiStyle.soundsEnabled();
        } catch (Exception e) {
            return true;
        }
    }

    private static void chord(int[] degrees) {
        if (!guiSoundsEnabled())
            return;
        long now = System.currentTimeMillis();
        if (now - lastGuiSound < DEBOUNCE_MS)
            return;
        lastGuiSound = now;
        for (int d : degrees) {
            int idx = MathUtils.clamp(d, 0, CMAJ.length - 1);
            playEvent(CMAJ[idx], 1.0f);
        }
    }


    public static void chordEnable()   { chord(CHORD_ENABLE); }
    public static void chordDisable()  { chord(CHORD_DISABLE); }
    public static void chordCategory() { chord(CHORD_CATEGORY); }
    public static void chordOpen()     { chord(CHORD_OPEN); }
    public static void chordEnum()     { chord(CHORD_ENUM); }
    public static void chordKeybind()  { chord(CHORD_KEYBIND); }
    public static void chordClick()    { chord(CHORD_CLICK); }


    private static volatile long lastHitConfirm = 0L;
    private static final long HIT_CONFIRM_DEBOUNCE_MS = 120L;

    public static void hitConfirm() {
        long now = System.currentTimeMillis();
        if (now - lastHitConfirm < HIT_CONFIRM_DEBOUNCE_MS)
            return;
        lastHitConfirm = now;
        playEvent(CMAJ[4], 1.0f);
        playEvent(CMAJ[7], 1.0f);
    }

    public static void note(int degree) {
        chord(new int[]{ degree });
    }


    public static void cmajStep()          { chordClick(); }
    public static void cmajUp()            { chordEnable(); }
    public static void cmajDown()          { chordDisable(); }
    public static void cmajTone(int degree){ chordKeybind(); }

    public static void slide(float fraction) {
        if (!guiSoundsEnabled())
            return;
        long now = System.currentTimeMillis();
        if (now - lastSlide < SLIDE_INTERVAL_MS)
            return;
        lastSlide = now;
        float f = MathUtils.clamp01(fraction);
        playEvent(CMAJ[0], 1.0f + f);
    }

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
