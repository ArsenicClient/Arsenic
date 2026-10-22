package arsenic.module.property;

/** The icons an open module shows its settings under, in the order they are drawn. See {@link PropertyGroup}. */
public enum Group {
    MAIN("Settings", "settings"),
    VISUAL("Visuals", "visual"),
    ADVANCED("Advanced", "advanced");

    private final String label;
    private final String icon;

    Group(String label, String icon) {
        this.label = label;
        this.icon = icon;
    }

    public String getLabel() {
        return label;
    }

    /** The icon's file name in assets/arsenic/icons, without .png. */
    public String getIcon() {
        return icon;
    }
}
