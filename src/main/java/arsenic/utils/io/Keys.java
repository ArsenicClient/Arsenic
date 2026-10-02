package arsenic.utils.io;

import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Key binds as SDL scancodes - what Minecraft 26.x reports in {@code KeyEvent#key()} and uses for
 * {@link InputConstants} - plus the names commands and the GUI show for them.
 * <p>
 * 1.8 stored LWJGL2 key codes, which are a different numbering. {@link #fromLwjgl2(int)} converts
 * binds read from an old config so they keep pointing at the same physical key.
 */
public final class Keys {

    public static final int NONE = 0;

    private static final String PREFIX = "key.keyboard.";

    /** LWJGL2 Keyboard code -> modern key name (without the {@code key.keyboard.} prefix). */
    private static final Map<Integer, String> LWJGL2 = new HashMap<>();

    static {
        String[] row = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"};
        for (int i = 0; i < row.length; i++)
            LWJGL2.put(2 + i, row[i]);
        String letters = "qwertyuiop";
        for (int i = 0; i < letters.length(); i++)
            LWJGL2.put(16 + i, String.valueOf(letters.charAt(i)));
        letters = "asdfghjkl";
        for (int i = 0; i < letters.length(); i++)
            LWJGL2.put(30 + i, String.valueOf(letters.charAt(i)));
        letters = "zxcvbnm";
        for (int i = 0; i < letters.length(); i++)
            LWJGL2.put(44 + i, String.valueOf(letters.charAt(i)));
        for (int i = 0; i < 10; i++)
            LWJGL2.put(59 + i, "f" + (i + 1));
        put(1, "escape"); put(12, "minus"); put(13, "equal"); put(14, "backspace"); put(15, "tab");
        put(26, "left.bracket"); put(27, "right.bracket"); put(28, "enter"); put(29, "left.control");
        put(39, "semicolon"); put(40, "apostrophe"); put(41, "grave.accent"); put(42, "left.shift");
        put(43, "backslash"); put(51, "comma"); put(52, "period"); put(53, "slash"); put(54, "right.shift");
        put(55, "keypad.multiply"); put(56, "left.alt"); put(57, "space"); put(58, "caps.lock");
        put(69, "num.lock"); put(70, "scroll.lock"); put(71, "keypad.7"); put(72, "keypad.8"); put(73, "keypad.9");
        put(74, "keypad.subtract"); put(75, "keypad.4"); put(76, "keypad.5"); put(77, "keypad.6");
        put(78, "keypad.add"); put(79, "keypad.1"); put(80, "keypad.2"); put(81, "keypad.3"); put(82, "keypad.0");
        put(83, "keypad.decimal"); put(87, "f11"); put(88, "f12"); put(157, "right.control"); put(184, "right.alt");
        put(199, "home"); put(200, "up"); put(201, "page.up"); put(203, "left"); put(205, "right"); put(207, "end");
        put(208, "down"); put(209, "page.down"); put(210, "insert"); put(211, "delete");
    }

    private static void put(int code, String name) {
        LWJGL2.put(code, name);
    }

    private Keys() {
    }

    /** Converts an LWJGL2 key code from a 1.8 config into a scancode; unknown keys become unbound. */
    public static int fromLwjgl2(int code) {
        String name = LWJGL2.get(code);
        return name == null ? NONE : getKeyIndex(name);
    }

    /** Short upper-case name for a scancode, e.g. {@code R}, {@code RIGHT_SHIFT}; {@code NONE} if unbound. */
    public static String getKeyName(int code) {
        if (code == NONE)
            return "NONE";
        String name = InputConstants.Type.KEYBOARD.getOrCreate(code).getName();
        if (name.startsWith(PREFIX))
            name = name.substring(PREFIX.length());
        return name.replace('.', '_').toUpperCase(Locale.ROOT);
    }

    /** The localised name Minecraft's controls screen would show, e.g. for the GUI. */
    public static String getDisplayName(int code) {
        if (code == NONE)
            return "None";
        return InputConstants.Type.KEYBOARD.getOrCreate(code).getDisplayName().getString();
    }

    /**
     * Parses a key name as typed by a user: {@code r}, {@code RIGHT_SHIFT}, {@code right.shift}, or
     * the 1.8 spellings {@code RSHIFT}/{@code LCONTROL}. Returns {@link #NONE} if it is not a key.
     */
    public static int getKeyIndex(String name) {
        if (name == null || name.isEmpty())
            return NONE;
        String key = name.toLowerCase(Locale.ROOT).replace('_', '.');
        switch (key) {
            case "none": return NONE;
            case "rshift": key = "right.shift"; break;
            case "lshift": key = "left.shift"; break;
            case "rcontrol": case "rctrl": key = "right.control"; break;
            case "lcontrol": case "lctrl": key = "left.control"; break;
            case "rmenu": case "ralt": key = "right.alt"; break;
            case "lmenu": case "lalt": key = "left.alt"; break;
            case "return": key = "enter"; break;
            case "back": key = "backspace"; break;
            case "grave": key = "grave.accent"; break;
            case "equals": key = "equal"; break;
            case "lbracket": key = "left.bracket"; break;
            case "rbracket": key = "right.bracket"; break;
            case "prior": key = "page.up"; break;
            case "next": key = "page.down"; break;
            case "capital": key = "caps.lock"; break;
            default: break;
        }
        try {
            InputConstants.Key k = InputConstants.getKey(PREFIX + key);
            return k.getType() == InputConstants.Type.KEYBOARD ? k.getValue() : NONE;
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }

    /** Every named keyboard key, for command auto-completion. */
    public static List<String> keyNames() {
        List<String> names = new ArrayList<>();
        for (int code = 1; code < 300; code++) {
            String name = InputConstants.Type.KEYBOARD.getOrCreate(code).getName();
            if (!name.startsWith(PREFIX))
                continue;
            // keys Minecraft has no name for are called key.keyboard.<scancode>; skip those
            String suffix = name.substring(PREFIX.length());
            if (suffix.length() == 1 || !suffix.matches("\\d+"))
                names.add(getKeyName(code));
        }
        return names;
    }

    /**
     * Whether the physical key or button bound to {@code mapping} is held right now, regardless of
     * what the mapping itself thinks - modules that force a mapping down need the real state.
     */
    public static boolean isPhysicallyDown(net.minecraft.client.KeyMapping mapping) {
        InputConstants.Key key = ((arsenic.injection.accessor.IMixinKeyMapping) mapping).getBoundKey();
        if (key.getType() == InputConstants.Type.MOUSE)
            return isMouseDown(fromSdlButton(key.getValue()));
        return isKeyDown(key.getValue());
    }

    public static boolean isKeyDown(int code) {
        return code != NONE && InputConstants.isKeyDown(code);
    }

    /**
     * Mouse buttons by the old LWJGL index: 0 left, 1 right, 2 middle. Reads SDL directly because
     * MouseHandler only tracks button state while no screen is open, and the GUI needs it too.
     */
    public static boolean isMouseDown(int button) {
        int mask = org.lwjgl.sdl.SDLMouse.SDL_GetMouseState(null, null);
        return switch (button) {
            case 0 -> (mask & 1) != 0;
            case 1 -> (mask & 4) != 0;
            case 2 -> (mask & 2) != 0;
            default -> false;
        };
    }

    /** SDL mouse button number (1 left, 2 middle, 3 right) to the old LWJGL index. */
    public static int fromSdlButton(int sdlButton) {
        return switch (sdlButton) {
            case 1 -> 0;
            case 3 -> 1;
            case 2 -> 2;
            default -> sdlButton - 1;
        };
    }
}
