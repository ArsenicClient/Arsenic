package arsenic.inject;

import arsenic.addon.MappingTable;
import arsenic.runtime.ClientTransformer;
import arsenic.runtime.RuntimeNames;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Java agent that loads the client into a Minecraft 1.8.9 game: Forge, vanilla or Lunar Client. Two ways in:
 * {@link #agentmain} when the {@link Injector} attaches to a running game, and {@link #premain} when the jar is given
 * at launch with {@code -javaagent} (for games that turn the attach API off). Either way it finds the class loader
 * that holds Minecraft and the names Minecraft has there, puts the client jar on that loader, hands over to
 * arsenic.runtime.InjectedLaunch (which starts the client on the game thread), and then hooks the Minecraft classes
 * with a {@link HookTransformer}.
 *
 * The client jar is built against SRG names (what Forge runs). Elsewhere a {@link ClientTransformer} renames the
 * client's classes to the game's names as they load.
 *
 * This class is loaded by the system class loader, while the game and the client usually live in another loader
 * (Forge's LaunchClassLoader, Lunar's own), so everything on the game side is reached by reflection.
 */
public final class Agent {

    private static final String MINECRAFT = "net/minecraft/client/Minecraft";
    private static final String GET_MINECRAFT = "func_71410_x";

    private Agent() {}

    /** The running game as the agent finds it. */
    private static final class Game {
        final String kind;
        final ClassLoader loader;
        final RuntimeNames.Namespace namespace;
        final boolean forge;

        Game(String kind, ClassLoader loader, RuntimeNames.Namespace namespace, boolean forge) {
            this.kind = kind;
            this.loader = loader;
            this.namespace = namespace;
            this.forge = forge;
        }
    }

    /** The hooks and the way out of an injected client, kept for an uninject. */
    private static final class Active {
        final Instrumentation inst;
        final HookTransformer hooks;
        final ClassLoader loader;
        final Method stop;

        Active(Instrumentation inst, HookTransformer hooks, ClassLoader loader, Method stop) {
            this.inst = inst;
            this.hooks = hooks;
            this.loader = loader;
            this.stop = stop;
        }
    }

    private static final String UNINJECT = "uninject:";
    private static volatile Active active;

    /** Loaded into a game that is already running, by the {@link Injector} through the attach API. */
    public static void agentmain(String args, Instrumentation inst) {
        if (args != null && args.startsWith(UNINJECT)) {
            Consumer<String> status = statusWriter(args.substring(UNINJECT.length()));
            try {
                uninject(inst, status);
            } catch (Throwable t) {
                status.accept("ERROR " + describe(t));
            }
            return;
        }
        Consumer<String> status = statusWriter(args);
        try {
            inject(inst, status);
        } catch (Throwable t) {
            status.accept("ERROR " + describe(t));
        }
    }

    /**
     * Handed to the JVM at launch with {@code -javaagent:Arsenic.jar} (no injector): the client loads itself. This is
     * the same mechanism Forge mods and Weave use, and it works where the attach API is turned off. Unlike
     * {@link #agentmain}, the game has not started yet, so a daemon thread waits until Minecraft is up and then does
     * the same work. Progress goes to stdout.
     */
    public static void premain(String args, Instrumentation inst) {
        Consumer<String> status = statusWriter(args);
        Thread waiter = new Thread(() -> {
            try {
                awaitGameStarted(inst, status);
                inject(inst, status);
            } catch (Throwable t) {
                status.accept("ERROR " + describe(t));
            }
        }, "Arsenic-premain");
        waiter.setDaemon(true);
        waiter.start();
    }

    /**
     * Takes the client out of the running game: the modules are switched off, then the hooks come out and the hooked
     * classes are retransformed, which gives them back their original bytes. Runs on the game thread (see InjectedLaunch).
     */
    private static void uninject(Instrumentation inst, Consumer<String> status) throws Exception {
        Active a = active;
        if (a == null)
            throw new IllegalStateException("Arsenic is not injected into this game");
        a.stop.invoke(null, status, (Runnable) () -> {
            a.inst.removeTransformer(a.hooks);
            List<Class<?>> targets = new ArrayList<>();
            for (Class<?> c : a.inst.getAllLoadedClasses())
                if (c.getClassLoader() == a.loader && a.hooks.targets().contains(c.getName().replace('.', '/')))
                    targets.add(c);
            try {
                a.inst.retransformClasses(targets.toArray(new Class<?>[0]));
            } catch (Exception e) {
                throw new IllegalStateException("Could not restore the game classes", e);
            }
            active = null;
        });
    }

    /** The first frame in Arsenic's own code (JDK frames say little), else the first frame. */
    static String where(Throwable t) {
        StackTraceElement[] frames = t.getStackTrace();
        for (StackTraceElement f : frames)
            if (f.getClassName().startsWith("arsenic."))
                return " at " + f;
        return frames.length > 0 ? " at " + frames[0] : "";
    }

    private static void clearLoaderCaches(ClassLoader loader) throws Exception {
        for (String cache : new String[]{"invalidClasses", "negativeResourceCache"}) {
            try {
                java.lang.reflect.Field f = loader.getClass().getDeclaredField(cache);
                f.setAccessible(true);
                ((java.util.Collection<?>) f.get(loader)).clear();
            } catch (NoSuchFieldException ignored) {
            }
        }
    }

    /**
     * Initialises the class nearly every client class extends (it calls Minecraft.getMinecraft() as it starts), so a jar
     * with the wrong Minecraft names fails here with a clear message. Otherwise it fails while the client starts, and every
     * later use of the class only gives a NoClassDefFoundError without a cause.
     */
    private static void checkClientNames(Game game) {
        try {
            Class.forName("arsenic.utils.java.UtilityClass", true, game.loader);
        } catch (NoSuchMethodError | NoSuchFieldError e) {
            if (String.valueOf(e.getMessage()).startsWith("net.minecraft."))
                throw new IllegalStateException("This Arsenic jar does not use the names of the game ("
                        + game.namespace.name().toLowerCase() + "): " + e.getMessage() + ". It was built without"
                        + " reobfuscation; build it with `gradlew build` and inject the jar from build/libs", e);
            throw new IllegalStateException("Could not start the client: " + chain(e), e);
        } catch (Throwable t) {
            throw new IllegalStateException("Could not start the client: " + chain(t), t);
        }
    }

    /** Every exception in the cause chain with its top frame, for the injector's log. */
    private static String chain(Throwable t) {
        StringBuilder out = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
            if (out.length() > 0)
                out.append(" <- caused by ");
            out.append(c).append(where(c));
        }
        return out.toString();
    }

    /**
     * One line for the injector: the real cause, not a reflection wrapper. A failed reflective call reports only
     * "java.lang.reflect.InvocationTargetException", which says nothing about what failed. A class that failed to load
     * under Forge's LaunchClassLoader is a NoClassDefFoundError whose cause is the load failure, so that is followed too.
     */
    private static String describe(Throwable t) {
        if (t instanceof IllegalStateException && t.getMessage() != null)
            return t.getMessage();
        Throwable root = t;
        while ((root instanceof java.lang.reflect.InvocationTargetException || root instanceof ExceptionInInitializerError
                || root instanceof NoClassDefFoundError || root instanceof ClassNotFoundException)
                && root.getCause() != null)
            root = root.getCause();
        String where = where(root);
        return root == t ? root + where : root + where + " (from " + t.getClass().getName() + ")";
    }

    // ---- waiting for the game (premain) ----

    private static final long STARTUP_TIMEOUT_MS = 300_000;

    /** Blocks until Minecraft has started, so {@link #inject} can find it and schedule work on the game thread. */
    private static void awaitGameStarted(Instrumentation inst, Consumer<String> status) throws InterruptedException {
        status.accept("Waiting for Minecraft to start");
        long deadline = System.currentTimeMillis() + STARTUP_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            Game game = findGameOrNull(inst);
            if (game != null && minecraftInstance(game) != null)
                return;
            Thread.sleep(250);
        }
        throw new IllegalStateException("Minecraft did not start within " + (STARTUP_TIMEOUT_MS / 1000) + "s");
    }

    private static Game findGameOrNull(Instrumentation inst) {
        try {
            return findGame(inst);
        } catch (Exception e) {
            return null;
        }
    }

    /** The running Minecraft singleton, or null when it has not been created yet. */
    private static Object minecraftInstance(Game game) {
        try {
            RuntimeNames names = RuntimeNames.create(game.namespace, game.loader);
            Class<?> mc = Class.forName(names.mapClass(MINECRAFT).replace('/', '.'), false, game.loader);
            String getter = names.mapMethod(MINECRAFT, GET_MINECRAFT, "()L" + MINECRAFT + ";");
            Method m;
            try {
                m = mc.getDeclaredMethod(getter);
            } catch (NoSuchMethodException e) {
                m = mc.getDeclaredMethod("getMinecraft");
            }
            m.setAccessible(true);
            return m.invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void inject(Instrumentation inst, Consumer<String> status) throws Exception {
        if (!inst.isRetransformClassesSupported())
            throw new IllegalStateException("This Java does not allow changing loaded classes");

        for (Class<?> c : inst.getAllLoadedClasses()) {
            if (c.getName().equals("arsenic.main.Arsenic"))
                throw new IllegalStateException("uninjected".equals(System.getProperty("arsenic.loaded"))
                        ? "Arsenic was removed from this game; restart the game to inject it again"
                        : "Arsenic is already loaded in this game");
        }

        Game game = findGame(inst);
        status.accept("LOG Found " + game.kind + " (" + game.namespace.name().toLowerCase() + " names, loader "
                + (game.loader == ClassLoader.getSystemClassLoader() ? "system" : game.loader.getClass().getName()) + ")");

        status.accept("Loading client");
        URL jarUrl = Agent.class.getProtectionDomain().getCodeSource().getLocation();
        File jar = new File(jarUrl.toURI());
        RuntimeNames runtimeNames = RuntimeNames.create(game.namespace, game.loader);
        addToLoader(inst, game.loader, jarUrl);
        // the client's classes are prepared as they load, so this must be in place before the first one does
        // Forge's LaunchClassLoader takes the accessor transformer; a launcher's own loader (Lunar's Forge) does not, so
        // the client's classes are rewritten by the ClientTransformer as they load instead
        Method register = game.forge ? findMethod(game.loader, "registerTransformer", String.class) : null;
        if (register != null)
            register.invoke(game.loader, "arsenic.runtime.AccessorTransformer");
        else
            inst.addTransformer(new ClientTransformer(game.loader, runtimeNames, line -> status.accept("LOG " + line)));
        // launchwrapper remembers classes it failed to find (netty probes for slf4j at startup, which the jar bundles)
        clearLoaderCaches(game.loader);
        checkClientNames(game);

        Class<?> launch = Class.forName("arsenic.runtime.InjectedLaunch", false, game.loader);
        if (launch.getClassLoader() != game.loader)
            throw new IllegalStateException("The game's class loader (" + game.loader.getClass().getName()
                    + ") does not load the client itself; this launcher is not supported yet");

        HookTransformer hooks = new HookTransformer(names(game, runtimeNames), game.forge, game.loader, line -> status.accept("LOG " + line));
        List<String> problems = hooks.verifyHooks(game.loader);
        if (!problems.isEmpty())
            throw new IllegalStateException("Client hooks do not match: " + problems);
        Method stop = launch.getMethod("stop", Consumer.class, Runnable.class);

        Runnable installHooks = () -> {
            inst.addTransformer(hooks, true);
            List<Class<?>> targets = new ArrayList<>();
            for (Class<?> c : inst.getAllLoadedClasses())
                if (c.getClassLoader() == game.loader && hooks.targets().contains(c.getName().replace('.', '/')))
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
            active = new Active(inst, hooks, game.loader, stop);
        };

        launch.getMethod("start", File.class, Runnable.class, Consumer.class, String.class, Function.class)
                .invoke(null, jar, installHooks, status, game.namespace.name(), classBytes(inst, game.loader));
    }

    /**
     * Class files of Minecraft classes as the game defined them, for compiling addons where the loader has no class
     * file to read (Lunar Client renames Minecraft as it loads it). Retransforming a class hands its bytes to every
     * transformer, so a short-lived one keeps a copy.
     */
    private static Function<String, byte[]> classBytes(Instrumentation inst, ClassLoader loader) {
        return internalName -> {
            Class<?> c;
            try {
                c = Class.forName(internalName.replace('/', '.'), false, loader);
            } catch (Throwable t) {
                return null;
            }
            byte[][] captured = new byte[1][];
            ClassFileTransformer capture = new ClassFileTransformer() {
                @Override
                public byte[] transform(ClassLoader l, String name, Class<?> redefined, java.security.ProtectionDomain domain, byte[] bytes) {
                    if (redefined == c)
                        captured[0] = bytes.clone();
                    return null;
                }
            };
            synchronized (Agent.class) {
                inst.addTransformer(capture, true);
                try {
                    inst.retransformClasses(c);
                } catch (Throwable t) {
                    return null;
                } finally {
                    inst.removeTransformer(capture);
                }
            }
            return captured[0];
        };
    }

    // ---- finding the game ----

    /**
     * Finds the loaded Minecraft class. Forge and Lunar have it under its real name (with SRG or MCP members); vanilla
     * has it under its obfuscated name, found through the class the game started from.
     */
    private static Game findGame(Instrumentation inst) throws Exception {
        Class<?> minecraft = null, vanillaMain = null;
        boolean lunar = false;
        for (Class<?> c : inst.getAllLoadedClasses()) {
            String name = c.getName();
            if (name.equals("net.minecraft.client.Minecraft") && c.getClassLoader() != null)
                minecraft = c;
            else if (name.equals("net.minecraft.client.main.Main") && c.getClassLoader() != null)
                vanillaMain = c;
            else if (name.startsWith("com.moonsworth.lunar."))
                lunar = true;
        }

        if (minecraft != null) {
            ClassLoader loader = minecraft.getClassLoader();
            boolean srg = declaresMethod(minecraft, GET_MINECRAFT);
            boolean forge = srg && classExists("net.minecraftforge.fml.common.Loader", loader);
            if (forge)
                return new Game("Forge", loader, isDeobfuscatedForge() ? RuntimeNames.Namespace.MCP : RuntimeNames.Namespace.SRG, true);
            if (srg)
                throw new IllegalStateException("Minecraft has SRG names but Forge is missing; this launcher is not supported");
            if (!declaresMethod(minecraft, "getMinecraft"))
                throw new IllegalStateException("Minecraft is loaded under names this injector does not know");
            if (classExists("net.minecraftforge.fml.common.Loader", loader))
                return new Game("Forge (development)", loader, RuntimeNames.Namespace.MCP, true);
            return new Game(lunar ? "Lunar Client" : "Minecraft (MCP names)", loader, RuntimeNames.Namespace.MCP, false);
        }

        if (vanillaMain != null || classExists("net.minecraft.launchwrapper.Launch", ClassLoader.getSystemClassLoader())) {
            RuntimeNames names = RuntimeNames.create(RuntimeNames.Namespace.NOTCH, null);
            String obfuscated = names.mapClass(MINECRAFT).replace('/', '.');
            for (Class<?> c : inst.getAllLoadedClasses()) {
                if (!c.getName().equals(obfuscated) || c.getClassLoader() == null)
                    continue;
                String getMinecraft = names.mapMethod(MINECRAFT, GET_MINECRAFT, "()L" + MINECRAFT + ";");
                Method m;
                try {
                    m = c.getDeclaredMethod(getMinecraft);
                } catch (NoSuchMethodException e) {
                    continue;
                }
                if (m.getReturnType() != c || !java.lang.reflect.Modifier.isStatic(m.getModifiers()))
                    continue;
                boolean launchwrapper = c.getClassLoader().getClass().getName().equals("net.minecraft.launchwrapper.LaunchClassLoader");
                return new Game(launchwrapper ? "Vanilla (launchwrapper)" : "Vanilla", c.getClassLoader(), RuntimeNames.Namespace.NOTCH, false);
            }
            throw new IllegalStateException("This is not Minecraft 1.8.9, or it has not finished starting");
        }
        throw new IllegalStateException("Minecraft has not started yet, or this is not Minecraft");
    }

    /** The public method with these parameters on the loader's class, or null when the loader does not have it. */
    private static Method findMethod(Object target, String name, Class<?>... params) {
        try {
            return target.getClass().getMethod(name, params);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static boolean declaresMethod(Class<?> c, String name) {
        try {
            for (Method m : c.getDeclaredMethods())
                if (m.getName().equals(name))
                    return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean classExists(String name, ClassLoader loader) {
        try {
            Class.forName(name, false, loader);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isDeobfuscatedForge() {
        try {
            Class<?> launch = Class.forName("net.minecraft.launchwrapper.Launch", false, ClassLoader.getSystemClassLoader());
            Map<?, ?> blackboard = (Map<?, ?>) launch.getField("blackboard").get(null);
            return Boolean.TRUE.equals(blackboard.get("fml.deobfuscatedEnvironment"));
        } catch (Exception e) {
            return false;
        }
    }

    // ---- getting the client onto the game's class loader ----

    /**
     * Adds the client jar to the class path of the game's class loader. The JVM already put the agent jar (the client
     * jar) on the system class path, which is enough when the game runs there (vanilla).
     */
    private static void addToLoader(Instrumentation inst, ClassLoader loader, URL jar) throws Exception {
        if (loader == ClassLoader.getSystemClassLoader())
            return;
        // Forge's LaunchClassLoader and some launchers' loaders make addURL public
        try {
            Method addURL = loader.getClass().getMethod("addURL", URL.class);
            addURL.invoke(loader, jar);
            return;
        } catch (NoSuchMethodException ignored) {
        }
        if (!(loader instanceof URLClassLoader))
            throw new IllegalStateException("Cannot add the client to the game's class loader (" + loader.getClass().getName() + ")");
        Method addURL = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
        try {
            addURL.setAccessible(true);
        } catch (RuntimeException closed) {
            // Java 9+: java.net is closed to the agent until the agent opens it (InaccessibleObjectException)
            openPackage(inst, URLClassLoader.class, "java.net");
            addURL.setAccessible(true);
        }
        addURL.invoke(loader, jar);
    }

    /** Opens a JDK package to this agent with Instrumentation.redefineModule (Java 9+, called by reflection). */
    private static void openPackage(Instrumentation inst, Class<?> inPackage, String pkg) throws Exception {
        Method getModule = Class.class.getMethod("getModule");
        Object target = getModule.invoke(inPackage);
        Object self = getModule.invoke(Agent.class);
        Class<?> moduleClass = Class.forName("java.lang.Module");
        Method redefine = Instrumentation.class.getMethod("redefineModule", moduleClass, java.util.Set.class, Map.class, Map.class,
                java.util.Set.class, Map.class);
        redefine.invoke(inst, target, Collections.emptySet(), Collections.emptyMap(),
                Collections.singletonMap(pkg, Collections.singleton(self)), Collections.emptySet(), Collections.emptyMap());
    }

    // ---- names ----

    /**
     * MCP names (what the hooks are written in) to the game's names: MCP to SRG with the bundled addon table, then SRG
     * to the game's names with {@link RuntimeNames}. Development games keep MCP names throughout.
     */
    private static HookTransformer.Names names(Game game, RuntimeNames runtime) throws Exception {
        if (game.namespace == RuntimeNames.Namespace.MCP && game.forge) {
            return new HookTransformer.Names() {
                @Override
                public String method(String owner, String name, String desc) {
                    return name;
                }

                @Override
                public String field(String owner, String name, String desc) {
                    return name;
                }

                @Override
                public String type(String internalName) {
                    return internalName;
                }

                @Override
                public String desc(String desc) {
                    return desc;
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
                return runtime.mapMethod(owner, srg != null ? srg : name, desc);
            }

            @Override
            public String field(String owner, String name, String desc) {
                String srg = table.fieldToSrg(owner, name);
                return runtime.mapField(owner, srg != null ? srg : name);
            }

            @Override
            public String type(String internalName) {
                return runtime.mapClass(internalName);
            }

            @Override
            public String desc(String desc) {
                return runtime.mapDesc(desc);
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
