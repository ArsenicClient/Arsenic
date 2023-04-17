package arsenic.injection.mixin;

import net.minecraft.util.Session;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(Session.class)
public class MixinSessionTemp {

    //temp

    /**
     * @author kv
     * @reason temp username chaneg for testing
     */
    /*@Overwrite
    public String getUsername() {
        return "_sda_dascwcn";
    }*/
}
