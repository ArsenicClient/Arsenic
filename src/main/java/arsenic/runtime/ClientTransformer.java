package arsenic.runtime;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.function.Consumer;

/**
 * Prepares the client's classes as the game's class loader loads them, for games that are not Forge: accessor calls
 * are rewritten ({@link AccessorRewriter}, as Forge's loader does with {@link AccessorTransformer}) and Minecraft
 * names are renamed from SRG to the game's names ({@link RuntimeNames#toRuntime}).
 *
 * Registered by the injector's agent; runs on the system class loader, so it must not touch Minecraft classes.
 */
public final class ClientTransformer implements ClassFileTransformer {

    private final ClassLoader gameLoader;
    private final RuntimeNames names;
    private final Consumer<String> log;

    public ClientTransformer(ClassLoader gameLoader, RuntimeNames names, Consumer<String> log) {
        this.gameLoader = gameLoader;
        this.names = names;
        this.log = log;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (loader != gameLoader || classBeingRedefined != null || className == null
                || !className.startsWith("arsenic/") || className.startsWith("arsenic/inject/") || className.startsWith("arsenic/lib/"))
            return null;
        try {
            byte[] bytes = classfileBuffer;
            if (AccessorRewriter.rewrites(className))
                bytes = AccessorRewriter.rewrite(bytes);
            bytes = names.toRuntime(bytes);
            return bytes == classfileBuffer ? null : bytes;
        } catch (Throwable t) {
            log.accept("Could not prepare " + className + ": " + t);
            return null;
        }
    }
}
