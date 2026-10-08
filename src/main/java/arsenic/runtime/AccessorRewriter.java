package arsenic.runtime;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.RemappingClassAdapter;
import org.objectweb.asm.tree.*;

import java.nio.charset.StandardCharsets;

import static org.objectweb.asm.Opcodes.*;

/**
 * Keeps the accessor interfaces in arsenic.injection.accessor working in an injected client. As a mod, mixins make
 * Minecraft classes implement them, so code casts (`((IMixinEntity) mc.thePlayer)`). An injected client cannot add
 * interfaces to classes that are already loaded, so every call on an accessor interface becomes a call to
 * {@link AccessorBridge}, and the interface types become Object so the casts always pass.
 *
 * Registered on the game's class loader for the client's own classes, and applied to compiled addons.
 */
public final class AccessorRewriter implements IClassTransformer {

    static final String ACCESSOR_PACKAGE = "arsenic/injection/accessor/";
    private static final String BRIDGE = "arsenic/runtime/AccessorBridge";
    private static final byte[] MARKER = ACCESSOR_PACKAGE.getBytes(StandardCharsets.ISO_8859_1);

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !transformedName.startsWith("arsenic.") || transformedName.startsWith("arsenic.injection.") || transformedName.startsWith("arsenic.runtime."))
            return basicClass;
        return rewrite(basicClass);
    }

    static boolean isAccessor(String internalName) {
        if (!internalName.startsWith(ACCESSOR_PACKAGE))
            return false;
        String simple = internalName.substring(ACCESSOR_PACKAGE.length());
        return simple.startsWith("IMixin") || simple.endsWith("Accessor");
    }

    /** @return the class with accessor calls rewritten, or the same array when it uses none */
    public static byte[] rewrite(byte[] classBytes) {
        if (!contains(classBytes, MARKER))
            return classBytes;

        ClassNode cn = new ClassNode();
        new ClassReader(classBytes).accept(cn, ClassReader.EXPAND_FRAMES);
        boolean changed = false;
        for (MethodNode mn : cn.methods) {
            for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; ) {
                AbstractInsnNode next = insn.getNext();
                if (insn.getOpcode() == INVOKEINTERFACE && isAccessor(((MethodInsnNode) insn).owner)) {
                    mn.instructions.insertBefore(insn, bridgeCall(mn, (MethodInsnNode) insn));
                    mn.instructions.remove(insn);
                    changed = true;
                }
                insn = next;
            }
        }
        if (!changed && !referencesAccessorType(cn))
            return classBytes;

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(new RemappingClassAdapter(writer, new Remapper() {
            @Override
            public String map(String typeName) {
                return isAccessor(typeName) ? "java/lang/Object" : typeName;
            }
        }));
        return writer.toByteArray();
    }

    /** [receiver, args...] -> AccessorBridge.invoke(receiver, owner, name + desc, args[]) -> unboxed result. */
    private static InsnList bridgeCall(MethodNode mn, MethodInsnNode call) {
        InsnList list = new InsnList();
        Type[] args = Type.getArgumentTypes(call.desc);
        int[] slots = new int[args.length];
        int next = mn.maxLocals;
        for (int i = 0; i < args.length; i++) {
            slots[i] = next;
            next += args[i].getSize();
        }
        mn.maxLocals = next;
        for (int i = args.length - 1; i >= 0; i--)
            list.add(new VarInsnNode(args[i].getOpcode(ISTORE), slots[i]));

        list.add(new LdcInsnNode(call.owner));
        list.add(new LdcInsnNode(call.name + call.desc));
        list.add(intConst(args.length));
        list.add(new TypeInsnNode(ANEWARRAY, "java/lang/Object"));
        for (int i = 0; i < args.length; i++) {
            list.add(new InsnNode(DUP));
            list.add(intConst(i));
            list.add(new VarInsnNode(args[i].getOpcode(ILOAD), slots[i]));
            box(list, args[i]);
            list.add(new InsnNode(AASTORE));
        }
        list.add(new MethodInsnNode(INVOKESTATIC, BRIDGE, "invoke",
                "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;", false));
        unbox(list, Type.getReturnType(call.desc));
        return list;
    }

    private static AbstractInsnNode intConst(int value) {
        return value <= 5 ? new InsnNode(ICONST_0 + value) : new IntInsnNode(BIPUSH, value);
    }

    private static String boxType(Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN: return "java/lang/Boolean";
            case Type.BYTE: return "java/lang/Byte";
            case Type.CHAR: return "java/lang/Character";
            case Type.SHORT: return "java/lang/Short";
            case Type.INT: return "java/lang/Integer";
            case Type.FLOAT: return "java/lang/Float";
            case Type.LONG: return "java/lang/Long";
            case Type.DOUBLE: return "java/lang/Double";
            default: return null;
        }
    }

    private static void box(InsnList list, Type type) {
        String box = boxType(type);
        if (box != null)
            list.add(new MethodInsnNode(INVOKESTATIC, box, "valueOf", "(" + type.getDescriptor() + ")L" + box + ";", false));
    }

    private static void unbox(InsnList list, Type type) {
        if (type.getSort() == Type.VOID) {
            list.add(new InsnNode(POP));
            return;
        }
        String box = boxType(type);
        if (box != null) {
            list.add(new TypeInsnNode(CHECKCAST, box));
            list.add(new MethodInsnNode(INVOKEVIRTUAL, box, type.getClassName() + "Value", "()" + type.getDescriptor(), false));
        } else if (!(type.getSort() == Type.OBJECT && isAccessor(type.getInternalName()))) {
            list.add(new TypeInsnNode(CHECKCAST, type.getSort() == Type.ARRAY ? type.getDescriptor() : type.getInternalName()));
        }
    }

    private static boolean referencesAccessorType(ClassNode cn) {
        for (MethodNode mn : cn.methods)
            for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext())
                if (insn instanceof TypeInsnNode && isAccessor(((TypeInsnNode) insn).desc))
                    return true;
        return false;
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++)
                if (haystack[i + j] != needle[j])
                    continue outer;
            return true;
        }
        return false;
    }
}
