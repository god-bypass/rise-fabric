package loutre.betterrise.loader.utils;

import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;

public class Utils {
    private static final String os = System.getProperty("os.name").toLowerCase();
    public static File getMinecraftDir(){
        String parent = os.contains("win")
                ? System.getenv("APPDATA") : System.getProperty("user.home");
        return new File(parent, ".minecraft/");
    }
    public static Path getJar() {
        try {
            return new File(Utils.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI()).getParentFile().toPath();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
    private static String getMacJava() {
        ProcessBuilder pb = new ProcessBuilder("sysctl", "-n", "machdep.cpu.brand_string");
        try {
            Process process = pb.start();
            try (BufferedReader reader = process.inputReader()) {
                String output = reader.readLine();
                int exitCode = process.waitFor();

                if (exitCode == 0 && output != null) {
                    if (output.toLowerCase().contains("apple")) {
                        return "./dependants/macos_arm/jdk-22/Contents/Home/bin/java";
                    } else if (output.toLowerCase().contains("intel")) {
                        Log.error(LogCategory.GENERAL, "dm @transgenre so i add intel compatibility");
                        return null;
                    }
                } else {
                    Log.error(LogCategory.GENERAL, "Failed to detect cpu type on mac.");
                }
            }
        } catch (IOException | InterruptedException e) {
            Log.error(LogCategory.GENERAL, "Error while detecting mac cpu", e);
        }
        return null;
    }
    public static String getJava() {
        if (os.contains("win")) {
            return ".\\dependants\\windows\\jdk-22\\bin\\java.exe";
        } else if (os.contains("mac")) {
            return getMacJava();
        } else if (os.contains("linux")) {
            return "../linux-x64/jdk-21.0.5+11/bin/java";
        }

        Log.error(LogCategory.GENERAL, "Unsupported OS for Java binary resolution: %s", os);
        return null;
    }

    public static String getNativePath() {
        String path = "-Djava.library.path=../linux-x64/bin:./dependants/windows/1.8.9-natives:.dependants/macos_arm/1.8.9-natives-mac/";

        if (os.contains("win")) {
            return path.replace(":", ";");
        }

        return path;
    }
}
