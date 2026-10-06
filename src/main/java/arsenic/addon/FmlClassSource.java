package arsenic.addon;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.common.asm.transformers.AccessTransformer;
import net.minecraftforge.fml.common.asm.transformers.DeobfuscationTransformer;

import java.io.IOException;

/**
 * Production class source: Minecraft classes as the running game sees them. The game jar holds notch-named classes
 * that Forge's class loader renames to SRG and runs its deobfuscation and access transformers over. This repeats
 * exactly those steps (using the loader's own name transformer, so inner classes resolve too), because the compiler
 * needs the same member names and the same visibility the game ends up with. Other transformers (mixins, other
 * mods) are left out on purpose.
 */
public class FmlClassSource extends LoaderClassSource {

    public FmlClassSource() {
        super(Launch.classLoader);
    }

    @Override
    public byte[] getBytes(String internalName) {
        String name = internalName.replace('/', '.');
        String rawName = name;
        DeobfuscationTransformer renamer = null;
        for (IClassTransformer t : Launch.classLoader.getTransformers()) {
            if (t instanceof DeobfuscationTransformer) {
                renamer = (DeobfuscationTransformer) t;
                rawName = renamer.unmapClassName(name);
                break;
            }
        }

        boolean minecraft = !rawName.equals(name) || name.startsWith("net.minecraft.");
        if (!minecraft)
            return super.getBytes(internalName);

        byte[] bytes;
        try {
            bytes = Launch.classLoader.getClassBytes(rawName);
        } catch (IOException e) {
            bytes = null;
        }
        if (bytes == null)
            return super.getBytes(internalName);

        for (IClassTransformer t : Launch.classLoader.getTransformers())
            if (t instanceof DeobfuscationTransformer || t instanceof AccessTransformer)
                bytes = t.transform(rawName, name, bytes);
        return bytes;
    }
}
