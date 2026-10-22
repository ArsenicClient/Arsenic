package arsenic.asm;

import net.minecraft.launchwrapper.IClassTransformer;

public class ClassTransformer implements IClassTransformer {
    @Override
    public byte[] transform(String classname, String transformedName, byte[] basicClass) {
        if(!classname.contains("arsenic") || classname.contains("arsenic.asm.ClassTransformer"))
            return basicClass;
        return AnnotationTransformer.transform(basicClass);
    }
}
