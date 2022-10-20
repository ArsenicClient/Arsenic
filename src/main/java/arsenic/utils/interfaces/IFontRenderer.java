package arsenic.utils.interfaces;

public interface IFontRenderer {

    void drawString(String text, float x, float y, int color);

    void drawStringWithShadow(String text, float x, float y, int color);

    default void drawStringWithOutline(String text, float x, float y, int color) {
        this.drawString(text, x - 0.5f, y, 0xFF000000);
        this.drawString(text, x + 0.5f, y, 0xFF000000);
        this.drawString(text, x, y - 0.5f, 0xFF000000);
        this.drawString(text, x, y + 0.5f, 0xFF000000);
        this.drawString(text, x, y, color);
    }

    float getWidth(String text);

    default float getHeight(String text) {
        return 11.0F;
    };

}
