package dev.kino.injection.mixin;

import dev.kino.utils.interfaces.IFontRenderer;
import net.minecraft.client.gui.FontRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(priority = 1111, value = FontRenderer.class)
public abstract class MixinFontRenderer implements IFontRenderer {

    @Shadow public int FONT_HEIGHT;

    @Shadow public abstract int getStringWidth(String p_getStringWidth_1_);

    @Shadow public abstract int drawString(String p_drawString_1_, float p_drawString_2_, float p_drawString_3_, int p_drawString_4_, boolean p_drawString_5_);

    @Override
    public int drawString(String text, float x, float y, int color) {
        return this.drawString(text, x, y, color, false);
    }

    @Override
    public int drawStringWithShadow(String text, float x, float y, int color) {
        return this.drawString(text, x, y, color, true);
    }

    @Override
    public float getWidth(String text) {
        return getStringWidth(text);
    }

    @Override
    public float getHeight(String text) {
        return FONT_HEIGHT;
    }

}
