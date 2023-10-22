package arsenic.asm;

import org.objectweb.asm.util.ASMifier;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;

import java.io.FileOutputStream;
import java.io.PrintStream;

import static jdk.nashorn.internal.runtime.regexp.joni.constants.OPCode.RETURN;
import static org.objectweb.asm.Opcodes.IFEQ;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;

public class ClassTransformer implements IClassTransformer {
    @Override
    public byte[] transform(String classname, String transformedName, byte[] basicClass) {
        if(!classname.contains("arsenic") || classname.contains("arsenic.asm.ClassTransformer"))
            return basicClass;
        ClassReader classReader = new ClassReader(basicClass);
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM5, classWriter) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM5, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                        System.out.println(descriptor);
                        if(descriptor.equals("Larsenic/asm/AgentInject;")) {
                            System.out.println("frucj");
                            this.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                            this.visitLdcInsn("Wow this method (" + name + ")" + " was just called and has a @Agent inject annotation");
                            this.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);

                            //doesntt work
                            //should inject
                            //if(isPlayerInGame)
                            // return;
                            /*this.visitMethodInsn(INVOKESTATIC, "arsenic/utils/minecraft/PlayerUtils", "isPlayerInGame", "()Z", false);
                            Label l0 = new Label();
                            this.visitJumpInsn(IFEQ, l0);
                            this.visitInsn(RETURN);
                            this.visitLabel(l0);
                            this.visitFrame(Opcodes.F_SAME, 0, null, 0, null); */
                        }
                        return super.visitAnnotation(descriptor, visible);
                    }
                };
            }
        };

        classReader.accept(classVisitor, 0);

        if(classname.equals("arsenic.main.Arsenic")) {
            try {
                FileOutputStream fos = new FileOutputStream("Arsenic.class");

                fos.write(classWriter.toByteArray());

                fos.close();

            } catch (final Exception e) {
                e.printStackTrace();
            }
        }

        return classWriter.toByteArray();
    }

    public static void h() {
        /*
        classReader.accept(classVisitor, 0);


        try {
            ASMifier.main(new String[]{"Arsenic.class"});
        } catch (Exception e) {
            throw new RuntimeException(e);
        }*/
    }
}