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
 * Applies Arsenic's annotation-driven bytecode changes to a class: {@link RequiresPlayer} (return early until a
 * player is loaded) and {@link arsenic.utils.keystrokes.SyntheticKey} (press a key at the start of the method).
 * Used both by the launch transformer and on compiled addon classes, which the game's transformers never see.
 */
public final class AnnotationTransformer {

    private static final String REQUIRES_PLAYER = "Larsenic/asm/RequiresPlayer;";
    private static final String SYNTHETIC_KEY = "Larsenic/utils/keystrokes/SyntheticKey;";
    private static final String KEY_TYPE = "Larsenic/utils/keystrokes/SyntheticKeys$Key;";

    private AnnotationTransformer() {}

    public static byte[] transform(byte[] basicClass) {
        ClassReader classReader = new ClassReader(basicClass);
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM5, classWriter) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM5, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    private String syntheticKey;

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
                        if (descriptor.equals(SYNTHETIC_KEY)) {
                            // The enum value arrives after this call, so the press is emitted in visitCode
                            return new AnnotationVisitor(Opcodes.ASM5, super.visitAnnotation(descriptor, visible)) {
                                @Override
                                public void visitEnum(String name, String enumDescriptor, String value) {
                                    syntheticKey = value;
                                    super.visitEnum(name, enumDescriptor, value);
                                }
                            };
                        }
                        return super.visitAnnotation(descriptor, visible);
                    }

                    @Override
                    public void visitCode() {
                        super.visitCode();
                        if (syntheticKey != null) {
                            this.visitFieldInsn(GETSTATIC, "arsenic/utils/keystrokes/SyntheticKeys$Key", syntheticKey, KEY_TYPE);
                            this.visitMethodInsn(INVOKESTATIC, "arsenic/utils/keystrokes/SyntheticKeys", "press",
                                    "(" + KEY_TYPE + ")V", false);
                        }
                    }
                };
            }
        };

        classReader.accept(classVisitor, 0);
        return classWriter.toByteArray();
    }
}
