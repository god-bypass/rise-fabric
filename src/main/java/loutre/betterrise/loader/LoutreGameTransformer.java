package loutre.betterrise.loader;

import net.fabricmc.loader.impl.game.patch.GamePatch;
import net.fabricmc.loader.impl.game.patch.GameTransformer;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.util.LoaderUtil;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class LoutreGameTransformer extends GameTransformer {
    private final List<GamePatch> patches;
    private Map<String, byte[]> patchedClasses;
    private boolean entrypointsLocated = false;

    public LoutreGameTransformer(GamePatch... patches) {
        this.patches = Arrays.asList(patches);
    }

    private void addPatchedClass(ClassNode node) {
        String key = node.name.replace('/', '.');

        if (patchedClasses.containsKey(key)) {
            throw new RuntimeException("Duplicate addPatchedClasses call: " + key);
        }

        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        patchedClasses.put(key, writer.toByteArray());
    }

    public void locateEntrypoints(FabricLauncher launcher, Path gameJar) {
        if (entrypointsLocated) {
            return;
        }

        patchedClasses = new HashMap<>();

        try (ZipFile zf = new ZipFile(gameJar.toFile())) {
            Function<String, ClassReader> classSource = name -> {
                byte[] data = patchedClasses.get(name);

                if (data != null) {
                    return new ClassReader(data);
                }

                ZipEntry entry = zf.getEntry(LoaderUtil.getClassFileName(name));
                if (entry == null) return null;

                try (InputStream is = zf.getInputStream(entry)) {
                    return new ClassReader(is);
                } catch (IOException e) {
                    throw new UncheckedIOException(String.format("error reading %s in %s: %s", name, gameJar.toAbsolutePath(), e), e);
                }
            };

            for (GamePatch patch : patches) {
                patch.process(launcher, classSource, this::addPatchedClass);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(String.format("error reading %s: %s", gameJar.toAbsolutePath(), e), e);
        }

        Log.info(LogCategory.GAME_PATCH, "Patched %d class%s", patchedClasses.size(), patchedClasses.size() != 1 ? "s" : "");
        entrypointsLocated = true;
    }


    public byte[] transform(String className) {
        return patchedClasses.get(className);
    }
}