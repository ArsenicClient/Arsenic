package arsenic.utils.java;

import java.io.File;
import java.net.URL;

public class JavaUtils {
    public static File[] getFilesFromPackage(String packageName) {
        URL root = Thread.currentThread().getContextClassLoader().getResource(packageName.replace(".", "/"));
        return new File(root.getFile()).listFiles();
    }
}
