package arsenic.addon;

import java.util.Map;

/**
 * Defines compiled addon classes from memory. Addon classes win over same-named classes on the classpath (the IDE
 * also compiles the bundled addons, and the copy in the addons folder is the one that must run); everything else
 * resolves through the game's class loader.
 */
final class AddonClassLoader extends ClassLoader {

    private final Map<String, byte[]> classes;

    AddonClassLoader(ClassLoader parent, Map<String, byte[]> classes) {
        super(parent);
        this.classes = classes;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null && classes.containsKey(name))
                loaded = findClass(name);
            if (loaded != null) {
                if (resolve)
                    resolveClass(loaded);
                return loaded;
            }
        }
        return super.loadClass(name, resolve);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] bytes = classes.get(name);
        if (bytes == null)
            throw new ClassNotFoundException(name);
        return defineClass(name, bytes, 0, bytes.length);
    }
}
