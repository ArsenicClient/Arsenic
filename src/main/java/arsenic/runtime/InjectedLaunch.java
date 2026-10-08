package arsenic.runtime;

import arsenic.gui.ArsenicSplash;
import arsenic.module.impl.visual.custommainmenu.CustomMenu;
import arsenic.main.Arsenic;
import arsenic.utils.render.capture.SilentView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.resources.FileResourcePack;
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
        Minecraft.getMinecraft().addScheduledTask(() -> {
            try {
                launch(jar, installHooks, status);
                status.accept("OK");
            } catch (Throwable t) {
                Arsenic arsenic = Arsenic.getInstance();
                if (arsenic != null)
                    arsenic.getLogger().error("Injection failed", t);
                else
                    t.printStackTrace();
                status.accept("ERROR " + t);
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
            if (Minecraft.getMinecraft().currentScreen instanceof GuiMainMenu)
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
        Minecraft.getMinecraft().addScheduledTask(() -> {
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

    private static void addResourcePack(File jar) throws Exception {
        Minecraft mc = Minecraft.getMinecraft();
        IResourcePack pack = new FileResourcePack(jar);
        // kept in the default packs so resource reloads (F3+T, changing packs) keep the client's assets
        List<IResourcePack> defaults = Access.field(Minecraft.class, "defaultResourcePacks").get(mc);
        defaults.add(pack);
        ((SimpleReloadableResourceManager) mc.getResourceManager()).reloadResourcePack(pack);
        // sounds.json is only read on a reload
        mc.getSoundHandler().onResourceManagerReload(mc.getResourceManager());
    }
}
