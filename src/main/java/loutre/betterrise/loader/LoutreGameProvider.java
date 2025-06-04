package loutre.betterrise.loader;

import loutre.betterrise.EntryPoint;
import loutre.betterrise.loader.patcher.EntrypointPatcher;
import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.game.patch.GameTransformer;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.util.Arguments;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import net.fabricmc.loader.impl.util.log.LogHandler;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
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

        String userHome = System.getProperty("user.home", ".");
        String appData = System.getenv("APPDATA");
        String basePath = (appData != null) ? appData : userHome;
        File minecraftDir = new File(basePath, ".minecraft/");
        this.gameDirectory = Path.of(minecraftDir.toURI());

        try {
            File currentDir = new File(LoutreGameProvider.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).getParentFile();

            Path gameJar = new File(currentDir, "Standalone.jar").toPath();
            Path libraryJar = new File(currentDir, "Libraries.jar").toPath();
            Path netty = new File(currentDir, "../linux-x64/netty-all-4.0.23.Final.jar").toPath();

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
            jars.add(netty);
            transformer.locateEntrypoints(launcher, gameJar);
            transformer.locateEntrypoints(launcher, netty);

        } catch (URISyntaxException e) {
            Log.error(LogCategory.GAME_PROVIDER, "Failed to locate game directory: %s", e);
            return false;
        }

        return true;
    }

    @Override
    public void initialize(FabricLauncher launcher) {
        // setupLogHandler(launcher, true);
    }

    @Override
    public GameTransformer getEntrypointTransformer() {
        return transformer;
    }

    @Override
    public void unlockClassPath(FabricLauncher launcher) {
        for (Path path : jars) {
            launcher.addToClassPath(path);
            transformer.locateEntrypoints(launcher, path);
        }
    }

    @Override
    public void launch(ClassLoader loader) {
        try {
            String userHome = System.getProperty("user.home", ".");
            String appData = System.getenv("APPDATA");
            String basePath = (appData != null) ? appData : userHome;
            File minecraftDir = new File(basePath, ".minecraft/");

            boolean isNotMac = !System.getProperty("os.name").toLowerCase().contains("mac");

            String[] launchArgs = new String[]{
                    "--version", "1.8",
                    "--accessToken", "0",
                    isNotMac ? "--gameDir" : "",
                    isNotMac ? new File(minecraftDir, ".").getAbsolutePath() : "",
                    "--assetsDir", isNotMac ? new File(minecraftDir, "assets/").getAbsolutePath() : "assets",
                    "--assetIndex", "1.8",
                    "--userProperties", "{}",
                    "-Dfabric.mixin.debug", "true"
            };
            Class<?> mainClass = loader.loadClass(getEntrypoint());
            Method mainMethod = mainClass.getMethod("main", String[].class);
            mainMethod.invoke(null, (Object) launchArgs);

        } catch (Exception e) {
            Log.error(LogCategory.GAME_PROVIDER, "Failed to launch game: %s", e);
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
    @SuppressWarnings("unused")
    private void setupLogHandler(FabricLauncher launcher, boolean useTargetCl) {
        System.setProperty("log4j2.formatMsgNoLookups", "true");

        try {
            final String logHandlerClsName = "net.fabricmc.loader.impl.game.minecraft.Log4jLogHandler";

            ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
            Class<?> logHandlerClass;

            if (useTargetCl) {
                Thread.currentThread().setContextClassLoader(launcher.getTargetClassLoader());
                logHandlerClass = launcher.loadIntoTarget(logHandlerClsName);
            } else {
                logHandlerClass = Class.forName(logHandlerClsName);
            }

            Log.init((LogHandler) logHandlerClass.getConstructor().newInstance(), true);
            Thread.currentThread().setContextClassLoader(previousClassLoader);

            Log.info(LogCategory.GAME_PROVIDER, "Logging initialized with %s", logHandlerClsName);
        } catch (ReflectiveOperationException e) {
            Log.error(LogCategory.GAME_PROVIDER, "Failed to initialize logging", e);
        }
    }
    public static void main(String[] args) throws Exception {
        if (System.getProperty("reload") == null) {
            String javaHome = System.getProperty("java.home");
            String javaBin = javaHome + "/bin/java";

            String jarName = new File(EntryPoint.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI()).getName();
            String natives =  "-Djava.library.path=../linux-x64/bin:./dependants/windows/1.8.9-natives:.dependants/macos_arm/1.8.9-natives-mac/";
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                natives = natives.replace(":", ";");
            }

            ProcessBuilder pb = new ProcessBuilder(
                    javaBin,
                    natives,
                    "-noverify",
                    "-XX:+DisableAttachMechanism",
                    "-Dreload=true",
                    "-jar",
                    jarName
            );

            pb.inheritIO();

            Process process = pb.start();
            int exitCode = process.waitFor();
            System.out.println("Child process exited with code " + exitCode);
            return;
        }

        String[] launchArgs = {
                "--gameDirectory=run",
                "-Dfabric.game.provider=loutre.betterrise.loader.LoutreGameProvider"
        };

        String[] combinedArgs = new String[launchArgs.length + args.length];
        System.arraycopy(launchArgs, 0, combinedArgs, 0, launchArgs.length);
        System.arraycopy(args, 0, combinedArgs, launchArgs.length, args.length);

        net.fabricmc.loader.impl.launch.knot.KnotClient.main(combinedArgs);
    }
}
