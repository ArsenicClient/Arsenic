package arsenic.gui.click.impl;

import arsenic.config.ConfigManager;
import arsenic.gui.click.ClickGuiScreen;
import arsenic.gui.click.Component;
import arsenic.main.Arsenic;
import arsenic.module.ModuleCategory;
import arsenic.utils.interfaces.IAlwaysKeyboardInput;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.PosInfo;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.render.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ConfigsComponent extends ModuleCategoryComponent implements IAlwaysKeyboardInput {

    private final ConfigManager configManager;
    private final StringBuilder newConfigName = new StringBuilder();
    private boolean isNaming;
    private float scroll;
    private float inputBoxX, inputBoxY, inputBoxX2, inputBoxY2;
    private String currentConfigName = "";

    private final java.util.List<ConfigButton> buttons = new ArrayList<>();

    private static class ConfigButton {
        float x, y, x2, y2;
        final String label;
        final Runnable action;

        ConfigButton(String label, Runnable action) {
            this.label = label;
            this.action = action;
        }

        boolean isMouseOver(float mx, float my) {
            return mx >= x && mx <= x2 && my >= y && my <= y2;
        }
    }

    private float hoveredRowY = -1;

    public ConfigsComponent() {
        super(ModuleCategory.CONFIGS);
        this.configManager = Arsenic.getArsenic().getConfigManager();
    }

    @Override
    protected float drawComponent(RenderInfo ri) {
        float anim = Math.max(enabledTimer.getPercent(), hoverTimer.getPercent());
        expandX = anim * (width/14f);
        int mainC = ColorUtils.setColor(getEnabledColor(), 0, (int) (anim * 255));
        int gradientC = ColorUtils.setColor(getGradientColor(), 0, (int) (anim * 255));
        RenderUtils.resetColor();
        RenderUtils.resetColorText();
        DrawUtils.drawGradientRoundedRect(x1 + expandX, y1, x2 + expandX, y2, height/4f, mainC,mainC,gradientC,gradientC);

        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

        float iconSize = ri.getFr().getHeight("|") * (ri.getGuiScreen().height / 300f);
        float iconX = x1 + (width / 7f) + expandX - iconSize;
        float iconY = midPointY - iconSize / 2f;
        Minecraft.getMinecraft().getTextureManager().bindTexture(icon);
        Gui.drawModalRectWithCustomSizedTexture((int) iconX, (int) iconY, 0, 0, (int) iconSize, (int) iconSize, (int) iconSize, (int) iconSize);

        ri.getFr().drawString(getName(), iconX + iconSize + 2 , midPointY, getWhite(), ri.getFr().CENTREY);
        return height;
    }

    @Override
    public void drawLeft(PosInfo pi, RenderInfo ri) {
        buttons.clear();
        float x = pi.getX();
        float y = pi.getY() + scroll + 10;
        float maxX = ri.getGuiScreen().width * 7 / 8f - 10;
        float panelWidth = maxX - x;
        float mx = ri.getMouseX();

        currentConfigName = configManager.getCurrentConfig() != null ? configManager.getCurrentConfig().getName() : "";

        ri.getFr().drawString("Config Manager", x + 5, y, getEnabledColor());
        y += 20;

        DrawUtils.drawRoundedRect(x + 5, y - 2, maxX, y + 1, 1, new Color(getEnabledColor()).darker().getRGB());
        y += 10;

        ri.getFr().drawString("Saved Configs", x + 5, y, getEnabledColor());
        y += 15;

        for (String name : configManager.getConfigList().stream().sorted().collect(Collectors.toList())) {
            boolean isActive = name.equals(currentConfigName);
            boolean isHovered = mx >= x + 5 && mx <= maxX && ri.getMouseY() >= y && ri.getMouseY() <= y + 22;

            int bgColor = isActive
                    ? new Color(15, 15, 15, 220).getRGB()
                    : new Color(5, 5, 5, 120).getRGB();
            DrawUtils.drawRoundedRect(x + 5, y, maxX, y + 22, 4, bgColor);

            if (isHovered) {
                DrawUtils.drawRoundedRect(x + 5, y, maxX, y + 22, 4, new Color(255, 255, 255, 12).getRGB());
            }

            if (isActive) {
                DrawUtils.drawRoundedRect(x + 5, y, x + 7, y + 22, 2, getEnabledColor());
            }

            int textColor = isActive ? getEnabledColor() : getWhite();
            ri.getFr().drawString(name, x + 14, y + 11, textColor, ri.getFr().CENTREY);

            float btnY = y + 4;
            float btnH = 14;

            float loadX = maxX - 80;
            float loadW = 38;
            addButton(loadX, btnY, loadX + loadW, btnY + btnH, "Load", () -> {
                configManager.loadConfig(name);
                Arsenic.getArsenic().getConfigManager().reloadConfigs();
            });
            int loadColor = isActive
                    ? RenderUtils.interpolateColoursInt(getDisabledColor(), getEnabledColor(), 0.8f)
                    : RenderUtils.interpolateColoursInt(getDisabledColor(), getEnabledColor(), 0.6f);
            DrawUtils.drawRoundedRect(loadX, btnY, loadX + loadW, btnY + btnH, 3, loadColor);
            ri.getFr().drawString("Load", loadX + loadW / 2f, btnY + btnH / 2f, 0xFFFFFFFF, ri.getFr().CENTREX, ri.getFr().CENTREY);

            float delX = maxX - 38;
            float delW = 33;
            addButton(delX, btnY, delX + delW, btnY + btnH, "Del", () -> {
                configManager.deleteConfig(name);
                configManager.reloadConfigs();
            });
            DrawUtils.drawRoundedRect(delX, btnY, delX + delW, btnY + btnH, 3, new Color(180, 40, 40, 200).getRGB());
            ri.getFr().drawString("Del", delX + delW / 2f, btnY + btnH / 2f, 0xFFFFFFFF, ri.getFr().CENTREX, ri.getFr().CENTREY);

            y += 26;
        }

        y += 12;

        DrawUtils.drawRoundedRect(x + 5, y - 2, maxX, y + 1, 1, new Color(getEnabledColor()).darker().getRGB());
        y += 10;

        ri.getFr().drawString("Save Current", x + 5, y, getEnabledColor());
        y += 15;

        float inputX = x + 5;
        float inputY = y;
        float inputW = panelWidth * 0.55f;
        float inputH = 18;
        inputBoxX = inputX;
        inputBoxY = inputY;
        inputBoxX2 = inputX + inputW;
        inputBoxY2 = inputY + inputH;
        int inputBorder = isNaming ? getEnabledColor() : new Color(100, 100, 100, 150).getRGB();
        int inputBg = isNaming ? new Color(15, 15, 15, 200).getRGB() : new Color(10, 10, 10, 180).getRGB();
        DrawUtils.drawRoundedRect(inputX, inputY, inputX + inputW, inputY + inputH, 4, inputBg);
        DrawUtils.drawRoundedOutline(inputX, inputY, inputX + inputW, inputY + inputH, 4, 1, inputBorder);

        boolean showCursor = isNaming && (System.currentTimeMillis() % 1000 < 500);

        String displayText = newConfigName.length() == 0 ? "New config name..." : newConfigName.toString();
        float textX = inputX + 4;
        float textY = inputY + inputH / 2f;
        int displayColor = newConfigName.length() == 0 ? new Color(100, 100, 100, 150).getRGB() : getWhite();
        ri.getFr().drawString(displayText, textX, textY, displayColor, ri.getFr().CENTREY);

        if (showCursor) {
            float cursorX = textX + ri.getFr().getWidth(newConfigName.toString()) + 1;
            DrawUtils.drawRect(cursorX, inputY + 3, cursorX + 1, inputY + inputH - 3, getEnabledColor());
        }

        float saveX = inputX + inputW + 5;
        float saveW = 50;
        addButton(saveX, inputY, saveX + saveW, inputY + inputH, "Save", () -> {
            String name = newConfigName.toString().trim();
            if (!name.isEmpty()) {
                configManager.createConfig(name);
                configManager.saveConfig();
                configManager.reloadConfigs();
                newConfigName.setLength(0);
                isNaming = false;
            }
        });
        DrawUtils.drawRoundedRect(saveX, inputY, saveX + saveW, inputY + inputH, 3, getEnabledColor());
        ri.getFr().drawString("Save", saveX + saveW / 2f, inputY + inputH / 2f, 0xFFFFFFFF, ri.getFr().CENTREX, ri.getFr().CENTREY);

        y += 25;

        float btnW = panelWidth * 0.45f;
        float btnH2 = 18;

        float impX = x + 5;
        addButton(impX, y, impX + btnW, y + btnH2, "Import", () -> importFromClipboard());
        DrawUtils.drawRoundedRect(impX, y, impX + btnW, y + btnH2, 4, new Color(40, 40, 40, 200).getRGB());
        DrawUtils.drawRoundedOutline(impX, y, impX + btnW, y + btnH2, 4, 1, new Color(80, 80, 80, 100).getRGB());
        ri.getFr().drawString("Import from Clipboard", impX + btnW / 2f, y + btnH2 / 2f, getWhite(), ri.getFr().CENTREX, ri.getFr().CENTREY);

        float expX = x + btnW + 10;
        addButton(expX, y, expX + btnW, y + btnH2, "Export", () -> exportToClipboard());
        DrawUtils.drawRoundedRect(expX, y, expX + btnW, y + btnH2, 4, new Color(40, 40, 40, 200).getRGB());
        DrawUtils.drawRoundedOutline(expX, y, expX + btnW, y + btnH2, 4, 1, new Color(80, 80, 80, 100).getRGB());
        ri.getFr().drawString("Export to Clipboard", expX + btnW / 2f, y + btnH2 / 2f, getWhite(), ri.getFr().CENTREX, ri.getFr().CENTREY);

        y += btnH2 + 10;
    }

    @Override
    public void drawRight(PosInfo pi, RenderInfo ri) {
    }

    @Override
    protected void clickComponent(int mouseX, int mouseY, int mouseButton) {
        super.clickComponent(mouseX, mouseY, mouseButton);
    }

    @Override
    public void clickChildren(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) return;

        if (mouseX >= inputBoxX && mouseX <= inputBoxX2 && mouseY >= inputBoxY && mouseY <= inputBoxY2) {
            isNaming = true;
            Arsenic.getArsenic().getClickGuiScreen().setAlwaysInputComponent(this);
            return;
        }

        for (ConfigButton btn : buttons) {
            if (btn.isMouseOver(mouseX, mouseY)) {
                btn.action.run();
                return;
            }
        }
    }

    @Override
    public void setNotAlwaysRecieveInput() {
        isNaming = false;
    }

    @Override
    public boolean recieveInput(int key) {
        if (!isNaming) return false;
        if (key == Keyboard.KEY_RETURN) {
            String name = newConfigName.toString().trim();
            if (!name.isEmpty()) {
                configManager.createConfig(name);
                configManager.saveConfig();
                configManager.reloadConfigs();
                newConfigName.setLength(0);
            }
            isNaming = false;
            Arsenic.getArsenic().getClickGuiScreen().setAlwaysInputComponent(null);
            return true;
        }
        if (key == Keyboard.KEY_ESCAPE) {
            isNaming = false;
            Arsenic.getArsenic().getClickGuiScreen().setAlwaysInputComponent(null);
            return true;
        }
        if (key == Keyboard.KEY_BACK && newConfigName.length() > 0) {
            newConfigName.deleteCharAt(newConfigName.length() - 1);
            return true;
        }
        char c = Keyboard.getEventCharacter();
        if (ChatAllowedCharacters.isAllowedCharacter(c)) {
            newConfigName.append(c);
            return true;
        }
        return true;
    }

    @Override
    public void scroll(int s) {
        this.scroll += s;
        this.scroll = Math.min(0, this.scroll);
    }

    private void addButton(float x, float y, float x2, float y2, String label, Runnable action) {
        ConfigButton btn = new ConfigButton(label, action);
        btn.x = x;
        btn.y = y;
        btn.x2 = x2;
        btn.y2 = y2;
        buttons.add(btn);
    }

    private void importFromClipboard() {
        try {
            String data = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            if (data == null || data.trim().isEmpty()) return;
            String name = "clipboard_" + System.currentTimeMillis();
            File configFile = new File(configManager.getCurrentConfig().getDirectory().getParent(), name + ".json");
            Files.write(configFile.toPath(), data.getBytes());
            configManager.reloadConfigs();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void exportToClipboard() {
        try {
            configManager.saveConfig();
            File configFile = configManager.getCurrentConfig().getDirectory();
            String content = new String(Files.readAllBytes(configFile.toPath()));
            StringSelection selection = new StringSelection(content);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
