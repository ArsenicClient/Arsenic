package arsenic.utils.java;

import java.io.File;
import java.net.URL;
import java.util.Arrays;

public class JavaUtils {
    public static File[] getFilesFromPackage(String packageName) {
        URL root = Thread.currentThread().getContextClassLoader().getResource(packageName.replace(".", "/"));
        return new File(root.getFile()).listFiles();
    }

    public static <T> T[] concat(T[] a, T[] b) {
        final T[] newArray = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, newArray, a.length, b.length);

        return newArray;
    }
}
