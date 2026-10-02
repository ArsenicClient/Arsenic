package arsenic.main;

public class MinecraftAPI {

    // kv why...
    public static float cachedYawM;
    public static float cachedYawL, cachedPrevYawL, cachedPitchL, cachedPrevPitchL;

    public static final boolean[] mouseDownLastTick = new boolean[] {false, false, false};

    /** True while the local player is raycasting for the crosshair target - see MixinEntity. */
    public static boolean picking;

}
