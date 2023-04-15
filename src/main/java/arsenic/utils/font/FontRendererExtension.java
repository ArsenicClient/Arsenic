package arsenic.utils.font;

import arsenic.utils.functionalinterfaces.ITwoParamVoidFunction;
import arsenic.utils.interfaces.IFontRenderer;
import arsenic.utils.render.PosInfo;
import org.lwjgl.opengl.GL11;

public class FontRendererExtension<T extends IFontRenderer>{

    float scale = 1f;
    float scaleReciprocal = 1f;
    float tempScale = 1f;
    float tempScaleReciprocal = 1f;

    private IFontRenderer fontRenderer = null;

    public FontRendererExtension(T fontRenderer) {
        this.fontRenderer = fontRenderer;
    }
    public void resetScale() {
        setScale(1f);
    }
    public float getWidth(String text) {
        return fontRenderer.getWidth(text);
    }
    public float getHeight(String text) {return fontRenderer.getHeight(text);}

    public void scale(float scale) {
        setScale(this.scale * scale);
    }

    public void setScale(float scale) {
        this.scale = scale;
        scaleReciprocal = 1f/scale;
    }

    public ITwoParamVoidFunction<PosInfo, String> getScaleModifier(float scale) {
        return (posInfo, string) -> {
            this.tempScaleReciprocal = 1f/scale;
            this.tempScale = scale;
            posInfo.setX(posInfo.getX() * tempScaleReciprocal);
            posInfo.setY(posInfo.getY() * tempScaleReciprocal);
        };
    }

    public final ITwoParamVoidFunction<PosInfo, String> CENTREX = (posInfo, string) -> posInfo.moveX(- (fontRenderer.getWidth(string)/2f));
    public final ITwoParamVoidFunction<PosInfo, String> CENTREY = (posInfo, string) -> posInfo.moveY(- (fontRenderer.getHeight(string)/2f));
    private final ITwoParamVoidFunction<PosInfo, String> SCALE = (posInfo, string) -> {
        posInfo.setX(posInfo.getX() * scaleReciprocal);
        posInfo.setY(posInfo.getY() * scaleReciprocal);
    };

    public void drawString(String text, float x, float y, int color, ITwoParamVoidFunction<PosInfo, String> ... modifiers) {
        PosInfo posInfo = new PosInfo(x, y);
        setup(posInfo, text, modifiers);
        fontRenderer.drawString(text, posInfo.getX(), posInfo.getY(), color);
        finsh();
    }

    public void drawStringWithShadow(String text, float x, float y, int color, ITwoParamVoidFunction<PosInfo, String> ... modifiers) {
        PosInfo posInfo = new PosInfo(x, y);
        setup(posInfo, text, modifiers);
        fontRenderer.drawStringWithShadow(text, posInfo.getX(), posInfo.getY(), color);
        finsh();
    }

    //issues with y values btw
    public void drawWrappingString(String unSplitText, float x, float y, int color, ITwoParamVoidFunction<PosInfo, String> ... modifiers) {
        for(String text : unSplitText.split("\n")) {
            drawString(text, x, y, color, modifiers);
            y += fontRenderer.getHeight(text);
        }
    }

    private void setup(PosInfo posInfo, String text, ITwoParamVoidFunction<PosInfo, String> ... modifiers) {
        for(ITwoParamVoidFunction<PosInfo, String> modifier : modifiers) {
            modifier.function(posInfo, text);
        }
        float scale = tempScale * this.scale;
        if(scale != 1f) {
            SCALE.function(posInfo, text);
            GL11.glScalef(scale, scale, scale);
        }
    }

    private void finsh() {
        float scale = tempScale * this.scale;
        if(scale != 1f) {
            float scaleReciprocal = 1f/(tempScale * this.scale);
            GL11.glScalef(scaleReciprocal, scaleReciprocal, scaleReciprocal);
        }
        this.tempScale = 1f;
        this.tempScaleReciprocal = 1f;
    }
}
