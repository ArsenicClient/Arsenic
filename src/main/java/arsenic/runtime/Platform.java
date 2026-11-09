package arsenic.runtime;

/**
 * What the client is running in. Forge-only calls go behind {@link #isForge()}: the JVM links a call when it first
 * runs, so a guarded call to a Forge class or a Forge-added method never fails in vanilla or Lunar Client.
 */
public final class Platform {

    // Fabric mods such as forgecompat ship stand-ins for some Forge classes: under Fabric (and Ornithe) it is never Forge
    private static final boolean FORGE = present("net.minecraftforge.common.MinecraftForge")
            && !present("net.fabricmc.loader.api.FabricLoader")
            && !present("net.fabricmc.loader.impl.launch.knot.Knot");

    private Platform() {}

    /** True when the game the client was injected into runs Forge. */
    public static boolean isForge() {
        return FORGE;
    }

    private static Boolean ownMenus;

    /**
     * True in OneClient (Polyfrost) and Lunar Client, which draw their own menus: the client's loading screen, custom
     * menus and screen transitions stay out of their way. Found from the launch.
     */
    public static boolean hasOwnMenus() {
        if (ownMenus == null) {
            String launch = System.getProperty("java.class.path", "") + ' ' + System.getProperty("sun.java.command", "")
                    + ' ' + System.getProperty("user.dir", "") + ' ' + System.getProperty("java.library.path", "");
            try {
                launch += ' ' + String.join(" ", java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments());
            } catch (Throwable ignored) {
            }
            launch = launch.toLowerCase(java.util.Locale.ROOT);
            ownMenus = launch.contains("oneclient") || launch.contains("polyfrost")
                    || launch.contains("lunarclient") || launch.contains("com.moonsworth.lunar");
        }
        return ownMenus;
    }

    private static boolean present(String name) {
        try {
            Class.forName(name, false, Platform.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
