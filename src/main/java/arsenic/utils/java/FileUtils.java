package arsenic.utils.java;

import net.minecraft.client.Minecraft;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;

public class FileUtils extends UtilityClass {

    public static String getArsenicFolderDirAsString() {
        return Minecraft.getInstance().gameDirectory + File.separator + "Arsenic";
    }

    public static File getArsenicFolderDirAsFile() {
        return new File(Minecraft.getInstance().gameDirectory + File.separator + "Arsenic");
    }

    public static String readInputStream(InputStream inputStream) {
        StringBuilder stringBuilder = new StringBuilder();

        try {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = bufferedReader.readLine()) != null)
                stringBuilder.append(line).append('\n');

        } catch (Exception e) {
            e.printStackTrace();
        }
        return stringBuilder.toString();
    }
}
