package arsenic.utils.java;

import net.minecraft.client.Minecraft;

import java.io.File;

public class FileUtils extends UtilityClass {

    public static String getArsenicFolderDirAsString() {
        return Minecraft.getMinecraft().mcDataDir + File.separator + "Arsenic";
    }

    public static File getArsenicFolderDirAsFile() {
        return new File(Minecraft.getMinecraft().mcDataDir + File.separator + "Arsenic");
    }
}
