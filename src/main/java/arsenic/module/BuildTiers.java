package arsenic.module;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.Properties;

/**
 * What this jar was built with, from buildtiers.properties (written by build.gradle into each release jar): the
 * module tiers it loads, and the name of its Arsenic folder, so builds keep their configs apart. A module whose tier
 * is not listed never loads, addons included. Without the file (a run straight from the IDE) every tier loads and the
 * folder is "Arsenic".
 */
public final class BuildTiers {

    private static final Properties PROPERTIES = read();
    private static final EnumSet<ModuleTier> TIERS = tiers();

    private BuildTiers() {
    }

    public static boolean allows(ModuleTier tier) {
        return TIERS.contains(tier);
    }

    /** The name of this build's Arsenic folder, inside the user's home folder. */
    public static String folderName() {
        return PROPERTIES.getProperty("folder", "Arsenic").trim();
    }

    private static Properties read() {
        Properties properties = new Properties();
        try (InputStream in = BuildTiers.class.getResourceAsStream("/buildtiers.properties")) {
            if (in != null)
                properties.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read buildtiers.properties", e);
        }
        return properties;
    }

    private static EnumSet<ModuleTier> tiers() {
        if (!PROPERTIES.containsKey("tiers"))
            return EnumSet.allOf(ModuleTier.class);
        EnumSet<ModuleTier> tiers = EnumSet.noneOf(ModuleTier.class);
        for (String name : PROPERTIES.getProperty("tiers").split(",")) {
            if (!name.trim().isEmpty())
                tiers.add(ModuleTier.valueOf(name.trim()));
        }
        return tiers;
    }
}
