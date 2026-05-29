package arsenic.gui.themes;

import arsenic.utils.interfaces.ISerializable;
import arsenic.utils.render.RenderUtils;
import com.google.gson.JsonObject;
import net.minecraft.util.ResourceLocation;

import java.awt.*;

public class Theme implements ISerializable {

    private final String name;
    private String logoName = "arsenic";
    private ResourceLocation logoPath = RenderUtils.getResourcePath("/assets/arsenic/logos/" + logoName + "logo.png");
    private ResourceLocation altLogoPath = RenderUtils.getResourcePath("/assets/arsenic/logos/" + logoName + "logo_alt.png");
    private int mainColor, darkerColor, white, black, gradientColor;
    private int clickGuiBackground = 0xDD0C0C0C;
    private int clickGuiSeparator = new Color(0, 0, 0, 68).getRGB();
    private int moduleBackground = new Color(5, 5, 5, 160).getRGB();
    private int moduleHover = new Color(255, 255, 255, 15).getRGB();
    private int scrollbarTrack = new Color(255, 255, 255, 20).getRGB();
    private int scrollbarThumb = new Color(255, 255, 255, 80).getRGB();
    private int buttonBackground = new Color(33, 33, 33, 234).getRGB();
    private int buttonCircleShadow = new Color(0, 0, 0, 60).getRGB();
    private int buttonCircleHighlight = new Color(255, 255, 255, 30).getRGB();
    private int configsBackground = 0xFF0D0D0F;
    private int configsCard = 0xFF111114;
    private int configsCardBorder = 0xFF1E1E24;
    private int separator = new Color(26, 26, 31).getRGB();
    private int configsHoverBackground = 0xFF141418;
    private int configsHoverBorder = 0xFF2A2A35;
    private int enumBackground = new Color(26, 25, 25, 205).getRGB();
    private int folderBackground = new Color(26, 25, 25, 150).getRGB();
    private int textPrimary = 0xFFDDDDDD;
    private int textSecondary = 0xFF888888;
    private int textMuted = 0xFF444444;
    private int error = 0xFFE24B4A;
    private int status = 0xFFFFFFAA;
    private boolean gradient;

    public Theme(String name, int mainColor, int darkerColor, int white, int black) {
        this.name = name;
        this.mainColor = mainColor;
        this.darkerColor = darkerColor;
        this.white = white;
        this.black = black;
    }

    public Theme(String name, int mainColor, int darkerColor, int white, int black, boolean gradient, int gradientColor) {
        this(name, mainColor, darkerColor, white, black);
        this.gradient = gradient;
        this.gradientColor = gradientColor;
    }

    public String getName(){
        return name;
    }
    public int getMainColor() {
        return mainColor;
    }
    public boolean isGradientEnabled(){
        return gradient;
    }
    public int getGradientColor() {
        return isGradientEnabled() ? gradientColor : getMainColor();
    }
    public void setMainColor(int mainColor) {
        this.mainColor = mainColor;
    }

    public int getDarkerColor() {
        return darkerColor;
    }

    public void setDarkerColor(int darkerColor) {
        this.darkerColor = darkerColor;
    }

    public int getWhite() {
        return white;
    }

    public void setWhite(int white) {
        this.white = white;
    }

    public int getBlack() {
        return black;
    }

    public void setBlack(int black) {
        this.black = black;
    }

    public int getClickGuiBackground() { return clickGuiBackground; }
    public void setClickGuiBackground(int clickGuiBackground) { this.clickGuiBackground = clickGuiBackground; }

    public int getClickGuiSeparator() { return clickGuiSeparator; }
    public void setClickGuiSeparator(int clickGuiSeparator) { this.clickGuiSeparator = clickGuiSeparator; }

    public int getModuleBackground() { return moduleBackground; }
    public void setModuleBackground(int moduleBackground) { this.moduleBackground = moduleBackground; }

    public int getModuleHover() { return moduleHover; }
    public void setModuleHover(int moduleHover) { this.moduleHover = moduleHover; }

    public int getScrollbarTrack() { return scrollbarTrack; }
    public void setScrollbarTrack(int scrollbarTrack) { this.scrollbarTrack = scrollbarTrack; }

    public int getScrollbarThumb() { return scrollbarThumb; }
    public void setScrollbarThumb(int scrollbarThumb) { this.scrollbarThumb = scrollbarThumb; }

    public int getButtonBackground() { return buttonBackground; }
    public void setButtonBackground(int buttonBackground) { this.buttonBackground = buttonBackground; }

    public int getButtonCircleShadow() { return buttonCircleShadow; }
    public void setButtonCircleShadow(int buttonCircleShadow) { this.buttonCircleShadow = buttonCircleShadow; }

    public int getButtonCircleHighlight() { return buttonCircleHighlight; }
    public void setButtonCircleHighlight(int buttonCircleHighlight) { this.buttonCircleHighlight = buttonCircleHighlight; }

    public int getConfigsBackground() { return configsBackground; }
    public void setConfigsBackground(int configsBackground) { this.configsBackground = configsBackground; }

    public int getConfigsCard() { return configsCard; }
    public void setConfigsCard(int configsCard) { this.configsCard = configsCard; }

    public int getConfigsCardBorder() { return configsCardBorder; }
    public void setConfigsCardBorder(int configsCardBorder) { this.configsCardBorder = configsCardBorder; }

    public int getSeparator() { return separator; }
    public void setSeparator(int separator) { this.separator = separator; }

    public int getConfigsHoverBackground() { return configsHoverBackground; }
    public void setConfigsHoverBackground(int configsHoverBackground) { this.configsHoverBackground = configsHoverBackground; }

    public int getConfigsHoverBorder() { return configsHoverBorder; }
    public void setConfigsHoverBorder(int configsHoverBorder) { this.configsHoverBorder = configsHoverBorder; }

