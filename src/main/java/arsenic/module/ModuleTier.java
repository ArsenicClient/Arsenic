package arsenic.module;

/** Which ClickGUI toggle a module belongs to. LEGIT is always shown; BLATANT follows its toggle; DEV only exists in the dev jar. */
public enum ModuleTier {
    LEGIT("Legit"),
    BLATANT("Blatant"),
    DEV("Dev");

    private final String displayName;

    ModuleTier(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
