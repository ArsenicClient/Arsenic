package arsenic.addon;

import arsenic.lib.asm.ClassReader;
import arsenic.lib.asm.ClassVisitor;
import arsenic.lib.asm.Opcodes;
import arsenic.lib.asm.ClassWriter;
import arsenic.lib.asm.commons.ClassRemapper;
import arsenic.lib.asm.commons.Remapper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** The two remapping directions the addon pipeline needs. */
final class Remappers {

    private Remappers() {}

    /** Super types of a class, looked up from compiled addon classes first and the class source second. */
    static final class Hierarchy {
        private final ClassSource source;
        private final Map<String, String[]> known = new HashMap<>();

        Hierarchy(ClassSource source) {
            this.source = source;
        }

        void register(byte[] classBytes) {
            ClassReader reader = new ClassReader(classBytes);
            known.put(reader.getClassName(), supersOf(reader));
        }

        private static String[] supersOf(ClassReader reader) {
            String[] ifaces = reader.getInterfaces();
            String[] all = new String[ifaces.length + 1];
            all[0] = reader.getSuperName();
            System.arraycopy(ifaces, 0, all, 1, ifaces.length);
            return all;
        }

        private String[] supers(String name) {
            String[] cached = known.get(name);
            if (cached != null)
                return cached;
            byte[] bytes = source.getBytes(name);
            String[] result = bytes == null ? new String[0] : supersOf(new ClassReader(bytes));
            known.put(name, result);
            return result;
        }

        /** Depth first search from owner through its super types for the first non-null lookup result. */
        <T> T find(String owner, Function<String, T> lookup) {
            return find(owner, lookup, new HashSet<>());
        }

        private <T> T find(String name, Function<String, T> lookup, Set<String> seen) {
            if (name == null || name.charAt(0) == '[' || !seen.add(name))
                return null;
            T hit = lookup.apply(name);
            if (hit != null)
                return hit;
            for (String parent : supers(name)) {
                T result = find(parent, lookup, seen);
                if (result != null)
                    return result;
            }
            return null;
        }
    }

    /** MCP -> SRG, applied to the compiled addon so it links against the runtime Minecraft classes. */
    static byte[] toSrg(byte[] classBytes, MappingTable table, Hierarchy hierarchy) {
        Remapper remapper = new Remapper() {
            @Override
            public String mapMethodName(String owner, String name, String desc) {
                if (name.charAt(0) == '<')
                    return name;
                String srg = hierarchy.find(owner, c -> table.methodToSrg(c, name, desc));
                return srg != null ? srg : name;
            }

            @Override
            public String mapFieldName(String owner, String name, String desc) {
                String srg = hierarchy.find(owner, c -> table.fieldToSrg(c, name));
                return srg != null ? srg : name;
            }
        };
        ClassWriter writer = new ClassWriter(0);
        new ClassReader(classBytes).accept(new ClassRemapper(writer, remapper), ClassReader.EXPAND_FRAMES);
        return writer.toByteArray();
    }

    /** SRG -> MCP for member declarations only, so the compiler sees Minecraft with its MCP names. */
    static byte[] toMcpDeclarations(byte[] classBytes, MappingTable table) {
        Remapper remapper = new Remapper() {
            @Override
            public String mapMethodName(String owner, String name, String desc) {
                String mcp = table.methodToMcp(owner, name);
                return mcp != null ? mcp : name;
            }

            @Override
            public String mapFieldName(String owner, String name, String desc) {
                String mcp = table.fieldToMcp(owner, name);
                return mcp != null ? mcp : name;
            }
        };
        ClassWriter writer = new ClassWriter(0);
        // A deobfuscated class still carries the obfuscated simple name of its member classes ("a"), and the
        // compiler matches MovingObjectPosition.MovingObjectType by that name, so restore it from the class name.
        ClassVisitor innerNames = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public void visitInnerClass(String name, String outerName, String innerName, int access) {
                if (innerName != null && outerName != null && name.startsWith(outerName + "$"))
                    innerName = name.substring(outerName.length() + 1);
                super.visitInnerClass(name, outerName, innerName, access);
            }
        };
        new ClassReader(classBytes).accept(new ClassRemapper(innerNames, remapper), ClassReader.SKIP_CODE);
        return writer.toByteArray();
    }
}
