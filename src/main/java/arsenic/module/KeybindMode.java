package arsenic.module;

/** How a module reacts to its keybind: pressing it switches the module, or the module is on while the key is held. */
public enum KeybindMode {
    TOGGLE("Toggle"),
    HOLD("Hold");

    private final String displayName;

    KeybindMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public KeybindMode next() {
        KeybindMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static KeybindMode byName(String name) {
        for (KeybindMode mode : values())
            if (mode.name().equalsIgnoreCase(name) || mode.displayName.equalsIgnoreCase(name))
                return mode;
        return TOGGLE;
    }
}
