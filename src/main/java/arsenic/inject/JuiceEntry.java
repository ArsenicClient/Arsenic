package arsenic.inject;

import arsenic.runtime.RuntimeNames;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Entry point for JuiceAgent (https://github.com/xiaozhou233/JuiceAgent). Its JarLoader module puts the client jar on
 * the system class path and calls {@link #run()} in a running Minecraft 1.8.9 Forge game. It does what {@link Agent}
 * does, but JuiceAgent has no {@link java.lang.instrument.Instrumentation}: the JuiceAgent API is used instead.
 *
 * Limits compared with the injector:
 * <ul>
 *   <li>Forge only. Vanilla and Lunar need a class transformer on the system class loader, which JuiceAgent does not
 *       expose to Java.</li>
 *   <li>Hooks for classes that load later go through Forge's class loader ({@link arsenic.runtime.HookBridge}); the
 *       classes already loaded are retransformed once, from the bytes JuiceAgent captures for them.</li>
 * </ul>
 */
public final class JuiceEntry {

    private static final String API = "cn.xiaozhou233.juiceagent.api.JuiceAgent";
    private static final String MINECRAFT = "net/minecraft/client/Minecraft";

    private JuiceEntry() {}

    /** Called by JuiceAgent after the client jar is on the system class path. */
    public static void run() throws Exception {
        Consumer<String> status = line -> System.out.println("[Arsenic] " + line);

        Class<?>[] loaded = loadedClasses();
        for (Class<?> c : loaded) {
            if (c.getName().equals("arsenic.main.Arsenic"))
                throw new IllegalStateException("Arsenic is already loaded in this game");
        }

        Class<?> minecraft = null;
        for (Class<?> c : loaded) {
            if (c.getName().equals("net.minecraft.client.Minecraft") && c.getClassLoader() != null) {
                minecraft = c;
                break;
            }
        }
        if (minecraft == null)
            throw new IllegalStateException("Minecraft is not loaded in this JVM");
        ClassLoader loader = minecraft.getClassLoader();
        if (!Agent.classExists("net.minecraftforge.fml.common.Loader", loader))
            throw new IllegalStateException("JuiceAgent mode supports Forge 1.8.9 only; use the injector for vanilla or Lunar Client");

        RuntimeNames.Namespace namespace = Agent.isDeobfuscatedForge() ? RuntimeNames.Namespace.MCP : RuntimeNames.Namespace.SRG;
        RuntimeNames runtimeNames = RuntimeNames.create(namespace, loader);
        status.accept("Found Forge (" + namespace.name().toLowerCase() + " names)");

        URL jarUrl = JuiceEntry.class.getProtectionDomain().getCodeSource().getLocation();
        File jar = new File(jarUrl.toURI());
        status.accept("Loading client");
        loader.getClass().getMethod("addURL", URL.class).invoke(loader, jarUrl);
        // the client's classes are prepared as they load, so this must be in place before the first one does
        loader.getClass().getMethod("registerTransformer", String.class).invoke(loader, "arsenic.runtime.AccessorTransformer");
        Agent.clearLoaderCaches(loader);

        Class<?> launch = Class.forName("arsenic.runtime.InjectedLaunch", false, loader);
        if (launch.getClassLoader() != loader)
            throw new IllegalStateException("The game's class loader (" + loader.getClass().getName()
                    + ") does not load the client itself; this launcher is not supported yet");

        HookTransformer hooks = new HookTransformer(Agent.names(namespace, true, runtimeNames), true, loader, line -> status.accept("LOG " + line));
        List<String> problems = hooks.verifyHooks(loader);
        if (!problems.isEmpty())
            throw new IllegalStateException("Client hooks do not match: " + problems);

        Runnable installHooks = () -> installHooks(hooks, loader, status);
        launch.getMethod("start", File.class, Runnable.class, Consumer.class, String.class, Function.class)
                .invoke(null, jar, installHooks, status, namespace.name(), classBytes(loader));
    }

    /**
     * Forge's loader hooks the classes it loads from now on. The classes already loaded are retransformed with their
     * bytes as JuiceAgent has them. The snapshot is taken before the bridge exists, so no class is hooked twice.
     */
    private static void installHooks(HookTransformer hooks, ClassLoader loader, Consumer<String> status) {
        Class<?>[] loaded;
        try {
            loaded = loadedClasses();
            BiFunction<String, byte[], byte[]> hook = (name, bytes) -> {
                try {
                    return hooks.transform(name, bytes);
                } catch (Throwable t) {
                    status.accept("LOG Could not hook " + name + ": " + t);
                    return bytes;
                }
            };
            Class<?> bridge = Class.forName("arsenic.runtime.HookBridge", true, loader);
            bridge.getField("hook").set(null, hook);
            loader.getClass().getMethod("registerTransformer", String.class).invoke(loader, "arsenic.runtime.HookBridge");
        } catch (Exception e) {
            throw new IllegalStateException("Could not register the hooks with the game's class loader", e);
        }

        try {
            for (Class<?> c : loaded) {
                if (c.getClassLoader() != loader)
                    continue;
                String name = c.getName().replace('.', '/');
                if (!hooks.targets().contains(name))
                    continue;
                byte[] original = classBytes(c);
                if (original == null) {
                    status.accept("LOG missed " + name + " (no class bytes)");
                    continue;
                }
                byte[] hooked = hooks.transform(name, original);
                if (Arrays.equals(hooked, original))
                    continue;
                if (!(Boolean) juice("retransformClass", new Class<?>[]{Class.class, byte[].class, int.class}, c, hooked, hooked.length))
                    status.accept("LOG missed " + name + " (retransform refused)");
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not hook the game classes", e);
        }

        for (String hook : hooks.applied())
            status.accept("LOG hooked " + hook);
        for (String hook : hooks.missed())
            status.accept("LOG missed " + hook);
    }

    /** Class files of Minecraft classes by internal name, for compiling addons. */
    private static java.util.function.Function<String, byte[]> classBytes(ClassLoader loader) {
        return internalName -> {
            try {
                return classBytes(Class.forName(internalName.replace('/', '.'), false, loader));
            } catch (Throwable t) {
                return null;
            }
        };
    }

    private static byte[] classBytes(Class<?> c) throws Exception {
        return (byte[]) juice("getClassBytes", new Class<?>[]{Class.class}, c);
    }

    private static Class<?>[] loadedClasses() throws Exception {
        return (Class<?>[]) juice("getLoadedClasses", new Class<?>[0]);
    }

    /** Calls a static method of the JuiceAgent API. It is loaded by the system class loader with the agent. */
    private static Object juice(String method, Class<?>[] types, Object... args) throws Exception {
        Class<?> api = Class.forName(API, true, ClassLoader.getSystemClassLoader());
        Method m = api.getMethod(method, types);
        return m.invoke(null, args);
    }
}
