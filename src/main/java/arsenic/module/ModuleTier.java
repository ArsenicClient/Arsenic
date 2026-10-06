package arsenic.module;

/** Which ClickGUI toggle a module belongs to. LEGIT is always shown; the others follow their own toggle. */
public enum ModuleTier {
    LEGIT("Legit"),
    BLATANT("Blatant"),
    EXTRA("Extra"),
    DEV("Dev");

    private final String displayName;

    ModuleTier(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
