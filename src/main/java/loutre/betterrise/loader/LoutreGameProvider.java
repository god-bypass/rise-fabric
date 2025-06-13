package loutre.betterrise.loader;

import loutre.betterrise.EntryPoint;
import loutre.betterrise.loader.patcher.EntrypointPatcher;
import loutre.betterrise.loader.utils.Utils;
import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.game.patch.GameTransformer;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.util.Arguments;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public class LoutreGameProvider implements GameProvider {

    private final List<Path> jars = new ArrayList<>();
    private Arguments arguments;
    private Path gameDirectory;
    private final LoutreGameTransformer transformer = new LoutreGameTransformer(
            new EntrypointPatcher()
    );

    @Override
    public String getGameId() {
        return "rise-client";
    }

    @Override
    public String getGameName() {
        return "Rise w/Fabric (by Loutre)";
    }

    @Override
    public String getRawGameVersion() {
        return "6.5.5";
    }

    @Override
    public String getNormalizedGameVersion() {
        return "6.5.5";
    }

    @Override
    public Collection<BuiltinMod> getBuiltinMods() {
        return Collections.emptyList();
    }

    @Override
    public String getEntrypoint() {
        return "net.minecraft.client.main.Main";
    }

    @Override
    public Path getLaunchDirectory() {
        return gameDirectory;
    }

    @Override
    public boolean isObfuscated() {
        return false;
    }

    @Override
    public boolean requiresUrlClassLoader() {
        return false;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean locateGame(FabricLauncher launcher, String[] args) {
        this.arguments = new Arguments();
        this.arguments.parse(args);

        if (args.length == 0) {
            Log.error(LogCategory.GAME_PROVIDER, "Missing launch directory argument!");
            return false;
        }

        this.gameDirectory = Path.of(Utils.getMinecraftDir().toURI());

        File currentDir = Utils.getJar().toFile();

        Path gameJar = new File(currentDir, "Standalone.jar").toPath();
        Path libraryJar = new File(currentDir, "Libraries.jar").toPath();

        if (!Files.exists(gameJar)) {
            Log.error(LogCategory.GAME_PROVIDER, "Game JAR does not exist: %s", gameJar);
            return false;
        }

        if (!Files.exists(libraryJar)) {
            Log.error(LogCategory.GAME_PROVIDER, "Library JAR does not exist: %s", libraryJar);
            return false;
        }

        patchLibraries(libraryJar);

        jars.add(gameJar);
        jars.add(libraryJar);
        transformer.locateEntrypoints(launcher, gameJar);

        return true;
    }

    @Override
    public void initialize(FabricLauncher launcher) {

    }

    @Override
    public GameTransformer getEntrypointTransformer() {
        return transformer;
    }

    @Override
    public void unlockClassPath(FabricLauncher launcher) {
        for (Path path : jars) {
            launcher.addToClassPath(path);
        }
    }

    @Override
    public void launch(ClassLoader loader) {
        try {
            Path minecraftDir = Utils.getMinecraftDir().toPath();
            boolean isMac = System.getProperty("os.name").toLowerCase().contains("mac");

            String[] launchArgs = {
                    "--version", "1.8",
                    "--accessToken", "0",
                    "--gameDir", !isMac ? minecraftDir.toString() : "",
                    "--assetsDir", !isMac ? minecraftDir.resolve("assets").toString() : "assets",
                    "--assetIndex", "1.8",
                    "--userProperties", "{}",
                    "-Dfabric.mixin.debug", "true"
            };

            Class<?> mainClass = loader.loadClass(getEntrypoint());
            Method mainMethod = mainClass.getMethod("main", String[].class);
            mainMethod.invoke(null, (Object) launchArgs);

        } catch (Exception e) {
            Log.error(LogCategory.GAME_PROVIDER, "Game launch failed", e);
        }
    }

    @Override
    public Arguments getArguments() {
        return arguments;
    }

    @Override
    public String[] getLaunchArguments(boolean sanitize) {
        return arguments.toArray();
    }

    public void patchLibraries(Path libraries) {
        try (JarFile jarFile = new JarFile(libraries.toFile())) {
            boolean hasModifications = false;
            Enumeration<JarEntry> entries = jarFile.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                if ("sun/misc/Unsafe.class".equals(name) || name.startsWith("org/objectweb/asm/")) {
                    hasModifications = true;
                    break;
                }
            }

            if (!hasModifications) {
                return;
            }
        } catch (IOException e) {
            Log.error(LogCategory.GAME_PROVIDER, "Failed to open jar for checking: %s", e);
            return;
        }

        try {
            Path tempJar = Files.createTempFile("libraries-cleaned", ".jar");

            try (JarFile jarFile = new JarFile(libraries.toFile());
                 JarOutputStream jos = new JarOutputStream(Files.newOutputStream(tempJar))) {

                for (JarEntry entry : Collections.list(jarFile.entries())) {
                    String name = entry.getName();

                    if ("sun/misc/Unsafe.class".equals(name)) {
                        Log.info(LogCategory.GAME_PROVIDER, "Removed sun/misc/Unsafe.class from libraries.jar");
                        continue;
                    }

                    if (name.startsWith("org/objectweb/asm/")) {
                        Log.info(LogCategory.GAME_PROVIDER, "Removed ASM class from libraries.jar: %s", name);
                        continue;
                    }

                    jos.putNextEntry(new JarEntry(name));
                    try (InputStream is = jarFile.getInputStream(entry)) {
                        is.transferTo(jos);
                    }
                    jos.closeEntry();
                }
            }

            Files.move(tempJar, libraries, StandardCopyOption.REPLACE_EXISTING);

        } catch (IOException e) {
            Log.error(LogCategory.GAME_PROVIDER, "Failed to patch libraries.jar: %s", e);
        }
    }


    public static void main(String[] args) throws Exception {
        if (System.getProperty("reload") == null) {
            reload();
            return;
        }
        launchFabric(args);
    }

    private static void reload() throws Exception {
        String jarName = new File(EntryPoint.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI()).getName();

        String javaBin = Utils.getJava();
        String natives = Utils.getNativePath();

        if (javaBin == null || !new File(javaBin).exists()) {
            Log.error(LogCategory.GAME_PROVIDER, "Missing Java binary");
            return;
        }

        new ProcessBuilder(
                javaBin, natives, "-noverify", "-XX:+DisableAttachMechanism",
                "-Dreload=true", "-jar", jarName
        ).inheritIO().start();

    }

    private static void launchFabric(String[] args) {
        String[] extraArgs = {
                "--gameDirectory=run",
                "-Dfabric.game.provider=loutre.betterrise.loader.LoutreGameProvider"
        };

        String[] combinedArgs = Arrays.copyOf(extraArgs, extraArgs.length + args.length);
        System.arraycopy(args, 0, combinedArgs, extraArgs.length, args.length);

        Log.info(LogCategory.GAME_PROVIDER, "Launching KnotClient");
        net.fabricmc.loader.impl.launch.knot.KnotClient.main(combinedArgs);
    }
}
