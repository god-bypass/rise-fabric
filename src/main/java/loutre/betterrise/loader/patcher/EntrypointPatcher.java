package loutre.betterrise.loader.patcher;

import net.fabricmc.loader.impl.game.minecraft.Hooks;
import net.fabricmc.loader.impl.game.patch.GamePatch;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.ListIterator;
import java.util.function.Consumer;
import java.util.function.Function;

public class EntrypointPatcher extends GamePatch {
    @Override
    public void process(FabricLauncher fabricLauncher, Function<String, ClassReader> function, Consumer<ClassNode> consumer) {
        ClassNode reader = readClass(function.apply("net/minecraft/client/d"));

        if(reader == null) {
            return;
        }
        System.out.println(reader);
        MethodNode mainMethod = findMethod(reader, (method) -> method.name.equals("su") && method.desc.equals("()V"));
        System.out.println(mainMethod);

        if (mainMethod != null) {
            ListIterator<AbstractInsnNode> iter = mainMethod.instructions.iterator();

            while (iter.hasNext()) {

                AbstractInsnNode insn = iter.next();
                if (insn instanceof MethodInsnNode methodInsn &&
                        methodInsn.getOpcode() == Opcodes.INVOKEINTERFACE &&
                        methodInsn.owner.equals("org/apache/logging/log4j/Logger") &&
                        methodInsn.name.equals("info") &&
                        methodInsn.desc.equals("(Ljava/lang/String;)V")) {

                    AbstractInsnNode prev = insn.getPrevious();
                    if (prev instanceof InvokeDynamicInsnNode indy &&
                            indy.bsmArgs.length > 0 &&
                            indy.bsmArgs[0] instanceof String str &&
                            str.contains("LWJGL Version")) {
                        finishEntrypoint(iter);
                        break;
                    }
                }
            }
        }

        consumer.accept(reader);
    }
    private void finishEntrypoint(ListIterator<AbstractInsnNode> it) {
        String methodName = String.format("start%s", "Client");
        String os = System.getProperty("os.name").toLowerCase();
        String minecraftPath;
        if (os.contains("win")) {
            minecraftPath = System.getenv("APPDATA") + "\\.minecraft";
        } else {
            minecraftPath = System.getProperty("user.home") + "/.minecraft";
        }
        it.add(new TypeInsnNode(Opcodes.NEW, "java/io/File"));
        it.add(new InsnNode(Opcodes.DUP));
        it.add(new LdcInsnNode(minecraftPath));
        it.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/io/File", "<init>", "(Ljava/lang/String;)V", false));
        it.add(new VarInsnNode(Opcodes.ALOAD, 0));
        it.add(new MethodInsnNode(Opcodes.INVOKESTATIC, Hooks.INTERNAL_NAME, methodName, "(Ljava/io/File;Ljava/lang/Object;)V", false));
    }
}
