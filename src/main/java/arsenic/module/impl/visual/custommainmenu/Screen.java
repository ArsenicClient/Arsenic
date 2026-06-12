package arsenic.module.impl.visual.custommainmenu;

import arsenic.main.Arsenic;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.interfaces.IFontRenderer;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.shader.ShaderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;

import java.awt.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class Screen extends GuiScreen {

    private ShaderUtil backgroundShader;
    private int currentShaderIndex;
    private final List<String> backgroundShaders = Arrays.asList("aurora", "zippyZaps", "wave", "rainbowShader", "kvShader");

    public Screen() {
        nextShader();
    }

    private void nextShader() {
        currentShaderIndex = (currentShaderIndex + 1) % backgroundShaders.size();
        backgroundShader = new ShaderUtil(backgroundShaders.get(currentShaderIndex));
    }

    @Override
    public void initGui() {
        int centerX = this.width / 2;
        int startY = this.height / 2 + 10;
        int spacing = 28;
        int btnW = 90;
        int gap = 10;

        int leftCol = centerX - gap / 2 - btnW;
        int rightCol = centerX + gap / 2;

        this.buttonList.add(new CustomGuiButton(1, leftCol, startY, btnW, 20, I18n.format("menu.singleplayer")));
        this.buttonList.add(new CustomGuiButton(2, rightCol, startY, btnW, 20, I18n.format("menu.multiplayer")));
        this.buttonList.add(new CustomGuiButton(3, leftCol, startY + spacing, btnW, 20, "Mods"));
        this.buttonList.add(new CustomGuiButton(0, rightCol, startY + spacing, btnW, 20, I18n.format("menu.options")));
        this.buttonList.add(new CustomGuiButton(4, leftCol, startY + spacing * 2, btnW, 20, I18n.format("menu.quit")));
        this.buttonList.add(new CustomGuiButton(6, rightCol, startY + spacing * 2, btnW, 20, "Changelog"));
        this.buttonList.add(new CustomGuiButton(5, this.width - 100, 5, 95, 20, "Next Shader"));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        Gui.drawRect(0, 0, 0, 0, new Color(255, 255, 255, 40).getRGB());

        backgroundShader.init();
        backgroundShader.setUniformf("time", (System.currentTimeMillis() % 1000000) / 5000f);
        ScaledResolution sr = new ScaledResolution(mc);
        backgroundShader.setUniformf("resolution", this.width * sr.getScaleFactor(), this.height * sr.getScaleFactor());
        ShaderUtil.drawQuads();
        backgroundShader.unload();

        FontRendererExtension<?> fr = ((IFontRenderer) mc.fontRendererObj).getFontRendererExtension();

        String title = "Arsenic";
        float scale = 6.5f;
        fr.setScale(scale);
        float x = this.width / 2f;
        float y = this.height / 3f - 20;

        fr.drawStringWithShadow(title, x + 1.5f, y + 1.5f, new Color(0, 0, 0, 80).getRGB(), fr.CENTREY, fr.CENTREX);
        fr.drawStringWithShadow(title, x, y, -1, fr.CENTREY, fr.CENTREX);
        fr.resetScale();

        String subtitle = "Arsenic Client - Arsenic Team";
        mc.fontRendererObj.drawStringWithShadow(subtitle, x - mc.fontRendererObj.getStringWidth(subtitle) / 2f, y + 56, new Color(255, 255, 255, 120).getRGB());

        String shaderName = backgroundShaders.get(currentShaderIndex);
        mc.fontRendererObj.drawStringWithShadow(shaderName, this.width - mc.fontRendererObj.getStringWidth(shaderName) - 2, this.height - 10, new Color(255, 255, 255, 80).getRGB());

        String modCount = "Mods: " + net.minecraftforge.fml.common.Loader.instance().getModList().size();
        mc.fontRendererObj.drawStringWithShadow(modCount, 2, this.height - mc.fontRendererObj.FONT_HEIGHT - 2, new Color(255, 255, 255, 100).getRGB());

        String info = Arsenic.getArsenic().getModuleManager().getModules().size() + " modules";
        mc.fontRendererObj.drawStringWithShadow(info, 2, 2, new Color(255, 255, 255, 80).getRGB());

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case 0:
                this.mc.displayGuiScreen(new GuiOptions(this, this.mc.gameSettings));
                break;
            case 1:
                this.mc.displayGuiScreen(new GuiSelectWorld(this));
                break;
            case 2:
                this.mc.displayGuiScreen(new GuiMultiplayer(this));
                break;
            case 3:
                this.mc.displayGuiScreen(new net.minecraftforge.fml.client.GuiModList(this));
                break;
            case 4:
                this.mc.shutdown();
                break;
            case 5:
                nextShader();
                break;
            case 6:
                this.mc.displayGuiScreen(new GuiChangelog(this));
                break;
        }
    }

    public static class CustomGuiButton extends GuiButton {

        private long hoverStartTime;

        public CustomGuiButton(int buttonId, int x, int y, int widthIn, int heightIn, String buttonText) {
            super(buttonId, x, y, widthIn, heightIn, buttonText);
            hoverStartTime = System.currentTimeMillis();
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY) {
            this.hovered = mouseX >= this.xPosition && mouseY >= this.yPosition
                    && mouseX < this.xPosition + this.width && mouseY < this.yPosition + this.height;

            float hoverAlpha = 0f;
            if (this.hovered) {
                hoverAlpha = Math.min(1f, (System.currentTimeMillis() - hoverStartTime) / 200f);
            } else {
                hoverStartTime = System.currentTimeMillis();
            }

            int bgColor = new Color(255, 255, 255, this.hovered ? 50 : 25).getRGB();
            int borderColor = this.hovered
                    ? new Color(255, 255, 255, (int)(80 + hoverAlpha * 60)).getRGB()
                    : new Color(255, 255, 255, 20).getRGB();

            int radius = 4;
            DrawUtils.drawRoundedRect(xPosition, yPosition, xPosition + width, yPosition + height, radius, bgColor);
            DrawUtils.drawRoundedOutline(xPosition, yPosition, xPosition + width, yPosition + height, radius, 1f, borderColor);

            this.mouseDragged(mc, mouseX, mouseY);

            FontRendererExtension<?> fr = ((IFontRenderer) mc.fontRendererObj).getFontRendererExtension();
            int textColor = this.hovered
                    ? new Color(255, 255, 255, (int)(200 + hoverAlpha * 55)).getRGB()
                    : new Color(255, 255, 255, 150).getRGB();
            fr.drawStringWithShadow(displayString, xPosition + width / 2f, yPosition + height / 2f, textColor, fr.CENTREX, fr.CENTREY);
        }
    }
}
