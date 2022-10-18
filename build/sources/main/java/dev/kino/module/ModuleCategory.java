package dev.kino.module;

public enum ModuleCategory {
    COMBAT("Combat"),
    MOVEMENT("Movement"),
    VISUAL("Visual"),
    EXPLOIT("Exploit"),
    MINIGAMES("Minigames"),
    MISC("Misc");

    private final String name;

    ModuleCategory(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

}
