package arsenic.injection.mixin;

import arsenic.main.Arsenic;
import arsenic.module.impl.player.NameHider;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.interfaces.IFontRenderer;
import net.minecraft.client.gui.FontRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import static arsenic.module.impl.player.NameHider.format;

@Mixin(priority = 1111, value = FontRenderer.class)
public abstract class MixinFontRenderer implements IFontRenderer {

    private final FontRendererExtension<MixinFontRenderer> fontRendererExtension = new FontRendererExtension<>(this);

    @Override
    public FontRendererExtension<?> getFontRendererExtension() {
        return fontRendererExtension;
    }

    @Shadow
    public int FONT_HEIGHT;

    @Shadow
    public abstract int getStringWidth(String p_getStringWidth_1_);
    @Shadow
    public abstract int drawString(String p_drawString_1_, float p_drawString_2_, float p_drawString_3_,
            int p_drawString_4_, boolean p_drawString_5_);

    @ModifyVariable(method = "getStringWidth", at = @At("HEAD"), argsOnly = true)
    private String modifyGetStringWidthArg(String p_getStringWidth_1_) {
        return format(p_getStringWidth_1_);
    }

    @ModifyVariable(method = "drawString*", at = @At("HEAD"), argsOnly = true)
    private String modifyDrawStringArg(String p_drawString_1_) {
        return format(p_drawString_1_);
    }

    @Override
    public void drawString(String text, float x, float y, int color) {
        this.drawString(format(text), x, y, color, false);
    }

    @Override
    public void drawStringWithShadow(String text, float x, float y, int color) {
        this.drawString(format(text), x, y, color, true);
    }

    @Override
    public float getWidth(String text) {
        return getStringWidth(format(text));
    }

    @Override
    public float getHeight(String text) {
        return FONT_HEIGHT;
    }

}
