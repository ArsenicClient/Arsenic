package arsenic.gui.click.impl;

import arsenic.config.ConfigManager;
import arsenic.main.Arsenic;
import arsenic.module.ModuleCategory;
import arsenic.utils.interfaces.IAlwaysKeyboardInput;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.PosInfo;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.render.RenderUtils;
import arsenic.gui.themes.ThemeManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class ConfigsComponent extends ModuleCategoryComponent implements IAlwaysKeyboardInput {

    private static final String SERVER_URL = "https://ascfg.tranlongdo2506.workers.dev";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd.MM HH:mm");

    private static final ResourceLocation LOCAL_ICON = new ResourceLocation("arsenic", "icons/local.png");
    private static final ResourceLocation ONLINE_ICON = new ResourceLocation("arsenic", "icons/online.png");
    private static final ResourceLocation FETCH_ICON = new ResourceLocation("arsenic", "icons/fetch.png");
    private static final ResourceLocation UPLOAD_ICON = new ResourceLocation("arsenic", "icons/upload.png");
    private static final ResourceLocation DOWNLOAD_ICON = new ResourceLocation("arsenic", "icons/download.png");
    private static final ResourceLocation COPY_ICON = new ResourceLocation("arsenic", "icons/copy.png");
    private static final ResourceLocation PASTE_ICON = new ResourceLocation("arsenic", "icons/paste.png");

    private final ConfigManager configManager;
    private final StringBuilder newConfigName = new StringBuilder();
    private final StringBuilder uploadName = new StringBuilder();
    private boolean isNaming;
    private boolean isUploadNaming;
    private boolean needsAutoFetch;
    private int currentTab;
    private float scroll;
    private float inputBoxX, inputBoxY, inputBoxX2, inputBoxY2;
    private String currentConfigName = "";
    private String onlineStatus = "";
    private int onlineStatusColor = ThemeManager.getTextPrimary();
    private String searchQuery = "";
    private final List<OnlineConfig> onlineConfigs = new ArrayList<>();
    private final List<ConfigButton> buttons = new ArrayList<>();
    private final List<LocalConfigEntry> localConfigs = new ArrayList<>();

    private static class ConfigButton {
        float x, y, x2, y2;
        final Runnable action;

        ConfigButton(Runnable action) {
            this.action = action;
        }

        boolean isMouseOver(float mx, float my) {
            return mx >= x && mx <= x2 && my >= y && my <= y2;
        }
    }

    private static class OnlineConfig {
        String author;
        String name;
        String configJson;
        String tags;
        long updatedAt;

        OnlineConfig(String author, String name, String configJson, String tags, long updatedAt) {
            this.author = author;
            this.name = name;
            this.configJson = configJson;
            this.tags = tags;
            this.updatedAt = updatedAt;
        }
    }

    private static class LocalConfigEntry {
        String name;
        boolean active;
        long lastModified;

        LocalConfigEntry(String name, boolean active, long lastModified) {
            this.name = name;
            this.active = active;
            this.lastModified = lastModified;
        }
    }

    public ConfigsComponent() {
        super(ModuleCategory.CONFIGS);
        this.configManager = Arsenic.getArsenic().getConfigManager();
    }

    @Override
    protected float drawComponent(RenderInfo ri) {
        float anim = Math.max(enabledTimer.getPercent(), hoverTimer.getPercent());
        expandX = anim * (width / 14f);
        int mainC = ColorUtils.setColor(getEnabledColor(), 0, (int) (anim * 255));
        int gradientC = ColorUtils.setColor(getGradientColor(), 0, (int) (anim * 255));
        RenderUtils.resetColor();
        RenderUtils.resetColorText();
        DrawUtils.drawGradientRoundedRect(x1 + expandX, y1, x2 + expandX, y2, height / 4f, mainC, mainC, gradientC, gradientC);

        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

        float iconSize = ri.getFr().getHeight("|") * (ri.getGuiScreen().height / 300f);
        float iconX = x1 + (width / 7f) + expandX - iconSize;
        float iconY = midPointY - iconSize / 2f;
        Minecraft.getMinecraft().getTextureManager().bindTexture(icon);
        Gui.drawModalRectWithCustomSizedTexture((int) iconX, (int) iconY, 0, 0, (int) iconSize, (int) iconSize, (int) iconSize, (int) iconSize);

        ri.getFr().drawString(getName(), iconX + iconSize + 2, midPointY, getWhite(), ri.getFr().CENTREY);
        return height;
    }

    @Override
    public void drawLeft(PosInfo pi, RenderInfo ri) {
        buttons.clear();
        float x = pi.getX();
        float y = pi.getY() + scroll + 12;
        float maxX = ri.getGuiScreen().width * 7 / 8f - 10;
        float panelWidth = maxX - x;
        float mx = ri.getMouseX();

        currentConfigName = configManager.getCurrentConfig() != null ? configManager.getCurrentConfig().getName() : "";

        refreshLocalConfigs();

        if (currentTab != 1 || !isUploadNaming) {
            drawTabs(x, y, maxX, ri);
        }
        y += 30;

        if (currentTab == 0) {
            drawLocalTab(x, y, maxX, panelWidth, mx, ri);
        } else {
            if (needsAutoFetch) {
                needsAutoFetch = false;
                fetchOnlineConfigs();
            }
            drawOnlineTab(x, y, maxX, panelWidth, mx, ri);
        }
    }

    private void drawTabs(float x, float y, float maxX, RenderInfo ri) {
        float tabW = (maxX - x - 10) / 2f;
        float tabH = 26;
        float mx = ri.getMouseX();
        float my = ri.getMouseY();

        for (int i = 0; i < 2; i++) {
            float tx = x + 5 + i * (tabW + 2);
            boolean hovered = mx >= tx && mx <= tx + tabW && my >= y && my <= y + tabH;
            boolean active = i == currentTab;
            int tabIndex = i;

            int bgColor = active ? ThemeManager.getConfigsCard() : (hovered ? ThemeManager.getConfigsHoverBackground() : ThemeManager.getConfigsBackground());
            DrawUtils.drawRoundedRect(tx, y, tx + tabW, y + tabH, 6, bgColor);

            if (active) {
                DrawUtils.drawRoundedOutline(tx, y, tx + tabW + 1, y + tabH + 1, 6, 1, ThemeManager.getConfigsCardBorder());
                DrawUtils.drawRoundedOutline(tx, y + tabH - 1, tx + tabW + 1, y + tabH + 1, 1, 1, ThemeManager.getConfigsCard());
            }

            ResourceLocation tabIcon = i == 0 ? LOCAL_ICON : ONLINE_ICON;
            String label = i == 0 ? "Local" : "Online";
            int textColor = active ? ThemeManager.getTextPrimary() : ThemeManager.getTextMuted();

            float iconSize = tabH - 8;
            drawIcon(tabIcon, tx + 8, y + 4, iconSize, ri);
            ri.getFr().drawString(label, tx + 8 + iconSize + 4, y + tabH / 2f, textColor, ri.getFr().CENTREY);

            addButton(tx, y, tx + tabW, y + tabH, () -> {
                if (currentTab != tabIndex) {
                    currentTab = tabIndex;
                    if (tabIndex == 1) needsAutoFetch = true;
                }
            });
        }
    }

    private void refreshLocalConfigs() {
        localConfigs.clear();
        File configDir = new File(
                Minecraft.getMinecraft().mcDataDir + File.separator + "Arsenic" + File.separator + "Configs"
        );
        for (String name : configManager.getConfigList().stream().sorted().collect(Collectors.toList())) {
            File f = new File(configDir, name + ".json");
            long mod = f.exists() ? f.lastModified() : 0;
            localConfigs.add(new LocalConfigEntry(name, name.equals(currentConfigName), mod));
        }
    }

    private void drawLocalTab(float x, float y, float maxX, float panelWidth, float mx, RenderInfo ri) {
        drawSectionLabel("Saved configs", x + 5, y, ri);
        y += 16;

        float rowW = maxX - x - 10;
        for (LocalConfigEntry entry : localConfigs) {
            float rowX = x + 5;
            float rowY = y;
            float rowH = 38;
            boolean hovered = mx >= rowX && mx <= rowX + rowW
                    && ri.getMouseY() >= rowY && ri.getMouseY() <= rowY + rowH;

            int bg = entry.active ? selectedBg() : ThemeManager.getConfigsCard();
            int border = hovered ? ThemeManager.getConfigsHoverBorder() : ThemeManager.getConfigsCardBorder();
            DrawUtils.drawRoundedRect(rowX, rowY, rowX + rowW, rowY + rowH, 8, bg);
            DrawUtils.drawRoundedOutline(rowX, rowY, rowX + rowW, rowY + rowH, 8, 1, border);

            float dotSize = 6;
            float dotX = rowX + 12;
            float dotY = rowY + rowH / 2f;
            int dotColor = entry.active ? accent() : ThemeManager.getConfigsHoverBorder();
            DrawUtils.drawCircle(dotX, dotY - dotSize / 2f, dotSize / 2f, dotColor);

            float textX = dotX + dotSize + 10;
            ri.getFr().drawString(entry.name, textX, rowY + 10, ThemeManager.getTextPrimary());

            String playerName = Minecraft.getMinecraft().thePlayer != null
                    ? Minecraft.getMinecraft().thePlayer.getName()
                    : "you";
            String dateStr = entry.lastModified > 0 ? DATE_FMT.format(new Date(entry.lastModified)) : "unknown";
            String meta = "Modified: " + dateStr + "  \u00b7  " + playerName;
            ri.getFr().drawString(meta, textX, rowY + 24, ThemeManager.getTextSecondary());

            if (hovered) {
                float btnY = rowY + (rowH - 22) / 2f;
                float btnH = 22;

                float delX = rowX + rowW - 8 - 22;
                addButton(delX, btnY, delX + 22, btnY + btnH, () -> {
                    configManager.deleteConfig(entry.name);
                    configManager.reloadConfigs();
                });
                DrawUtils.drawRoundedRect(delX, btnY, delX + 22, btnY + btnH, 5, ThemeManager.getConfigsCard());
                DrawUtils.drawRoundedOutline(delX, btnY, delX + 22, btnY + btnH, 5, 1, ThemeManager.getConfigsHoverBorder());
                ri.getFr().drawString("X", delX + 11, btnY + btnH / 2f, ThemeManager.getError(), ri.getFr().CENTREX, ri.getFr().CENTREY);

                float copyX = delX - 8 - 22;
                addButton(copyX, btnY, copyX + 22, btnY + btnH, () -> exportConfig(entry.name));
                DrawUtils.drawRoundedRect(copyX, btnY, copyX + 22, btnY + btnH, 5, ThemeManager.getConfigsCard());
                DrawUtils.drawRoundedOutline(copyX, btnY, copyX + 22, btnY + btnH, 5, 1, ThemeManager.getConfigsHoverBorder());
                float iconS = 14;
                drawIcon(COPY_ICON, copyX + (22 - iconS) / 2f, btnY + (btnH - iconS) / 2f, iconS, ri);

                float loadX = copyX - 8 - 50;
                float loadW = 50;
                addButton(loadX, btnY, loadX + loadW, btnY + btnH, () -> {
                    configManager.loadConfig(entry.name);
                    Arsenic.getArsenic().getConfigManager().reloadConfigs();
                });
                DrawUtils.drawRoundedRect(loadX, btnY, loadX + loadW, btnY + btnH, 5, accent());
                ri.getFr().drawString("Load", loadX + loadW / 2f, btnY + btnH / 2f, ThemeManager.getWhite(), ri.getFr().CENTREX, ri.getFr().CENTREY);
            }

            y += rowH + 6;
        }

        y += 10;

        DrawUtils.drawRect(x + 5, y, maxX, y + 1, ThemeManager.getSeparator());
        y += 16;

        drawSectionLabel("Save current", x + 5, y, ri);
        y += 16;

        float inputH = 22;
        inputBoxX = x + 5;
        inputBoxY = y;
        inputBoxX2 = x + 5 + panelWidth * 0.55f;
        inputBoxY2 = y + inputH;
        float inputW = inputBoxX2 - inputBoxX;

        int inputBorder = isNaming ? accent() : ThemeManager.getConfigsCardBorder();
        int inputBg = ThemeManager.getConfigsCard();
        DrawUtils.drawRoundedRect(inputBoxX, inputBoxY, inputBoxX2, inputBoxY2, 6, inputBg);
        DrawUtils.drawRoundedOutline(inputBoxX, inputBoxY, inputBoxX2, inputBoxY2, 6, 1, inputBorder);

        boolean showCursor = isNaming && (System.currentTimeMillis() % 1000 < 500);
        String displayText = newConfigName.length() == 0 ? "Config name..." : newConfigName.toString();
        int displayColor = newConfigName.length() == 0 ? ThemeManager.getTextMuted() : ThemeManager.getTextPrimary();
        ri.getFr().drawString(displayText, inputBoxX + 8, inputBoxY + inputH / 2f, displayColor, ri.getFr().CENTREY);

        if (showCursor) {
            float cursorX = inputBoxX + 8 + ri.getFr().getWidth(newConfigName.toString()) + 1;
            DrawUtils.drawRect(cursorX, inputBoxY + 4, cursorX + 1, inputBoxY + inputH - 4, accent());
        }

        float saveX = inputBoxX2 + 6;
        float saveW = 50;
        addButton(saveX, y, saveX + saveW, y + inputH, () -> {
            String name = newConfigName.toString().trim();
            if (!name.isEmpty()) {
                configManager.createConfig(name);
                configManager.saveConfig();
                configManager.reloadConfigs();
                newConfigName.setLength(0);
                isNaming = false;
            }
        });
        DrawUtils.drawRoundedRect(saveX, y, saveX + saveW, y + inputH, 6, accent());
        ri.getFr().drawString("Save", saveX + saveW / 2f, y + inputH / 2f, ThemeManager.getWhite(), ri.getFr().CENTREX, ri.getFr().CENTREY);

        y += inputH + 10;

        float btnW = (panelWidth - 8 - 5) / 2f;
        float btnH2 = 26;

        float impX = x + 5;
        addButton(impX, y, impX + btnW, y + btnH2, () -> importFromClipboard());
        DrawUtils.drawRoundedRect(impX, y, impX + btnW, y + btnH2, 6, ThemeManager.getConfigsCard());
        DrawUtils.drawRoundedOutline(impX, y, impX + btnW, y + btnH2, 6, 1, ThemeManager.getConfigsCardBorder());
        drawIconWithText(PASTE_ICON, "Import", impX, y, btnW, btnH2, ThemeManager.getTextSecondary(), ri);

        float expX = x + 5 + btnW + 8;
        addButton(expX, y, expX + btnW, y + btnH2, () -> exportToClipboard());
        DrawUtils.drawRoundedRect(expX, y, expX + btnW, y + btnH2, 6, ThemeManager.getConfigsCard());
        DrawUtils.drawRoundedOutline(expX, y, expX + btnW, y + btnH2, 6, 1, ThemeManager.getConfigsCardBorder());
        drawIconWithText(COPY_ICON, "Export", expX, y, btnW, btnH2, ThemeManager.getTextSecondary(), ri);

        y += btnH2 + 10;
    }

    private void drawUploadNamingDialog(float x, float y, float maxX, float panelWidth, RenderInfo ri) {
        float dialogW = 300;
        float dialogH = 100;
        float dx = x + (maxX - x - dialogW) / 2f;
        float dy = y + 20;

        DrawUtils.drawRoundedRect(dx, dy, dx + dialogW, dy + dialogH, 8, ThemeManager.getConfigsCard());
        DrawUtils.drawRoundedOutline(dx, dy, dx + dialogW, dy + dialogH, 8, 1, ThemeManager.getConfigsCardBorder());

        ri.getFr().drawString("Config name for upload:", dx + 15, dy + 15, ThemeManager.getTextPrimary());

        float inX = dx + 15;
        float inY = dy + 35;
        float inW = dialogW - 30;
        float inH = 28;

        int inBorder = ThemeManager.getConfigsCardBorder();
        DrawUtils.drawRoundedRect(inX, inY, inX + inW, inY + inH, 6, ThemeManager.getConfigsCard());
        DrawUtils.drawRoundedOutline(inX, inY, inX + inW, inY + inH, 6, 1, inBorder);

        boolean showCursor = isUploadNaming && (System.currentTimeMillis() % 1000 < 500);
        String displayText = uploadName.length() == 0 ? "Upload name..." : uploadName.toString();
        int displayColor = uploadName.length() == 0 ? ThemeManager.getTextMuted() : ThemeManager.getTextPrimary();
        ri.getFr().drawString(displayText, inX + 8, inY + inH / 2f, displayColor, ri.getFr().CENTREY);

        if (showCursor) {
            float cursorX = inX + 8 + ri.getFr().getWidth(uploadName.toString()) + 1;
            DrawUtils.drawRect(cursorX, inY + 4, cursorX + 1, inY + inH - 4, accent());
        }

        float btnW2 = 60;
        float btnH2 = 24;
        float cancelX = dx + dialogW - 15 - btnW2;
        float uploadX = cancelX - 8 - btnW2;
        float btnY2 = dy + dialogH - 15 - btnH2;

        addButton(uploadX, btnY2, uploadX + btnW2, btnY2 + btnH2, () -> {
            String name = uploadName.toString().trim();
            if (!name.isEmpty()) {
                uploadCurrentConfig(name);
            }
            isUploadNaming = false;
            Arsenic.getArsenic().getClickGuiScreen().setAlwaysInputComponent(null);
        });
        DrawUtils.drawRoundedRect(uploadX, btnY2, uploadX + btnW2, btnY2 + btnH2, 5, accent());
        ri.getFr().drawString("Upload", uploadX + btnW2 / 2f, btnY2 + btnH2 / 2f, ThemeManager.getWhite(), ri.getFr().CENTREX, ri.getFr().CENTREY);

        addButton(cancelX, btnY2, cancelX + btnW2, btnY2 + btnH2, () -> {
            isUploadNaming = false;
            Arsenic.getArsenic().getClickGuiScreen().setAlwaysInputComponent(null);
        });
        DrawUtils.drawRoundedRect(cancelX, btnY2, cancelX + btnW2, btnY2 + btnH2, 5, ThemeManager.getConfigsCard());
        DrawUtils.drawRoundedOutline(cancelX, btnY2, cancelX + btnW2, btnY2 + btnH2, 5, 1, ThemeManager.getConfigsHoverBorder());
        ri.getFr().drawString("Cancel", cancelX + btnW2 / 2f, btnY2 + btnH2 / 2f, ThemeManager.getTextSecondary(), ri.getFr().CENTREX, ri.getFr().CENTREY);

        addButton(dx, dy, dx + dialogW, dy + dialogH, () -> {
            isUploadNaming = false;
            Arsenic.getArsenic().getClickGuiScreen().setAlwaysInputComponent(null);
        });
    }

    private void drawOnlineTab(float x, float y, float maxX, float panelWidth, float mx, RenderInfo ri) {
        float rowW = maxX - x - 10;
        float btnH = 24;

        if (isUploadNaming) {
            drawUploadNamingDialog(x, y, maxX, panelWidth, ri);
            return;
        }

        float fetchW = rowW / 2f - 4;
        addButton(x + 5, y, x + 5 + fetchW, y + btnH, () -> fetchOnlineConfigs());
        DrawUtils.drawRoundedRect(x + 5, y, x + 5 + fetchW, y + btnH, 6, ThemeManager.getConfigsCard());
        DrawUtils.drawRoundedOutline(x + 5, y, x + 5 + fetchW, y + btnH, 6, 1, ThemeManager.getConfigsCardBorder());
        drawIconWithText(FETCH_ICON, "Fetch", x + 5, y, fetchW, btnH, ThemeManager.getTextSecondary(), ri);

        float upX = x + 5 + rowW / 2f + 4;
        addButton(upX, y, upX + fetchW, y + btnH, () -> {
            isUploadNaming = true;
            uploadName.setLength(0);
            Arsenic.getArsenic().getClickGuiScreen().setAlwaysInputComponent(this);
        });
        DrawUtils.drawRoundedRect(upX, y, upX + fetchW, y + btnH, 6, accent());
        drawIconWithText(UPLOAD_ICON, "Upload", upX, y, fetchW, btnH, ThemeManager.getWhite(), ri);

        y += btnH + 6;

        if (!onlineStatus.isEmpty()) {
            ri.getFr().drawString(onlineStatus, x + 5, y, onlineStatusColor);
            y += 12;
        }

        DrawUtils.drawRect(x + 5, y - 2, maxX, y - 1, ThemeManager.getSeparator());
        y += 4;

        drawSectionLabel("Online configs", x + 5, y, ri);
        y += 16;

        if (onlineConfigs.isEmpty()) {
            ri.getFr().drawString("No configs fetched.", x + 5, y, ThemeManager.getTextMuted());
            return;
        }

        for (OnlineConfig cfg : onlineConfigs) {
            float rowX = x + 5;
            float rowY = y;
            float rowH = 44;
            boolean hovered = mx >= rowX && mx <= rowX + rowW
                    && ri.getMouseY() >= rowY && ri.getMouseY() <= rowY + rowH;

            int border = hovered ? ThemeManager.getConfigsHoverBorder() : ThemeManager.getConfigsCardBorder();
            DrawUtils.drawRoundedRect(rowX, rowY, rowX + rowW, rowY + rowH, 8, ThemeManager.getConfigsCard());
            DrawUtils.drawRoundedOutline(rowX, rowY, rowX + rowW, rowY + rowH, 8, 1, border);

            ri.getFr().drawString(cfg.name, rowX + 14, rowY + 10, ThemeManager.getTextPrimary());

            float tagX = rowX + 14 + ri.getFr().getWidth(cfg.name) + 10;
            if (!cfg.tags.isEmpty()) {
                int tagW = (int) ri.getFr().getWidth(cfg.tags) + 16;
                int tagBg = ColorUtils.setColor(accent() & 0x00FFFFFF, 0, 15);
                int tagBorder = ColorUtils.setColor(accent() & 0x00FFFFFF, 0, 40);
                DrawUtils.drawRoundedRect(tagX, rowY + 6, tagX + tagW, rowY + 18, 4, tagBg);
                DrawUtils.drawRoundedOutline(tagX, rowY + 6, tagX + tagW, rowY + 18, 4, 1, tagBorder);
                ri.getFr().drawString(cfg.tags, tagX + tagW / 2f, rowY + 12, accent(), ri.getFr().CENTREX, ri.getFr().CENTREY);
            }

            String dateStr = cfg.updatedAt > 0 ? DATE_FMT.format(new Date(cfg.updatedAt)) : "unknown";
            String info = "by " + cfg.author + "  \u00b7  " + dateStr;
            ri.getFr().drawString(info, rowX + 14, rowY + 28, ThemeManager.getTextMuted());

            if (hovered) {
                float btnY = rowY + (rowH - 22) / 2f;
                float dlSize = 22;
                float dlX = rowX + rowW - 8 - dlSize;
                addButton(dlX, btnY, dlX + dlSize, btnY + dlSize, () -> downloadConfig(cfg));
                DrawUtils.drawRoundedRect(dlX, btnY, dlX + dlSize, btnY + dlSize, 5, accent());
                float dIconS = 14;
                drawIcon(DOWNLOAD_ICON, dlX + (dlSize - dIconS) / 2f, btnY + (dlSize - dIconS) / 2f, dIconS, ri);
            }

            y += rowH + 6;
        }
    }

    private int accent() {
        return getEnabledColor();
    }

    private int selectedBg() {
        return RenderUtils.interpolateColoursInt(ThemeManager.getConfigsCard(), getEnabledColor(), 0.08f);
    }

    private void drawSectionLabel(String label, float x, float y, RenderInfo ri) {
        ri.getFr().drawString(label.toUpperCase(), x, y, ThemeManager.getTextMuted());
    }

    private void drawIcon(ResourceLocation res, float x, float y, float size, RenderInfo ri) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(res);
        GlStateManager.color(1f, 1f, 1f, 1f);
        Gui.drawModalRectWithCustomSizedTexture((int) x, (int) y, 0, 0, (int) size, (int) size, (int) size, (int) size);
    }

    private void drawIconWithText(ResourceLocation icon, String text, float areaX, float areaY, float areaW, float areaH, int textColor, RenderInfo ri) {
        float iconSize = 16;
        float textW = ri.getFr().getWidth(text);
        float totalW = iconSize + 4 + textW;
        float startX = areaX + (areaW - totalW) / 2f;
        drawIcon(icon, startX, areaY + (areaH - iconSize) / 2f, iconSize, ri);
        ri.getFr().drawString(text, startX + iconSize + 4, areaY + areaH / 2f, textColor, ri.getFr().CENTREY);
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

        if (currentTab == 0 && mouseX >= inputBoxX && mouseX <= inputBoxX2
                && mouseY >= inputBoxY && mouseY <= inputBoxY2) {
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

        if (isUploadNaming) {
            isUploadNaming = false;
            Arsenic.getArsenic().getClickGuiScreen().setAlwaysInputComponent(null);
        }
    }

    @Override
    public void setNotAlwaysRecieveInput() {
        isNaming = false;
        isUploadNaming = false;
    }

    @Override
    public boolean recieveInput(int key) {
        if (isUploadNaming) {
            if (key == Keyboard.KEY_RETURN) {
                String name = uploadName.toString().trim();
                if (!name.isEmpty()) {
                    uploadCurrentConfig(name);
                }
                isUploadNaming = false;
                Arsenic.getArsenic().getClickGuiScreen().setAlwaysInputComponent(null);
                return true;
            }
            if (key == Keyboard.KEY_ESCAPE) {
                isUploadNaming = false;
                Arsenic.getArsenic().getClickGuiScreen().setAlwaysInputComponent(null);
                return true;
            }
            if (key == Keyboard.KEY_BACK && uploadName.length() > 0) {
                uploadName.deleteCharAt(uploadName.length() - 1);
                return true;
            }
            char c = Keyboard.getEventCharacter();
            if (ChatAllowedCharacters.isAllowedCharacter(c)) {
                uploadName.append(c);
                return true;
            }
            return true;
        }

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

    private void addButton(float x, float y, float x2, float y2, Runnable action) {
        ConfigButton btn = new ConfigButton(action);
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

    private void exportConfig(String name) {
        try {
            File configDir = new File(
                    Minecraft.getMinecraft().mcDataDir + File.separator + "Arsenic" + File.separator + "Configs"
            );
            File configFile = new File(configDir, name + ".json");
            String content = new String(Files.readAllBytes(configFile.toPath()));
            StringSelection selection = new StringSelection(content);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
            onlineStatus = "Exported " + name + " to clipboard";
            onlineStatusColor = getEnabledColor();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private HttpURLConnection openConnection(String path) throws IOException {
        URL url = new URL(SERVER_URL + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        return conn;
    }

    private void fetchOnlineConfigs() {
        new Thread(() -> {
            try {
                setOnlineStatus("Fetching...", ThemeManager.getStatus());
                HttpURLConnection conn = openConnection("/api/fetchall");
                conn.setRequestMethod("GET");

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    setOnlineStatus("Server error: " + responseCode, ThemeManager.getError());
                    return;
                }

                String response = readStream(conn.getInputStream());
                JsonElement parsed = new JsonParser().parse(response);
                JsonArray arr;
                if (parsed.isJsonArray()) {
                    arr = parsed.getAsJsonArray();
                } else if (parsed.isJsonObject() && parsed.getAsJsonObject().has("configs")) {
                    arr = parsed.getAsJsonObject().get("configs").getAsJsonArray();
                } else {
                    setOnlineStatus("Unexpected response format", ThemeManager.getError());
                    return;
                }

                List<OnlineConfig> fetched = new ArrayList<>();
                for (JsonElement el : arr) {
                    JsonObject obj = el.getAsJsonObject();
                    String author = obj.get("author").getAsString();
                    String name = obj.get("name").getAsString();
                    String configJson = obj.get("config").toString();
                    String tags = "";
                    if (obj.has("tags") && obj.get("tags").isJsonArray()) {
                        JsonArray tArr = obj.get("tags").getAsJsonArray();
                        tags = tArr.size() > 0 ? tArr.get(0).getAsString() : "";
                    }
                    long updatedAt = obj.has("updatedAt") ? obj.get("updatedAt").getAsLong() : 0;
                    fetched.add(new OnlineConfig(author, name, configJson, tags, updatedAt));
                }

                onlineConfigs.clear();
                onlineConfigs.addAll(fetched);
                setOnlineStatus("Fetched " + fetched.size() + " configs", getEnabledColor());
            } catch (Exception e) {
                setOnlineStatus("Error: " + e.getMessage(), ThemeManager.getError());
            }
        }).start();
    }

    private void uploadCurrentConfig() {
        uploadCurrentConfig(configManager.getCurrentConfig().getName());
    }

    private void uploadCurrentConfig(String configName) {
        new Thread(() -> {
            try {
                setOnlineStatus("Uploading...", ThemeManager.getStatus());
                configManager.saveConfig();

                File configFile = configManager.getCurrentConfig().getDirectory();
                String configData = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
                String playerName = Minecraft.getMinecraft().thePlayer != null
                        ? Minecraft.getMinecraft().thePlayer.getName()
                        : "Unknown";

                JsonObject body = new JsonObject();
                body.addProperty("author", playerName);
                body.addProperty("name", configName);
                body.add("config", new JsonParser().parse(configData));

                HttpURLConnection conn = openConnection("/api/upload");
                conn.setRequestMethod("PUT");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    setOnlineStatus("Uploaded successfully", getEnabledColor());
                    needsAutoFetch = true;
                } else {
                    setOnlineStatus("Upload failed: " + responseCode, ThemeManager.getError());
                }
            } catch (Exception e) {
                setOnlineStatus("Error: " + e.getMessage(), ThemeManager.getError());
            }
        }).start();
    }

    private void downloadConfig(OnlineConfig cfg) {
        new Thread(() -> {
            try {
                setOnlineStatus("Downloading " + cfg.name + "...", ThemeManager.getStatus());

                File configDir = new File(
                        Minecraft.getMinecraft().mcDataDir + File.separator + "Arsenic" + File.separator + "Configs"
                );
                File configFile = new File(configDir, cfg.name + ".json");
                Files.write(configFile.toPath(), cfg.configJson.getBytes(StandardCharsets.UTF_8));

                configManager.reloadConfigs();
                setOnlineStatus("Downloaded " + cfg.name, getEnabledColor());
            } catch (Exception e) {
                setOnlineStatus("Error: " + e.getMessage(), ThemeManager.getError());
            }
        }).start();
    }

    private void setOnlineStatus(String msg, int color) {
        onlineStatus = msg;
        onlineStatusColor = color;
    }

    private String readStream(InputStream stream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            result.append(line);
        }
        reader.close();
        return result.toString();
    }
}
