package arsenic.asm;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static org.objectweb.asm.Opcodes.*;

/**
 * Applies Arsenic's bytecode changes to a class: {@link RequiresPlayer} (return early until a player is loaded) and
 * every attack call to PlayerControllerMP.attackEntity (press LMB before it).
 * Used both by the launch transformer and on compiled addon classes, which the game's transformers never see.
 */
public final class AnnotationTransformer {

    private static final String REQUIRES_PLAYER = "Larsenic/asm/RequiresPlayer;";
    private static final String KEY_TYPE = "Larsenic/utils/keystrokes/SyntheticKeys$Key;";

    // PlayerControllerMP.attackEntity: MCP name, and the SRG name used by Forge builds
    private static final String ATTACK_OWNER = "PlayerControllerMP";
    private static final String[] ATTACK_NAMES = {"attackEntity", "func_78764_a"};

    private AnnotationTransformer() {}

    private static boolean isAttack(String owner, String name) {
        if (!owner.endsWith(ATTACK_OWNER))
            return false;
        for (String attack : ATTACK_NAMES)
            if (attack.equals(name))
                return true;
        return false;
    }

    public static byte[] transform(byte[] basicClass) {
        ClassReader classReader = new ClassReader(basicClass);
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM5, classWriter) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM5, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                        if (descriptor.equals(REQUIRES_PLAYER)) {
                            this.visitMethodInsn(INVOKESTATIC, "arsenic/utils/minecraft/PlayerUtils", "isPlayerNotLoaded", "()Z", false);
                            Label l0 = new Label();
                            this.visitJumpInsn(IFEQ, l0);
                            this.visitInsn(RETURN);
                            this.visitLabel(l0);
                            this.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                        }
                        return super.visitAnnotation(descriptor, visible);
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                        // Every attack the client sends is one LMB press, wherever it is made
                        if (isAttack(owner, name)) {
                            this.visitFieldInsn(GETSTATIC, "arsenic/utils/keystrokes/SyntheticKeys$Key", "LMB", KEY_TYPE);
                            this.visitMethodInsn(INVOKESTATIC, "arsenic/utils/keystrokes/SyntheticKeys", "press",
                                    "(" + KEY_TYPE + ")V", false);
                        }
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                    }
                };
            }
        };

        classReader.accept(classVisitor, 0);
        return classWriter.toByteArray();
    }
}
