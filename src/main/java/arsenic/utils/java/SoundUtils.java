package arsenic.utils.java;

import arsenic.main.Arsenic;
import arsenic.gui.click.GuiStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.Identifier;

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
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getSoundManager() == null)
                return;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(
                    SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(DOMAIN, name)), pitch));
        } catch (Exception ignored) {
        }
    }


    // 1.8 sound names that were renamed outright; the rest follow the general rules in byName
    private static final java.util.Map<String, String> LEGACY_SOUNDS = java.util.Map.ofEntries(
            java.util.Map.entry("random.orb", "entity.experience_orb.pickup"),
            java.util.Map.entry("random.levelup", "entity.player.levelup"),
            java.util.Map.entry("random.anvil_land", "block.anvil.land"),
            java.util.Map.entry("random.click", "ui.button.click"),
            java.util.Map.entry("random.pop", "entity.item.pickup"),
            java.util.Map.entry("random.successful_hit", "entity.arrow.hit_player"),
            java.util.Map.entry("random.bow", "entity.arrow.shoot"),
            java.util.Map.entry("random.break", "entity.item.break"),
            java.util.Map.entry("random.explode", "entity.generic.explode"),
            java.util.Map.entry("random.fizz", "block.fire.extinguish"),
            java.util.Map.entry("random.hurt", "entity.player.hurt"),
            java.util.Map.entry("game.player.hurt", "entity.player.hurt"),
            java.util.Map.entry("note.bd", "block.note_block.basedrum"));

    /**
     * A vanilla sound by its modern id ("entity.experience_orb.pickup") or its 1.8 name ("random.orb", "note.pling",
     * "mob.villager.yes"), or null.
     */
    public static SoundEvent byName(String name) {
        String key = name.trim().toLowerCase(java.util.Locale.ROOT);
        if (key.startsWith("minecraft:"))
            key = key.substring("minecraft:".length());
        if (LEGACY_SOUNDS.containsKey(key))
            key = LEGACY_SOUNDS.get(key);
        else if (key.startsWith("note."))
            key = "block.note_block." + key.substring("note.".length());
        else if (key.startsWith("mob."))
            key = "entity." + key.substring("mob.".length());
        Identifier id = Identifier.tryParse(key);
        if (id == null)
            return null;
        return net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getOptional(id).orElse(null);
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
    public static void chordSlider()   { chordClick(); }


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
