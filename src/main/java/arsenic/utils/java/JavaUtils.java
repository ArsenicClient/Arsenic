package arsenic.utils.java;

import java.util.Arrays;

public class JavaUtils extends UtilityClass {

    public static <T> T[] concat(T[] a, T[] b) {
        final T[] newArray = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, newArray, a.length, b.length);

        return newArray;
    }
}
