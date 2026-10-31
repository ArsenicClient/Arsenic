package arsenic.utils.java;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Module binds are plain ints. A positive number is a keyboard key (LWJGL key code); a mouse button is stored as
 * {@code MOUSE_OFFSET + button}, which is the same negative encoding vanilla uses for its own key bindings
 * (so {@code GameSettings.getKeyDisplayString} already reads it). 0 is "no bind".
 * <p>
 * Buttons are numbered like LWJGL does: 0 is left, 1 is right, 2 is middle, 3 and up are the side buttons. They are
 * shown to the player 1-based (MOUSE3 is the middle button, MOUSE4 and MOUSE5 the usual thumb buttons).
 */
public final class BindUtils {

    public static final int MOUSE_OFFSET = -100;
    /** The first button that can be bound: left and right click would fire on every attack / block. */
    public static final int FIRST_BINDABLE_BUTTON = 2;
    private static final String MOUSE_PREFIX = "MOUSE";

    private BindUtils() {}

    public static int mouseKey(int button) {
        return MOUSE_OFFSET + button;
    }

    public static boolean isMouse(int key) {
        return key < 0;
    }

    public static int getButton(int key) {
        return key - MOUSE_OFFSET;
    }

    public static boolean isBindableButton(int button) {
        return button >= FIRST_BINDABLE_BUTTON;
    }

    /** The name shown for a bind, e.g. "G", "LSHIFT" or "MOUSE4". Never throws, whatever the number. */
    public static String getKeyName(int key) {
        if (isMouse(key))
            return MOUSE_PREFIX + (getButton(key) + 1);
        if (key == 0)
            return "NONE";
        String name = key < Keyboard.KEYBOARD_SIZE ? Keyboard.getKeyName(key) : null;
        return name == null ? "KEY" + key : name;
    }

    /**
     * The bind for a name typed by the player ("g", "f13", "mouse4").
     * @return the bind, or 0 when the name is not a key or a bindable mouse button
     */
    public static int parse(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        if (upper.startsWith(MOUSE_PREFIX)) {
            try {
                int button = Integer.parseInt(upper.substring(MOUSE_PREFIX.length())) - 1;
                return isBindableButton(button) ? mouseKey(button) : 0;
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return Keyboard.getKeyIndex(upper);
    }

    /** How many mouse buttons the game can see on this mouse (at least the usual five). */
    public static int mouseButtonCount() {
        try {
            return Math.max(5, Mouse.getButtonCount());
        } catch (IllegalStateException e) {
            return 5;
        }
    }

    /** Every name {@link #parse} accepts, for tab completion. */
    public static List<String> allNames() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < Keyboard.KEYBOARD_SIZE; i++) {
            String name = Keyboard.getKeyName(i);
            if (name != null)
                names.add(name);
        }
        for (int button = FIRST_BINDABLE_BUTTON; button < mouseButtonCount(); button++)
            names.add(MOUSE_PREFIX + (button + 1));
        return names;
    }
}
