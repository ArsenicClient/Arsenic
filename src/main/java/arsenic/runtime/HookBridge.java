package arsenic.runtime;

import net.minecraft.launchwrapper.IClassTransformer;

import java.util.function.BiFunction;

/**
 * Forge's class loader calls this for every class it loads. The hooks themselves live on the system class loader, so
 * {@code arsenic.inject.JuiceEntry} sets {@link #hook} to them (names with slashes, bytes in, hooked bytes out).
 * Registered by name with the game's class loader, the same way as {@link AccessorTransformer}.
 */
public final class HookBridge implements IClassTransformer {

    public static volatile BiFunction<String, byte[], byte[]> hook;

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        BiFunction<String, byte[], byte[]> current = hook;
        if (current == null || basicClass == null || transformedName == null)
            return basicClass;
        return current.apply(transformedName.replace('.', '/'), basicClass);
    }
}
