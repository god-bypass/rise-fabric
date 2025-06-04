package loutre.betterrise;


import java.io.File;

public class EntryPoint {
    public static void main(String[] args) throws Exception {
        if (System.getProperty("reload") == null) {
            String javaHome = System.getProperty("java.home");
            String javaBin = javaHome + "/bin/java";
            String jarName = new File(EntryPoint.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI()).getName();

            ProcessBuilder pb = new ProcessBuilder(
                    javaBin,
                    "-Djava.library.path=../linux-x64/bin:../linux-x64/jdk-21.0.5+11/lib/",
                    "--add-exports", "java.base/sun.misc=ALL-UNNAMED",
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