    public int getEnumBackground() { return enumBackground; }
    public void setEnumBackground(int enumBackground) { this.enumBackground = enumBackground; }

    public int getFolderBackground() { return folderBackground; }
    public void setFolderBackground(int folderBackground) { this.folderBackground = folderBackground; }

    public int getTextPrimary() { return textPrimary; }
    public void setTextPrimary(int textPrimary) { this.textPrimary = textPrimary; }

    public int getTextSecondary() { return textSecondary; }
    public void setTextSecondary(int textSecondary) { this.textSecondary = textSecondary; }

    public int getTextMuted() { return textMuted; }
    public void setTextMuted(int textMuted) { this.textMuted = textMuted; }

    public int getError() { return error; }
    public void setError(int error) { this.error = error; }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public ResourceLocation getLogoPath() {
        return logoPath;
    }

    public ResourceLocation getAltLogoPath() {
        return altLogoPath;
    }

    public void setLogoPath(String logoName) {
        logoName = logoName.toLowerCase();
        this.logoName = logoName;
        logoPath = RenderUtils.getResourcePath("/assets/arsenic/logos/" + logoName + "logo.png");
        altLogoPath = RenderUtils.getResourcePath("/assets/arsenic/logos/" + logoName + "logo.png");
    }


    //configs could be done with reflection
    // but this would look strange with obfuscation and honestly not much of a point
    @Override
    public void loadFromJson(JsonObject obj) {
        setLogoPath(obj.get("resourcelocation").getAsString());
        white = obj.get("white").getAsInt();
        black = obj.get("black").getAsInt();
        darkerColor = obj.get("dark").getAsInt();
        mainColor = obj.get("main").getAsInt();
        if(obj.has("clickGuiBackground")) clickGuiBackground = obj.get("clickGuiBackground").getAsInt();
        if(obj.has("clickGuiSeparator")) clickGuiSeparator = obj.get("clickGuiSeparator").getAsInt();
        if(obj.has("moduleBackground")) moduleBackground = obj.get("moduleBackground").getAsInt();
        if(obj.has("moduleHover")) moduleHover = obj.get("moduleHover").getAsInt();
        if(obj.has("scrollbarTrack")) scrollbarTrack = obj.get("scrollbarTrack").getAsInt();
        if(obj.has("scrollbarThumb")) scrollbarThumb = obj.get("scrollbarThumb").getAsInt();
        if(obj.has("buttonBackground")) buttonBackground = obj.get("buttonBackground").getAsInt();
        if(obj.has("buttonCircleShadow")) buttonCircleShadow = obj.get("buttonCircleShadow").getAsInt();
        if(obj.has("buttonCircleHighlight")) buttonCircleHighlight = obj.get("buttonCircleHighlight").getAsInt();
        if(obj.has("configsBackground")) configsBackground = obj.get("configsBackground").getAsInt();
        if(obj.has("configsCard")) configsCard = obj.get("configsCard").getAsInt();
        if(obj.has("configsCardBorder")) configsCardBorder = obj.get("configsCardBorder").getAsInt();
        if(obj.has("separator")) separator = obj.get("separator").getAsInt();
        if(obj.has("configsHoverBackground")) configsHoverBackground = obj.get("configsHoverBackground").getAsInt();
        if(obj.has("configsHoverBorder")) configsHoverBorder = obj.get("configsHoverBorder").getAsInt();
        if(obj.has("enumBackground")) enumBackground = obj.get("enumBackground").getAsInt();
        if(obj.has("folderBackground")) folderBackground = obj.get("folderBackground").getAsInt();
        if(obj.has("textPrimary")) textPrimary = obj.get("textPrimary").getAsInt();
        if(obj.has("textSecondary")) textSecondary = obj.get("textSecondary").getAsInt();
        if(obj.has("textMuted")) textMuted = obj.get("textMuted").getAsInt();
        if(obj.has("error")) error = obj.get("error").getAsInt();
        if(obj.has("status")) status = obj.get("status").getAsInt();
    }

    @Override
    public JsonObject saveInfoToJson(JsonObject obj) {
        obj.addProperty("resourcelocation", logoName);
        obj.addProperty("white", getWhite());
        obj.addProperty("black", getBlack());
        obj.addProperty("dark", getDarkerColor());
        obj.addProperty("main", getMainColor());
        obj.addProperty("clickGuiBackground", clickGuiBackground);
        obj.addProperty("clickGuiSeparator", clickGuiSeparator);
        obj.addProperty("moduleBackground", moduleBackground);
        obj.addProperty("moduleHover", moduleHover);
        obj.addProperty("scrollbarTrack", scrollbarTrack);
        obj.addProperty("scrollbarThumb", scrollbarThumb);
        obj.addProperty("buttonBackground", buttonBackground);
        obj.addProperty("buttonCircleShadow", buttonCircleShadow);
        obj.addProperty("buttonCircleHighlight", buttonCircleHighlight);
        obj.addProperty("configsBackground", configsBackground);
        obj.addProperty("configsCard", configsCard);
        obj.addProperty("configsCardBorder", configsCardBorder);
        obj.addProperty("separator", separator);
        obj.addProperty("configsHoverBackground", configsHoverBackground);
        obj.addProperty("configsHoverBorder", configsHoverBorder);
        obj.addProperty("enumBackground", enumBackground);
        obj.addProperty("folderBackground", folderBackground);
        obj.addProperty("textPrimary", textPrimary);
        obj.addProperty("textSecondary", textSecondary);
        obj.addProperty("textMuted", textMuted);
        obj.addProperty("error", error);
        obj.addProperty("status", status);
        return obj;
    }

    @Override
    public String getJsonKey() {
        return name;
    }
}
