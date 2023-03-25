package arsenic.utils.java;

public class ColorUtils extends UtilityClass {

    public static int setColor(int value, int i, int newValue) {
        if(newValue > 0xFF)
            newValue = 0xFF;
        else if (newValue < 0)
            newValue = 0;
        int a = 24 - (i*8);
        return ((value & ~(0xFF << a)) | (newValue << a));
    }

    public static int getColor(int value, int i) {
        return (value >> (8 * (3 - i))) & 0xFF;
    }

}
