package arsenic.runtime;

import arsenic.addon.MappingTable;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Reflective access to private Minecraft members, written with MCP names. Works in a development environment (MCP
 * names) and in the game (SRG names, looked up in the bundled /addon-mappings.txt). The hooks use this instead of
 * mixin shadows so the same code runs whether the client was loaded as a mod or injected into a running game.
 */
public final class Access {

    private static MappingTable table;

    private Access() {}

    private static synchronized MappingTable table() {
        if (table == null) {
            try (InputStream in = Access.class.getResourceAsStream("/addon-mappings.txt")) {
                table = in != null ? MappingTable.load(in) : MappingTable.load(new java.io.ByteArrayInputStream(new byte[0]));
            } catch (Exception e) {
                throw new IllegalStateException("Could not read addon-mappings.txt", e);
            }
        }
        return table;
    }

    private static String internal(Class<?> c) {
        return c.getName().replace('.', '/');
    }

    public static FieldRef field(Class<?> owner, String mcpName) {
        Field f;
        try {
            f = owner.getDeclaredField(mcpName);
        } catch (NoSuchFieldException e) {
            String srg = table().fieldToSrg(internal(owner), mcpName);
            try {
                f = owner.getDeclaredField(srg != null ? srg : mcpName);
            } catch (NoSuchFieldException e2) {
                throw new IllegalStateException("No field " + owner.getName() + "." + mcpName, e2);
            }
        }
        f.setAccessible(true);
        return new FieldRef(f);
    }

    public static MethodRef method(Class<?> owner, String mcpName, Class<?>... params) {
        for (Method m : owner.getDeclaredMethods()) {
            if (!Arrays.equals(m.getParameterTypes(), params))
                continue;
            if (m.getName().equals(mcpName) || mcpName.equals(table().methodToMcp(internal(owner), m.getName()))) {
                m.setAccessible(true);
                return new MethodRef(m);
            }
        }
        throw new IllegalStateException("No method " + owner.getName() + "." + mcpName + Arrays.toString(params));
    }

    public static final class FieldRef {
        private final Field field;

        private FieldRef(Field field) {
            this.field = field;
        }

        @SuppressWarnings("unchecked")
        public <T> T get(Object owner) {
            try {
                return (T) field.get(owner);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }

        public void set(Object owner, Object value) {
            try {
                field.set(owner, value);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }

        public int getInt(Object owner) {
            try {
                return field.getInt(owner);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }

        public void setInt(Object owner, int value) {
            try {
                field.setInt(owner, value);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }

        public float getFloat(Object owner) {
            try {
                return field.getFloat(owner);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }

        public void setBoolean(Object owner, boolean value) {
            try {
                field.setBoolean(owner, value);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    public static final class MethodRef {
        private final Method method;

        private MethodRef(Method method) {
            this.method = method;
        }

        @SuppressWarnings("unchecked")
        public <T> T invoke(Object owner, Object... args) {
            try {
                return (T) method.invoke(owner, args);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException)
                    throw (RuntimeException) cause;
                if (cause instanceof Error)
                    throw (Error) cause;
                throw new IllegalStateException(cause);
            }
        }
    }
}
