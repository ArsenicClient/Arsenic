package arsenic.runtime;

import arsenic.lib.asm.ClassReader;
import arsenic.lib.asm.Type;
import arsenic.lib.asm.tree.AnnotationNode;
import arsenic.lib.asm.tree.ClassNode;
import arsenic.lib.asm.tree.MethodNode;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs accessor interface methods (@Accessor and @Invoker in arsenic.injection.accessor) by reflection, for the calls
 * {@link AccessorRewriter} redirects here. The member each method stands for is read from its mixin annotations,
 * the same way Mixin itself works it out.
 */
public final class AccessorBridge {

    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String ACCESSOR = "Lorg/spongepowered/asm/mixin/gen/Accessor;";
    private static final String INVOKER = "Lorg/spongepowered/asm/mixin/gen/Invoker;";

    private interface Member {
        Object run(Object self, Object[] args);
    }

    private static final Map<String, Member> MEMBERS = new ConcurrentHashMap<>();

    private AccessorBridge() {}

    public static Object invoke(Object self, String accessor, String nameAndDesc, Object[] args) {
        Member member = MEMBERS.get(accessor + '.' + nameAndDesc);
        if (member == null) {
            member = resolve(accessor, nameAndDesc);
            MEMBERS.put(accessor + '.' + nameAndDesc, member);
        }
        return member.run(self, args);
    }

    private static Member resolve(String accessor, String nameAndDesc) {
        int paren = nameAndDesc.indexOf('(');
        String name = nameAndDesc.substring(0, paren);
        String desc = nameAndDesc.substring(paren);
        ClassLoader loader = AccessorBridge.class.getClassLoader();

        ClassNode iface = new ClassNode();
        new ClassReader(readClass(loader, accessor)).accept(iface, ClassReader.SKIP_CODE);
        Object targetType = annotationValue(annotations(iface.visibleAnnotations, iface.invisibleAnnotations), MIXIN, "value");
        if (!(targetType instanceof List) || ((List<?>) targetType).isEmpty())
            throw new IllegalStateException(accessor + " has no @Mixin target");
        Class<?> target = load(((Type) ((List<?>) targetType).get(0)).getClassName(), loader);

        MethodNode method = null;
        for (MethodNode mn : iface.methods)
            if (mn.name.equals(name) && mn.desc.equals(desc))
                method = mn;
        if (method == null)
            throw new IllegalStateException("No " + accessor + "." + nameAndDesc);
        List<AnnotationNode> annotations = annotations(method.visibleAnnotations, method.invisibleAnnotations);

        Type[] argTypes = Type.getArgumentTypes(desc);
        Class<?>[] params = new Class<?>[argTypes.length];
        for (int i = 0; i < argTypes.length; i++)
            params[i] = classOf(argTypes[i], loader);

        if (hasAnnotation(annotations, INVOKER)) {
            String member = (String) annotationValue(annotations, INVOKER, "value");
            if (member == null || member.isEmpty())
                member = decapitalize(stripPrefix(name, "invoke", "call"));
            Access.MethodRef ref = findMethod(target, member, params);
            return ref::invoke;
        }

        String member = (String) annotationValue(annotations, ACCESSOR, "value");
        boolean setter = Type.getReturnType(desc).getSort() == Type.VOID && argTypes.length == 1;
        if (member == null || member.isEmpty()) {
            String bare = stripPrefix(name, "get", "is", "set");
            member = decapitalize(bare);
            Access.FieldRef ref = findField(target, member, bare);
            return setter ? (self, args) -> { ref.set(self, args[0]); return null; } : (self, args) -> ref.get(self);
        }
        Access.FieldRef ref = findField(target, member, member);
        return setter ? (self, args) -> { ref.set(self, args[0]); return null; } : (self, args) -> ref.get(self);
    }

    private static Access.FieldRef findField(Class<?> target, String name, String fallback) {
        try {
            return Access.field(target, name);
        } catch (IllegalStateException e) {
            return Access.field(target, fallback);
        }
    }

    private static Access.MethodRef findMethod(Class<?> target, String name, Class<?>[] params) {
        for (Class<?> c = target; c != null; c = c.getSuperclass()) {
            try {
                return Access.method(c, name, params);
            } catch (IllegalStateException ignored) {
            }
        }
        throw new IllegalStateException("No method " + target.getName() + "." + name);
    }

    private static String stripPrefix(String name, String... prefixes) {
        for (String prefix : prefixes)
            if (name.length() > prefix.length() && name.startsWith(prefix) && Character.isUpperCase(name.charAt(prefix.length())))
                return name.substring(prefix.length());
        return name;
    }

    private static String decapitalize(String name) {
        return name.isEmpty() ? name : Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    private static List<AnnotationNode> annotations(List<AnnotationNode> visible, List<AnnotationNode> invisible) {
        List<AnnotationNode> all = new ArrayList<>();
        if (visible != null)
            all.addAll(visible);
        if (invisible != null)
            all.addAll(invisible);
        return all;
    }

    private static boolean hasAnnotation(List<AnnotationNode> annotations, String desc) {
        for (AnnotationNode a : annotations)
            if (a.desc.equals(desc))
                return true;
        return false;
    }

    private static Object annotationValue(List<AnnotationNode> annotations, String desc, String key) {
        for (AnnotationNode a : annotations) {
            if (!a.desc.equals(desc) || a.values == null)
                continue;
            for (int i = 0; i + 1 < a.values.size(); i += 2)
                if (key.equals(a.values.get(i)))
                    return a.values.get(i + 1);
        }
        return null;
    }

    private static Class<?> classOf(Type type, ClassLoader loader) {
        switch (type.getSort()) {
            case Type.BOOLEAN: return boolean.class;
            case Type.BYTE: return byte.class;
            case Type.CHAR: return char.class;
            case Type.SHORT: return short.class;
            case Type.INT: return int.class;
            case Type.FLOAT: return float.class;
            case Type.LONG: return long.class;
            case Type.DOUBLE: return double.class;
            case Type.ARRAY: return load(type.getDescriptor().replace('/', '.'), loader);
            default: return load(type.getClassName(), loader);
        }
    }

    /** Loads a class named with SRG names (what the accessor interfaces are written in) under its runtime name. */
    private static Class<?> load(String name, ClassLoader loader) {
        try {
            return Class.forName(RuntimeNames.current().mapClass(name.replace('.', '/')).replace('/', '.'), false, loader);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] readClass(ClassLoader loader, String internalName) {
        try (InputStream in = loader.getResourceAsStream(internalName + ".class")) {
            if (in == null)
                throw new IllegalStateException("Missing " + internalName);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1)
                out.write(buf, 0, n);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
