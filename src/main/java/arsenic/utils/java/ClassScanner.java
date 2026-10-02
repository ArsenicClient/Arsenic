package arsenic.utils.java;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Finds the client's own classes by package, for discovering modules and commands.
 * <p>
 * The 1.8 client used the Reflections library, which scans the JVM classpath. Under Fabric the
 * mod is loaded from its own root paths (a jar in production, class folders in dev), so this
 * walks those directly - it works the same in both and needs no extra dependency.
 */
public final class ClassScanner {

    private ClassScanner() {
    }

    /** Every concrete subclass of {@code type} in {@code pkg} or below. */
    @SuppressWarnings("unchecked")
    public static <T> List<Class<? extends T>> findSubTypes(String pkg, Class<T> type) {
        List<Class<? extends T>> found = new ArrayList<>();
        ModContainer mod = FabricLoader.getInstance().getModContainer("arsenic")
                .orElseThrow(() -> new IllegalStateException("arsenic mod container missing"));
        String folder = pkg.replace('.', '/');
        ClassLoader loader = ClassScanner.class.getClassLoader();

        for (Path root : mod.getRootPaths()) {
            Path dir = root.resolve(folder);
            if (!Files.isDirectory(dir))
                continue;
            try (Stream<Path> files = Files.walk(dir)) {
                files.filter(p -> p.toString().endsWith(".class")).forEach(p -> {
                    String name = root.relativize(p).toString().replace('\\', '/').replace('/', '.');
                    name = name.substring(0, name.length() - ".class".length());
                    try {
                        Class<?> c = Class.forName(name, false, loader);
                        if (type.isAssignableFrom(c) && c != type && !c.isInterface() && !Modifier.isAbstract(c.getModifiers()))
                            found.add((Class<? extends T>) c);
                    } catch (Throwable ignored) {
                        // not loadable on its own (e.g. a mixin) - not something we were looking for
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException("Failed to scan " + dir, e);
            }
        }
        return found;
    }
}
