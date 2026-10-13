package arsenic.module;

/**
 * Which release jars a module ships in (see {@link BuildTiers}), and which ClickGUI toggle it sits behind. QOL is
 * anything that is not a cheat, VISUAL is rendering that could still be an advantage, LEGIT is subtle gameplay
 * assistance, BLATANT is obvious cheating, and DEV is for development only.
 */
public enum ModuleTier {
    QOL("QoL"),
    VISUAL("Visual"),
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
