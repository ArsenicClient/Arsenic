package arsenic.runtime;

import arsenic.gui.ArsenicSplash;
import arsenic.module.impl.visual.custommainmenu.CustomMenu;
import arsenic.main.Arsenic;
import arsenic.utils.render.capture.SilentView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.resources.FileResourcePack;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.SimpleReloadableResourceManager;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Starts the client inside a game that is already running (Forge, vanilla or Lunar Client), for the injector
 * (arsenic.inject.Agent). This is the work Forge does for the mod at startup: the client's assets become a resource
 * pack, the client is created and initialised, and then the agent hooks the Minecraft classes. All of it runs on the
 * game thread, between frames.
 */
public final class InjectedLaunch {

    private static final String[] HOOK_CLASSES = {
            "arsenic.runtime.hooks.MinecraftHooks", "arsenic.runtime.hooks.PlayerHooks",
            "arsenic.runtime.hooks.EntityHooks", "arsenic.runtime.hooks.RenderHooks",
            "arsenic.runtime.hooks.GuiHooks", "arsenic.runtime.hooks.MiscHooks"
    };

    private static boolean injected;
    private static Function<String, byte[]> classBytes;

    private InjectedLaunch() {}

    /**
     * The class file of a Minecraft class as the game defined it, when the class loader has none to read (Lunar
     * Client). Null when not injected or the class does not exist.
     */
    public static byte[] classBytes(String internalName) {
        return classBytes != null && internalName.startsWith("net/minecraft/") ? classBytes.apply(internalName) : null;
    }

    /** True when the client was injected rather than loaded by Forge. */
    public static boolean isInjected() {
        return injected;
    }

    /**
     * Called by the agent on its own thread.
     *
     * @param jar          the client jar
     * @param installHooks hooks the Minecraft classes; throws if that fails
     * @param status       progress lines for the injector; "OK" or "ERROR ..." ends the injection
     * @param namespace    the names Minecraft has in this game, a {@link RuntimeNames.Namespace}
     * @param classBytes   class files of loaded classes by internal name (null when not loaded), from the agent
     */
    public static void start(File jar, Runnable installHooks, Consumer<String> status, String namespace, Function<String, byte[]> classBytes) {
        InjectedLaunch.classBytes = classBytes;
        // before anything looks a Minecraft member up by name
        RuntimeNames.setCurrent(RuntimeNames.create(RuntimeNames.Namespace.valueOf(namespace), InjectedLaunch.class.getClassLoader()));
        schedule(() -> {
            try {
                launch(jar, installHooks, status);
                status.accept("OK");
            } catch (Throwable t) {
                Arsenic arsenic = Arsenic.getInstance();
                if (arsenic != null)
                    arsenic.getLogger().error("Injection failed", t);
                else
                    t.printStackTrace();
                Throwable root = t;
                while ((root instanceof java.lang.reflect.InvocationTargetException || root instanceof ExceptionInInitializerError
                        || root instanceof NoClassDefFoundError || root instanceof ClassNotFoundException)
                        && root.getCause() != null)
                    root = root.getCause();
                logTrace(t, "", status);
                status.accept("ERROR " + root + where(root));
            }
        });
    }

    private static void launch(File jar, Runnable installHooks, Consumer<String> status) throws Exception {
        injected = true;
        System.setProperty("arsenic.loaded", "injected");
        // the loading screen covers the stages below; it runs on its own thread and is finished before the game continues
        ArsenicSplash.start();
        try {
            // the hooks resolve their private Minecraft members when first loaded; fail here rather than mid-frame
            for (String hookClass : HOOK_CLASSES)
                Class.forName(hookClass, true, InjectedLaunch.class.getClassLoader());

            stage(0.15f, "Adding resources", status);
            addResourcePack(jar);

            stage(0.45f, "Starting client", status);
            Arsenic arsenic = new Arsenic();
            Field instance = Arsenic.class.getDeclaredField("instance");
            instance.setAccessible(true);
            instance.set(null, arsenic);
            if (Platform.isForge()) {
                ForgeLaunch.asMinecraft(arsenic::initialize);
            } else {
                arsenic.initialize();
                SilentView.register();
            }

            stage(0.8f, "Hooking game", status);
            installHooks.run();
            // the title screen is already up: the hooks only swap screens that are shown after they are in place
            if (currentScreen() instanceof GuiMainMenu)
                CustomMenu.display();
            ArsenicSplash.progress(1f, "Ready");
        } finally {
            ArsenicSplash.finish();
        }
    }

