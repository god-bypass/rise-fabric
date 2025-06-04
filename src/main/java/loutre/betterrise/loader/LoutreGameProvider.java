package loutre.betterrise.loader;

import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.game.patch.GameTransformer;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.util.Arguments;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;

public class LoutreGameProvider implements GameProvider {
    private static final Logger logger = Logger.getLogger("Game Provider");
    private final List<Path> jars = new ArrayList<>();
    private Arguments arguments;
    private Path gameDirectory;
    private final LoutreGameTransformer transformer = new LoutreGameTransformer();

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
        System.out.println(Arrays.toString(arguments.toArray()));

        if (args.length == 0) {
            logger.severe("Missing launch directory argument!");
            return false;
        }

        String userHome = System.getProperty("user.home", ".");
        String appData = System.getenv("APPDATA");
        String basePath = (appData != null) ? appData : userHome;
        File minecraftDir = new File(basePath, ".minecraft/");
        this.gameDirectory = Paths.get(minecraftDir.toURI());

        try {
            File currentDir = new File(LoutreGameProvider.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).getParentFile();

            Path gameJar = new File(currentDir, "Standalone.jar").toPath();
            Path libraryJar = new File(currentDir, "Libraries.jar").toPath();
            Path netty = new File(currentDir, "../linux-x64/netty-all-4.0.23.Final.jar").toPath();

            if (!Files.exists(gameJar)) {
                System.err.println("Game JAR does not exist: " + gameJar);
                return false;
            }

            if (!Files.exists(libraryJar)) {
                System.err.println("Library JAR does not exist: " + libraryJar);
                return false;
            }

            jars.add(gameJar);
            jars.add(libraryJar);
            jars.add(netty);
            transformer.locateEntrypoints(launcher, gameJar);
            transformer.locateEntrypoints(launcher, netty);

        } catch (URISyntaxException e) {
            logger.severe(e.getMessage());
            return false;
        }

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
                    "--userProperties", "{}"
            };

            Class<?> mainClass = loader.loadClass("net.minecraft.client.main.Main");
            Method mainMethod = mainClass.getMethod("main", String[].class);
            mainMethod.invoke(null, (Object) launchArgs);

        } catch (Exception e) {
            logger.severe(e.getMessage());
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


}
