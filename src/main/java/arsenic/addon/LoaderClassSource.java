package arsenic.addon;

import arsenic.runtime.InjectedLaunch;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Reads class files as resources of a class loader. Correct wherever class names on disk equal runtime names. */
public class LoaderClassSource implements ClassSource {

    private final ClassLoader loader;

    public LoaderClassSource(ClassLoader loader) {
        this.loader = loader;
    }

    @Override
    public byte[] getBytes(String internalName) {
        byte[] bytes = readResource(loader, internalName + ".class");
        // Lunar Client renames Minecraft as it loads it, so its classes may have no class file under these names
        return bytes != null ? bytes : InjectedLaunch.classBytes(internalName);
    }

    static byte[] readResource(ClassLoader loader, String path) {
        try (InputStream in = loader.getResourceAsStream(path)) {
            if (in == null)
                return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1)
                out.write(buf, 0, n);
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
}
