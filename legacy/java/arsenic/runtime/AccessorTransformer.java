package arsenic.runtime;

import net.minecraft.launchwrapper.IClassTransformer;

/** {@link AccessorRewriter} as a transformer of Forge's class loader, registered by the injector's agent. */
public final class AccessorTransformer implements IClassTransformer {

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        // Decided on the name alone: AccessorRewriter is one of the classes this loader is loading, so asking it here
        // would load it again while it is being transformed (a ClassCircularityError). Same rules as AccessorRewriter.rewrites.
        String internal = transformedName.replace('.', '/');
        if (basicClass == null || !internal.startsWith("arsenic/") || internal.startsWith("arsenic/injection/")
                || internal.startsWith("arsenic/runtime/") || internal.startsWith("arsenic/lib/"))
            return basicClass;
        return AccessorRewriter.rewrite(basicClass);
    }
}
