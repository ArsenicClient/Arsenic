package arsenic.inject;

import arsenic.addon.MappingTable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Java agent the {@link Injector} loads into a running Forge 1.8.9 game. It puts the client jar on the game's class
 * loader, hands over to arsenic.runtime.InjectedLaunch (which starts the client on the game thread), and then
 * hooks the Minecraft classes with a {@link HookTransformer}.
 *
 * This class is loaded by the system class loader, while the game and the client live in Forge's LaunchClassLoader,
 * so everything on the game side is reached by reflection.
 */
public final class Agent {

    private Agent() {}

    public static void agentmain(String args, Instrumentation inst) {
        Consumer<String> status = statusWriter(args);
        try {
            inject(inst, status);
        } catch (Throwable t) {
            status.accept("ERROR " + t);
        }
    }

    private static void inject(Instrumentation inst, Consumer<String> status) throws Exception {
        if (!inst.isRetransformClassesSupported())
            throw new IllegalStateException("This Java does not allow changing loaded classes");

        ClassLoader launchLoader;
        try {
            Class<?> launch = Class.forName("net.minecraft.launchwrapper.Launch", false, ClassLoader.getSystemClassLoader());
            launchLoader = (ClassLoader) launch.getField("classLoader").get(null);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Not a Forge game (no launchwrapper)");
        }
        if (launchLoader == null)
            throw new IllegalStateException("Minecraft has not started yet");
        try {
            Class.forName("net.minecraftforge.fml.common.Loader", false, launchLoader);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Not a Forge 1.8.9 game");
        }
        for (Class<?> c : inst.getAllLoadedClasses()) {
            if (c.getName().equals("arsenic.main.Arsenic"))
                throw new IllegalStateException("Arsenic is already loaded in this game");
        }

        status.accept("Loading client");
        URL jarUrl = Agent.class.getProtectionDomain().getCodeSource().getLocation();
        File jar = new File(jarUrl.toURI());
        Method addURL = launchLoader.getClass().getMethod("addURL", URL.class);
        addURL.invoke(launchLoader, jarUrl);
        // the loader remembers classes it failed to find (netty probes for slf4j at startup, which the jar bundles)
        for (String cache : new String[]{"invalidClasses", "negativeResourceCache"}) {
            try {
                java.lang.reflect.Field f = launchLoader.getClass().getDeclaredField(cache);
                f.setAccessible(true);
                ((java.util.Collection<?>) f.get(launchLoader)).clear();
            } catch (NoSuchFieldException ignored) {
            }
        }
        // must be in place before the first client class loads
        launchLoader.getClass().getMethod("registerTransformer", String.class)
                .invoke(launchLoader, "arsenic.runtime.AccessorRewriter");

        HookTransformer hooks = new HookTransformer(names(), line -> status.accept("LOG " + line));
        List<String> problems = hooks.verifyHooks(launchLoader);
        if (!problems.isEmpty())
            throw new IllegalStateException("Client hooks do not match: " + problems);

        Runnable installHooks = () -> {
            inst.addTransformer(hooks, true);
            List<Class<?>> targets = new ArrayList<>();
            for (Class<?> c : inst.getAllLoadedClasses())
                if (c.getClassLoader() == launchLoader && hooks.targets().contains(c.getName().replace('.', '/')))
                    targets.add(c);
            try {
                inst.retransformClasses(targets.toArray(new Class<?>[0]));
            } catch (Exception e) {
                throw new IllegalStateException("Could not hook the game classes", e);
            }
            // classes that load later (a container screen, the option slider, ...) go through the transformer then
            for (String hook : hooks.applied())
                status.accept("LOG hooked " + hook);
            for (String hook : hooks.missed())
                status.accept("LOG missed " + hook);
        };

        Class<?> launch = Class.forName("arsenic.runtime.InjectedLaunch", true, launchLoader);
        launch.getMethod("start", File.class, Runnable.class, Consumer.class).invoke(null, jar, installHooks, status);
    }

    /** MCP names in a development game, SRG names (from the bundled table) in a normal game. */
    private static HookTransformer.Names names() throws Exception {
        boolean deobf = false;
        try {
            Class<?> launch = Class.forName("net.minecraft.launchwrapper.Launch", false, ClassLoader.getSystemClassLoader());
            Map<?, ?> blackboard = (Map<?, ?>) launch.getField("blackboard").get(null);
            deobf = Boolean.TRUE.equals(blackboard.get("fml.deobfuscatedEnvironment"));
        } catch (Exception ignored) {
        }
        if (deobf) {
            return new HookTransformer.Names() {
                @Override
                public String method(String owner, String name, String desc) {
                    return name;
                }

                @Override
                public String field(String owner, String name, String desc) {
                    return name;
                }
            };
        }
        MappingTable table;
        try (InputStream in = Agent.class.getResourceAsStream("/addon-mappings.txt")) {
            if (in == null)
                throw new IllegalStateException("addon-mappings.txt is missing from this build");
            table = MappingTable.load(in);
        }
        return new HookTransformer.Names() {
            @Override
            public String method(String owner, String name, String desc) {
                String srg = table.methodToSrg(owner, name, desc);
                return srg != null ? srg : name;
            }

            @Override
            public String field(String owner, String name, String desc) {
                String srg = table.fieldToSrg(owner, name);
                return srg != null ? srg : name;
            }
        };
    }

    /** Progress lines go to the file the injector named, one per line, flushed as they come. */
    private static Consumer<String> statusWriter(String path) {
        if (path == null || path.isEmpty())
            return line -> System.out.println("[Arsenic] " + line);
        return line -> {
            synchronized (Agent.class) {
                try (Writer w = new OutputStreamWriter(new FileOutputStream(path, true), StandardCharsets.UTF_8)) {
                    w.write(line.replace('\n', ' ') + "\n");
                } catch (Exception e) {
                    System.out.println("[Arsenic] " + line);
                }
            }
        };
    }
}
