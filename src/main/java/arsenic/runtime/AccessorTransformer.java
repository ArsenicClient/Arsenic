package arsenic.runtime;

import net.minecraft.launchwrapper.IClassTransformer;

/** {@link AccessorRewriter} as a transformer of Forge's class loader, registered by the injector's agent. */
public final class AccessorTransformer implements IClassTransformer {

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !AccessorRewriter.rewrites(transformedName.replace('.', '/')))
            return basicClass;
        return AccessorRewriter.rewrite(basicClass);
    }
}
