package arsenic.runtime;

/**
 * What the client is running in. Forge-only calls go behind {@link #isForge()}: the JVM links a call when it first
 * runs, so a guarded call to a Forge class or a Forge-added method never fails in vanilla or Lunar Client.
 */
public final class Platform {

    private static final boolean FORGE = present("net.minecraftforge.common.MinecraftForge");

    private Platform() {}

    /** True when Forge is in the game (as a mod or injected into a Forge game). */
    public static boolean isForge() {
        return FORGE;
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
