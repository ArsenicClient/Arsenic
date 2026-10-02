package arsenic.injection.mixin;

import arsenic.module.impl.player.NameHider;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.util.StringDecomposer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * NameHider's hook. Every piece of literal text Minecraft draws is split into glyphs here, so this
 * is the modern equivalent of rewriting the string in 1.8's FontRenderer#renderString.
 */
@Mixin(StringDecomposer.class)
public class MixinStringDecomposer {

    @ModifyVariable(method = "iterateFormatted(Ljava/lang/String;ILnet/minecraft/network/chat/Style;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static String arsenic$nameHider(String text) {
        return NameHider.format(text);
    }
}
