package arsenic.addon;

import arsenic.runtime.RuntimeNames;

/**
 * Class source for an injected client in vanilla: the game's classes carry Mojang's obfuscated names, so each
 * Minecraft class is read under its obfuscated name and handed to the compiler with SRG names, the way Forge would
 * have renamed it ({@link FmlClassSource}). Everything else is read as it is.
 */
public class VanillaClassSource extends LoaderClassSource {

    private final ClassLoader loader;
    private final RuntimeNames names;

    public VanillaClassSource(ClassLoader loader, RuntimeNames names) {
        super(loader);
        this.loader = loader;
        this.names = names;
    }

    @Override
    public byte[] getBytes(String internalName) {
        String runtime = names.mapClass(internalName);
        if (runtime.equals(internalName) && !internalName.startsWith("net/minecraft/"))
            return super.getBytes(internalName);
        byte[] bytes = readResource(loader, runtime + ".class");
        return bytes == null ? null : names.toSrgDeclarations(bytes);
    }
}