    /**
     * Takes the client out of the game (uninject), on the game thread: its modules are switched off, the classes it hooked
     * get their original bytes back through {@code restoreClasses}, and the splash-free hooks stop running. The client's
     * classes stay loaded (the JVM cannot unload them), so the game has to be restarted before Arsenic can be injected again.
     */
    public static void stop(Consumer<String> status, Runnable restoreClasses) {
        schedule(() -> {
            try {
                status.accept("Switching off modules");
                for (arsenic.module.Module module : Arsenic.getArsenic().getModuleManager().getModules()) {
                    if (!module.isEnabled())
                        continue;
                    try {
                        module.setEnabled(false);
                    } catch (Throwable t) {
                        status.accept("LOG could not switch off " + module.getName() + ": " + t);
                    }
                }
                SilentView.unregister();
                status.accept("Removing hooks");
                restoreClasses.run();
                injected = false;
                System.setProperty("arsenic.loaded", "uninjected");
                status.accept("OK");
            } catch (Throwable t) {
                status.accept("ERROR " + t);
            }
        });
    }

    /** One stage of the launch: the status line for the injector and the loading screen. */
    private static void stage(float fraction, String text, Consumer<String> status) {
        status.accept(text);
        ArsenicSplash.progress(fraction, text);
    }

    /**
     * The Minecraft members this class uses, looked up by name for the game it runs in. They are compiled with the
     * development names, which the game does not have under Forge (SRG) or Lunar, so a direct call fails with a
     * NoSuchMethodError. Resolved on first use, which is after {@link RuntimeNames#setCurrent} in start.
     */
    private static final class Names {
        static final Access.MethodRef GET_MINECRAFT = Access.method(Minecraft.class, "getMinecraft");
        static final Access.MethodRef ADD_SCHEDULED_TASK = Access.method(Minecraft.class, "addScheduledTask", Runnable.class);
        static final Access.MethodRef GET_RESOURCE_MANAGER = Access.method(Minecraft.class, "getResourceManager");
        static final Access.MethodRef GET_SOUND_HANDLER = Access.method(Minecraft.class, "getSoundHandler");
        static final Access.FieldRef CURRENT_SCREEN = Access.field(Minecraft.class, "currentScreen");
        static final Access.MethodRef RELOAD_RESOURCE_PACK =
                Access.method(SimpleReloadableResourceManager.class, "reloadResourcePack", IResourcePack.class);
        static final Access.MethodRef ON_RESOURCE_MANAGER_RELOAD =
                Access.method(SoundHandler.class, "onResourceManagerReload", IResourceManager.class);
    }

    private static Minecraft minecraft() {
        return Names.GET_MINECRAFT.invoke(null);
    }

    private static void schedule(Runnable task) {
        Names.ADD_SCHEDULED_TASK.invoke(minecraft(), task);
    }

    private static Object currentScreen() {
        return Names.CURRENT_SCREEN.get(minecraft());
    }

    private static void addResourcePack(File jar) throws Exception {
        Minecraft mc = minecraft();
        IResourcePack pack = new FileResourcePack(jar);
        // kept in the default packs so resource reloads (F3+T, changing packs) keep the client's assets
        List<IResourcePack> defaults = Access.field(Minecraft.class, "defaultResourcePacks").get(mc);
        defaults.add(pack);
        Object resources = Names.GET_RESOURCE_MANAGER.invoke(mc);
        Names.RELOAD_RESOURCE_PACK.invoke(resources, pack);
        // sounds.json is only read on a reload
        Names.ON_RESOURCE_MANAGER_RELOAD.invoke(Names.GET_SOUND_HANDLER.invoke(mc), resources);
    }

    /**
     * The whole failure for the injector's log, with its causes and suppressed exceptions. The game's own log may be
     * out of reach, and the one-line error cannot say which earlier failure left a class unusable.
     */
    private static void logTrace(Throwable t, String prefix, Consumer<String> status) {
        status.accept("LOG " + prefix + t);
        StackTraceElement[] frames = t.getStackTrace();
        int shown = Math.min(frames.length, 25);
        for (int i = 0; i < shown; i++)
            status.accept("LOG     at " + frames[i]);
        if (frames.length > shown)
            status.accept("LOG     ... " + (frames.length - shown) + " more");
        for (Throwable s : t.getSuppressed())
            logTrace(s, "Suppressed: ", status);
        if (t.getCause() != null && t.getCause() != t)
            logTrace(t.getCause(), "Caused by: ", status);
    }

    /** The first frame in Arsenic's own code (JDK frames say little), else the first frame. */
    static String where(Throwable t) {
        StackTraceElement[] frames = t.getStackTrace();
        for (StackTraceElement f : frames)
            if (f.getClassName().startsWith("arsenic."))
                return " at " + f;
        return frames.length > 0 ? " at " + frames[0] : "";
    }
}
