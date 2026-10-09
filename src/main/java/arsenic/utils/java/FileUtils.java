package arsenic.utils.java;

import net.minecraft.client.Minecraft;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileUtils extends UtilityClass {

    private static File folder;

    public static String getArsenicFolderDirAsString() {
        return getArsenicFolderDirAsFile().getPath();
    }

    /**
     * The Arsenic folder: configs, addons, themes and everything else Arsenic saves. It sits in the user's home folder
     * (C:\Users\<name>\Arsenic on Windows) rather than the game folder, so every launcher and instance shares it.
     * The first time, an Arsenic folder from the game folder (where it used to live) is copied over.
     */
    public static synchronized File getArsenicFolderDirAsFile() {
        if (folder == null) {
            String home = System.getProperty("user.home");
            File legacy = legacyFolder();
            if (home == null || home.isEmpty()) {
                folder = legacy;
            } else {
                folder = new File(home, "Arsenic");
                if (!folder.exists() && legacy != null && legacy.isDirectory())
                    copyFolder(legacy, folder);
            }
        }
        return folder;
    }

    /** Where the Arsenic folder used to be: inside the game folder of the launcher that started the game. */
    private static File legacyFolder() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc == null || mc.mcDataDir == null ? new File("Arsenic") : new File(mc.mcDataDir, "Arsenic");
    }

    private static void copyFolder(File from, File to) {
        Path source = from.toPath(), target = to.toPath();
        try (Stream<Path> files = Files.walk(source)) {
            List<Path> paths = files.collect(Collectors.toList());
            for (Path p : paths) {
                Path dest = target.resolve(source.relativize(p).toString());
                if (Files.isDirectory(p))
                    Files.createDirectories(dest);
                else
                    Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            }
            System.out.println("Copied the Arsenic folder from " + from + " to " + to);
        } catch (Exception e) {
            System.err.println("Could not copy the Arsenic folder from " + from + " to " + to + ": " + e);
        }
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
