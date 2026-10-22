package arsenic.gui.click.impl;

import arsenic.config.ConfigManager;
import arsenic.config.ModuleConfig;
import arsenic.gui.click.GuiStyle;
import arsenic.gui.click.UITheme;
import arsenic.gui.themes.ThemeManager;
import arsenic.main.Arsenic;
import arsenic.module.ModuleCategory;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.interfaces.IAlwaysKeyboardInput;
import arsenic.utils.java.SoundUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.PosInfo;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.timer.TimeUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.util.StringUtil;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import arsenic.utils.io.Keys;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * The Configs page: the config in use, saving the current settings as a new config, the saved configs (load, rename,
 * copy, delete) and the shared online configs. Everything that changes files goes through {@link ConfigManager}.
 */
public class ConfigsComponent extends ModuleCategoryComponent implements IAlwaysKeyboardInput {

    private static final String SERVER_URL = "https://ascfg.tranlongdo2506.workers.dev";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd MMM, HH:mm");

    private static final Identifier LOCAL_ICON = Identifier.fromNamespaceAndPath("arsenic", "icons/local.png");
    private static final Identifier ONLINE_ICON = Identifier.fromNamespaceAndPath("arsenic", "icons/online.png");

    private static final float BOTTOM_MARGIN = 28f;
    private static final float ROW_GAP = 6f;
    private static final long CONFIRM_MS = 3000;
    private static final long STATUS_MS = 4500;
    private static final long LIST_REFRESH_MS = 2000;

    private enum Tab { LOCAL, ONLINE }
    private enum Tone { NORMAL, PRIMARY, DANGER }

    /** A clickable area recorded while drawing; the first one under the mouse gets the click. */
    private static final class Hit {
        final float x1, y1, x2, y2;
        final Runnable action;

        Hit(float x1, float y1, float x2, float y2, Runnable action) {
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
            this.action = action;
        }

        boolean contains(float mx, float my) {
            return mx >= x1 && mx <= x2 && my >= y1 && my <= y2;
        }
    }

    /** A one-line text box. Only one is focused at a time; it gets the keyboard until Enter, Escape or a click elsewhere. */
    private static final class Field {
        final StringBuilder text = new StringBuilder();
        final String placeholder;
        Runnable onSubmit;
        Runnable onCancel;

        Field(String placeholder) {
            this.placeholder = placeholder;
        }

        String value() { return text.toString().trim(); }

        void set(String s) {
            text.setLength(0);
            text.append(s == null ? "" : s);
        }
    }

    private static final class Status {
        final String text;
        final boolean error;
        final long time = System.currentTimeMillis();

        Status(String text, boolean error) {
            this.text = text;
            this.error = error;
        }
    }

    private static final class OnlineConfig {
        final String author, name, configJson, tag;
        final long updatedAt;

        OnlineConfig(String author, String name, String configJson, String tag, long updatedAt) {
            this.author = author;
            this.name = name;
            this.configJson = configJson;
            this.tag = tag;
            this.updatedAt = updatedAt;
        }
    }

    private final ConfigManager configManager;
    private final List<Hit> hits = new ArrayList<>();

    private final Field createField = new Field("Name the current settings...");
    private final Field renameField = new Field("New name...");
    private final Field uploadField = new Field("Name to share it as...");
    private Field focused;

    private Tab tab = Tab.LOCAL;
    /** The saved config being renamed, or null. */
    private String renaming;
    /** What a red "Sure?" button is waiting for (e.g. "delete:name" or "reset"), and since when. */
    private String confirmKey;
    private long confirmTime;
    private boolean uploading;

    private List<ModuleConfig> localList = Collections.emptyList();
    private long localListTime;

    // written by the network threads
    private volatile Status status;
    private volatile List<OnlineConfig> online = Collections.emptyList();
    private volatile boolean fetching, fetchedOnce;

    public ConfigsComponent() {
        super(ModuleCategory.CONFIGS);
        this.configManager = Arsenic.getArsenic().getConfigManager();

        createField.onSubmit = this::createConfig;
        renameField.onSubmit = this::finishRename;
        renameField.onCancel = () -> renaming = null;
        uploadField.onSubmit = this::upload;
        uploadField.onCancel = () -> uploading = false;
    }

    @Override
    protected boolean followsMoreToggle() {
        return false;
    }

    @Override
    public void setCurrentCategory(boolean currentCategory) {
        super.setCurrentCategory(currentCategory);
        if (currentCategory)
            refreshLocalList();
        else
            unfocus();
    }

    /* ================================================================== drawing */

    @Override
    public void drawLeft(PosInfo pi, RenderInfo ri) {
        hits.clear();

        scroll += (targetScroll - scroll) * GuiStyle.scrollEase();
        if (Math.abs(targetScroll - scroll) < 0.5f)
            scroll = targetScroll;

        if (System.currentTimeMillis() - localListTime > LIST_REFRESH_MS)
            refreshLocalList();
        if (confirmKey != null && System.currentTimeMillis() - confirmTime > CONFIRM_MS)
            confirmKey = null;

        float x = pi.getX() + 5;
        float contentTop = pi.getY() + scroll + 12;
        float maxX = ri.getGuiScreen().width * 7 / 8f - 10;
        float w = maxX - x - 5;
        float y = contentTop;

        y = drawTabs(ri, x, y, w);
        y = drawStatus(ri, x, y, w);

        if (tab == Tab.LOCAL)
            y = drawLocal(ri, x, y, w);
        else
            y = drawOnline(ri, x, y, w);

        maxHeight = y - contentTop + BOTTOM_MARGIN;
    }

    @Override
    public void drawRight(PosInfo pi, RenderInfo ri) {
    }

    private float drawTabs(RenderInfo ri, float x, float y, float w) {
        float h = 26, gap = 6;
        float tabW = (w - gap) / 2f;
        for (Tab t : Tab.values()) {
            float tx = x + t.ordinal() * (tabW + gap);
            boolean active = tab == t;
            boolean hovered = hover(ri, tx, y, tx + tabW, y + h);

            int fill = active ? UITheme.mix(ThemeManager.getConfigsCard(), UITheme.accent(), 0.12f)
                    : (hovered ? ThemeManager.getConfigsHoverBackground() : ThemeManager.getConfigsCard());
            UITheme.surface(tx, y, tx + tabW, y + h, 8f, fill,
                    active || hovered ? UITheme.Elevation.RAISED : UITheme.Elevation.FLAT, active ? 1f : 0.6f);
            DrawUtils.drawRoundedOutline(tx, y, tx + tabW, y + h, 8f, active ? 1.4f : 1f,
                    active ? UITheme.alpha(UITheme.accent(), 190)
                           : (hovered ? ThemeManager.getConfigsHoverBorder() : ThemeManager.getConfigsCardBorder()));

            String label = t == Tab.LOCAL ? "My configs" : "Online";
            int color = active ? ThemeManager.getTextPrimary() : ThemeManager.getTextSecondary();
            float icon = h - 12;
            float textW = ri.getFr().getWidth(label);
            float startX = tx + (tabW - (icon + 5 + textW)) / 2f;
            drawIcon(t == Tab.LOCAL ? LOCAL_ICON : ONLINE_ICON, startX, y + (h - icon) / 2f, icon, color);
            ri.getFr().drawString(label, startX + icon + 5, y + h / 2f, color, ri.getFr().CENTREY);

            hits.add(new Hit(tx, y, tx + tabW, y + h, () -> switchTab(t)));
        }
        return y + h + 10;
    }

    /** The last result (saved, loaded, an error...), fading out after a few seconds. */
    private float drawStatus(RenderInfo ri, float x, float y, float w) {
        Status s = status;
        if (s == null)
            return y;
        long age = System.currentTimeMillis() - s.time;
        if (age > STATUS_MS)
            return y;
        float pct = age > STATUS_MS - 600 ? (STATUS_MS - age) / 600f : 1f;
        float h = 22;
        int tint = s.error ? ThemeManager.getError() : UITheme.accent();
        DrawUtils.drawRoundedRect(x, y, x + w, y + h, 7f,
                UITheme.alpha(UITheme.mix(ThemeManager.getConfigsCard(), tint, 0.18f), pct));
        DrawUtils.drawRoundedRect(x, y, x + 3, y + h, 1.5f, UITheme.alpha(tint, pct));
        ri.getFr().drawString(ellipsize(ri.getFr(), s.text, w - 24, 1f), x + 12, y + h / 2f,
                UITheme.alpha(ThemeManager.getTextPrimary(), pct), ri.getFr().CENTREY);
        return y + h + 10;
    }

    /* ------------------------------------------------------------------ local tab */

    private float drawLocal(RenderInfo ri, float x, float y, float w) {
        ModuleConfig current = configManager.getCurrentConfig();

        sectionLabel(ri, "In use", x, y);
        y += 16;

        float cardH = 46;
        UITheme.surface(x, y, x + w, y + cardH, 10f,
                UITheme.mix(ThemeManager.getConfigsCard(), UITheme.accent(), 0.10f), UITheme.Elevation.RAISED, 1f);
        DrawUtils.drawRoundedOutline(x, y, x + w, y + cardH, 10f, 1.4f, UITheme.alpha(UITheme.accent(), 190));
        UITheme.accentBar(x, y, x + 3, y + cardH, 10f, 1f);

        float bx = x + w - 10;
        bx -= button(ri, bx, y + (cardH - 22) / 2f, 22, isConfirming("reset") ? "Sure?" : "Reset",
                isConfirming("reset") ? Tone.DANGER : Tone.NORMAL, this::resetCurrent, true) + 6;
        bx -= button(ri, bx, y + (cardH - 22) / 2f, 22, "Copy", Tone.NORMAL,
                () -> copyToClipboard(null), true) + 6;
        bx -= button(ri, bx, y + (cardH - 22) / 2f, 22, "Save", Tone.PRIMARY, () -> {
            configManager.saveConfig();
            info("Saved " + configManager.getCurrentName());
        }, true) + 10;

        String name = current == null ? "None" : current.getName();
        ri.getFr().drawString(ellipsize(ri.getFr(), name, bx - x - 16, 1f), x + 14, y + 15, ThemeManager.getTextPrimary(), ri.getFr().CENTREY);
        String meta = current == null ? "No config is selected"
                : "Saves automatically  ·  " + modified(current);
        small(ri, ellipsize(ri.getFr(), meta, bx - x - 16, 0.85f), x + 14, y + 31);
        y += cardH + 14;

        sectionLabel(ri, "Save as new", x, y);
        y += 16;

        float h = 24;
        float btnW = Math.max(70, ri.getFr().getWidth("Create") + 24);
        drawField(ri, createField, x, y, w - btnW - 6, h);
        button(ri, x + w, y, h, "Create", Tone.PRIMARY, this::createConfig, true, btnW);
        y += h + 6;

        float half = (w - 6) / 2f;
        button(ri, x + half, y, h, "Import from clipboard", Tone.NORMAL, this::importFromClipboard, true, half);
        button(ri, x + w, y, h, "Open folder", Tone.NORMAL, this::openFolder, true, half);
        y += h + 16;

        sectionLabel(ri, "Saved configs  ·  " + localList.size(), x, y);
        y += 16;

        if (localList.isEmpty()) {
            small(ri, "No configs yet. Save the current settings above.", x, y + 4);
            return y + 20;
        }
        for (ModuleConfig config : localList)
            y = drawLocalRow(ri, config, x, y, w) + ROW_GAP;
        return y;
    }

    private float drawLocalRow(RenderInfo ri, ModuleConfig config, float x, float y, float w) {
        String name = config.getName();
        boolean active = configManager.isCurrent(name);
        boolean isRenaming = name.equals(renaming);
        float h = 36;
        boolean hovered = hover(ri, x, y, x + w, y + h);

        int fill = active ? UITheme.mix(ThemeManager.getConfigsCard(), UITheme.accent(), 0.08f) : ThemeManager.getConfigsCard();
        UITheme.surface(x, y, x + w, y + h, 9f, fill,
                hovered ? UITheme.Elevation.RAISED : UITheme.Elevation.FLAT, hovered ? 1f : 0.6f);
        DrawUtils.drawRoundedOutline(x, y, x + w, y + h, 9f, 1f,
                active ? UITheme.alpha(UITheme.accent(), 150)
                       : (hovered ? ThemeManager.getConfigsHoverBorder() : ThemeManager.getConfigsCardBorder()));

        float dot = 3f;
        DrawUtils.drawCircle(x + 13, y + h / 2f, dot, active ? UITheme.accent() : ThemeManager.getConfigsHoverBorder());
        float textX = x + 24;
        float bh = 20, by = y + (h - bh) / 2f;
        float bx = x + w - 8;

        if (isRenaming) {
            bx -= button(ri, bx, by, bh, "Cancel", Tone.NORMAL, this::cancelRename, true) + 5;
            bx -= button(ri, bx, by, bh, "Rename", Tone.PRIMARY, this::finishRename, true) + 8;
            drawField(ri, renameField, textX - 4, by, bx - textX + 4, bh);
            return y + h;
        }

        // the buttons show on hover only, so the list stays readable
        if (hovered || isConfirming("delete:" + name)) {
            boolean sure = isConfirming("delete:" + name);
            if (!active)
                bx -= button(ri, bx, by, bh, sure ? "Sure?" : "Delete", Tone.DANGER, () -> delete(name), sure) + 5;
            bx -= button(ri, bx, by, bh, "Copy", Tone.NORMAL, () -> copyToClipboard(name), true) + 5;
            bx -= button(ri, bx, by, bh, "Rename", Tone.NORMAL, () -> startRename(name), true) + 5;
            if (!active)
                bx -= button(ri, bx, by, bh, "Load", Tone.PRIMARY, () -> load(name), true) + 5;
            bx -= 3;
        } else if (active) {
            float chipW = UITheme.chip(ri.getFr(), "IN USE", bx, y + h / 2f, 14, UITheme.accent(),
                    UITheme.alpha(UITheme.accent(), 40));
            bx -= chipW + 8;
        }

        float textMax = bx - textX - 4;
        ri.getFr().drawString(ellipsize(ri.getFr(), name, textMax, 1f), textX, y + 12,
                active ? ThemeManager.getTextPrimary() : ThemeManager.getTextSecondary(), ri.getFr().CENTREY);
        small(ri, ellipsize(ri.getFr(), modified(config), textMax, 0.85f), textX, y + 25);

        // clicking the row itself loads it; added last so the buttons win
        if (!active)
            hits.add(new Hit(x, y, x + w, y + h, () -> load(name)));
        return y + h;
    }

    /* ------------------------------------------------------------------ online tab */

    private float drawOnline(RenderInfo ri, float x, float y, float w) {
        float h = 24;
        float half = (w - 6) / 2f;
        button(ri, x + half, y, h, fetching ? "Refreshing..." : "Refresh", Tone.NORMAL, this::fetch, !fetching, half);
        button(ri, x + w, y, h, "Share current config", Tone.PRIMARY, () -> {
            uploading = !uploading;
            if (uploading) {
                uploadField.set(configManager.getCurrentName());
                focus(uploadField);
            } else if (focused == uploadField) {
                unfocus();
            }
        }, true, half);
        y += h + 8;

        if (uploading) {
            float btnW = Math.max(70, ri.getFr().getWidth("Upload") + 24);
            drawField(ri, uploadField, x, y, w - btnW * 2 - 12, h);
            button(ri, x + w - btnW - 6, y, h, "Upload", Tone.PRIMARY, this::upload, true, btnW);
            button(ri, x + w, y, h, "Cancel", Tone.NORMAL, () -> {
                uploading = false;
                unfocus();
            }, true, btnW);
            y += h + 8;
            small(ri, "Uploads " + configManager.getCurrentName() + " as " + author() + ".", x, y);
            y += 14;
        }
        y += 8;

        List<OnlineConfig> list = online;
        sectionLabel(ri, "Shared configs  ·  " + list.size(), x, y);
        y += 16;

        if (list.isEmpty()) {
            small(ri, fetching ? "Loading..." : (fetchedOnce ? "Nothing here yet." : "Press Refresh to see shared configs."), x, y + 4);
            return y + 20;
        }
        for (OnlineConfig config : list)
            y = drawOnlineRow(ri, config, x, y, w) + ROW_GAP;
        return y;
    }

    private float drawOnlineRow(RenderInfo ri, OnlineConfig config, float x, float y, float w) {
        float h = 38;
        boolean hovered = hover(ri, x, y, x + w, y + h);
        UITheme.surface(x, y, x + w, y + h, 9f, ThemeManager.getConfigsCard(),
                hovered ? UITheme.Elevation.RAISED : UITheme.Elevation.FLAT, hovered ? 1f : 0.6f);
        DrawUtils.drawRoundedOutline(x, y, x + w, y + h, 9f, 1f,
                hovered ? ThemeManager.getConfigsHoverBorder() : ThemeManager.getConfigsCardBorder());

        float bh = 20, by = y + (h - bh) / 2f;
        float bx = x + w - 8;
        if (hovered) {
            bx -= button(ri, bx, by, bh, "Use", Tone.PRIMARY, () -> download(config, true), true) + 5;
            bx -= button(ri, bx, by, bh, "Download", Tone.NORMAL, () -> download(config, false), true) + 8;
        }

        float textX = x + 12;
        float textMax = bx - textX - 4;
        String name = ellipsize(ri.getFr(), config.name, textMax, 1f);
        ri.getFr().drawString(name, textX, y + 13, ThemeManager.getTextPrimary(), ri.getFr().CENTREY);
        if (!config.tag.isEmpty()) {
            float tagX = textX + ri.getFr().getWidth(name) + 8;
            float tagW = ri.getFr().getWidth(config.tag) * 0.85f + 12;
            if (tagX + tagW < bx - 4) {
                DrawUtils.drawRoundedRect(tagX, y + 6, tagX + tagW, y + 20, 4f, UITheme.alpha(UITheme.accent(), 40));
                ri.getFr().drawString(config.tag, tagX + 6, y + 13, UITheme.accent(), ri.getFr().getScaleModifier(0.85f), ri.getFr().CENTREY);
            }
        }
        String when = config.updatedAt > 0 ? DATE_FMT.format(new Date(config.updatedAt)) : "unknown date";
        small(ri, ellipsize(ri.getFr(), "by " + config.author + "  ·  " + when, textMax, 0.85f), textX, y + 28);
        return y + h;
    }

    /* ------------------------------------------------------------------ widgets */

    /**
     * A pill button whose right edge is at rightX. @return its width
     * @param enabled whether it can be clicked (a disabled one is drawn faded)
     */
    private float button(RenderInfo ri, float rightX, float y, float h, String label, Tone tone, Runnable action, boolean enabled) {
        return button(ri, rightX, y, h, label, tone, action, enabled, ri.getFr().getWidth(label) * 0.9f + 18);
    }

    private float button(RenderInfo ri, float rightX, float y, float h, String label, Tone tone, Runnable action,
                         boolean enabled, float w) {
        float x1 = rightX - w;
        boolean hovered = enabled && hover(ri, x1, y, rightX, y + h);
        int fill, border, text;
        switch (tone) {
            case PRIMARY:
                fill = UITheme.alpha(UITheme.accent(), hovered ? 255 : 215);
                border = UITheme.alpha(UITheme.accent(), 255);
                text = UITheme.readableOn(UITheme.accent());
                break;
            case DANGER:
                fill = UITheme.alpha(ThemeManager.getError(), hovered ? 230 : 150);
                border = UITheme.alpha(ThemeManager.getError(), 255);
                text = ThemeManager.getWhite();
                break;
            default:
                fill = hovered ? ThemeManager.getConfigsHoverBackground() : ThemeManager.getButtonBackground();
                border = hovered ? ThemeManager.getConfigsHoverBorder() : ThemeManager.getConfigsCardBorder();
                text = ThemeManager.getTextPrimary();
        }
        if (!enabled) {
            fill = UITheme.fade(fill, 0.5f);
            text = UITheme.fade(text, 0.5f);
        }
        DrawUtils.drawRoundedRect(x1, y, rightX, y + h, Math.min(7f, h / 2f), fill);
        DrawUtils.drawRoundedOutline(x1, y, rightX, y + h, Math.min(7f, h / 2f), 1f, border);
        ri.getFr().drawString(label, x1 + w / 2f, y + h / 2f, text,
                ri.getFr().getScaleModifier(0.9f), ri.getFr().CENTREX, ri.getFr().CENTREY);
        if (enabled)
            hits.add(new Hit(x1, y, rightX, y + h, action));
        return w;
    }

    private void drawField(RenderInfo ri, Field field, float x, float y, float w, float h) {
        boolean isFocused = focused == field;
        boolean hovered = hover(ri, x, y, x + w, y + h);
        DrawUtils.drawRoundedRect(x, y, x + w, y + h, 7f, ThemeManager.getConfigsBackground());
        DrawUtils.drawRoundedOutline(x, y, x + w, y + h, 7f, isFocused ? 1.4f : 1f,
                isFocused ? UITheme.alpha(UITheme.accent(), 220)
                          : (hovered ? ThemeManager.getConfigsHoverBorder() : ThemeManager.getConfigsCardBorder()));

        FontRendererExtension<?> fr = ri.getFr();
        float pad = 8, room = w - pad * 2 - 2;
        if (field.text.length() == 0)
            fr.drawString(ellipsize(fr, field.placeholder, room, 1f), x + pad, y + h / 2f,
                    UITheme.fade(ThemeManager.getTextMuted(), 0.45f), fr.CENTREY);
        // the end of the text stays visible while typing
        String shown = field.text.toString();
        while (shown.length() > 0 && fr.getWidth(shown) > room)
            shown = shown.substring(1);
        if (!shown.isEmpty())
            fr.drawString(shown, x + pad, y + h / 2f, ThemeManager.getTextPrimary(), fr.CENTREY);
        if (isFocused && TimeUtils.blink(500)) {
            float cx = x + pad + (shown.isEmpty() ? 0 : fr.getWidth(shown)) + 1;
            DrawUtils.drawRect(cx, y + 5, cx + 1, y + h - 5, UITheme.accent());
        }
        hits.add(new Hit(x, y, x + w, y + h, () -> focus(field)));
    }

    private void sectionLabel(RenderInfo ri, String label, float x, float y) {
        StringBuilder sb = new StringBuilder();
        String upper = label.toUpperCase();
        for (int i = 0; i < upper.length(); i++) {
            sb.append(upper.charAt(i));
            if (i < upper.length() - 1)
                sb.append(' ');
        }
        ri.getFr().drawString(sb.toString(), x, y, ThemeManager.getTextMuted(), ri.getFr().getScaleModifier(0.78f));
    }

    private void small(RenderInfo ri, String text, float x, float y) {
        ri.getFr().drawString(text, x, y, UITheme.fade(ThemeManager.getTextMuted(), 0.6f),
                ri.getFr().getScaleModifier(0.85f), ri.getFr().CENTREY);
    }

    private void drawIcon(Identifier res, float x, float y, float size, int color) {
        DrawUtils.drawTexture(res, x, y, size, size, 0xFF000000 | color);
    }

    private static String ellipsize(FontRendererExtension<?> fr, String text, float maxWidth, float scale) {
        if (text == null)
            return "";
        if (fr.getWidth(text) * scale <= maxWidth)
            return text;
        String s = text;
        while (s.length() > 1 && fr.getWidth(s + "...") * scale > maxWidth)
            s = s.substring(0, s.length() - 1);
        return s + "...";
    }

    private static boolean hover(RenderInfo ri, float x1, float y1, float x2, float y2) {
        float mx = ri.getMouseX(), my = ri.getMouseY();
        return mx >= x1 && mx <= x2 && my >= y1 && my <= y2;
    }

    private static String modified(ModuleConfig config) {
        long time = config.lastModified();
        return time > 0 ? "Changed " + DATE_FMT.format(new Date(time)) : "Not saved yet";
    }

    /* ================================================================== actions */

    private void switchTab(Tab t) {
        if (tab == t)
            return;
        tab = t;
        unfocus();
        renaming = null;
        confirmKey = null;
        targetScroll = 0;
        if (t == Tab.ONLINE && !fetchedOnce && !fetching)
            fetch();
    }

    private void refreshLocalList() {
        configManager.reloadConfigs();
        localList = new ArrayList<>(configManager.getConfigs());
        localListTime = System.currentTimeMillis();
    }

    private void load(String name) {
        if (configManager.loadConfig(name))
            info("Loaded " + configManager.getCurrentName());
        else
            error("\"" + name + "\" is gone (deleted outside the game?)");
        refreshLocalList();
    }

    private void createConfig() {
        try {
            String raw = createField.value();
            String name = configManager.saveAs(raw, false);
            createField.set("");
            unfocus();
            info("Saved the current settings as " + name + " (now in use)");
        } catch (IllegalArgumentException e) {
            error(e.getMessage());
            focus(createField);
        }
        refreshLocalList();
    }

    private void delete(String name) {
        if (!isConfirming("delete:" + name)) {
            confirm("delete:" + name);
            return;
        }
        confirmKey = null;
        try {
            configManager.deleteConfig(name);
            info("Deleted " + name);
        } catch (IllegalArgumentException e) {
            error(e.getMessage());
        }
        refreshLocalList();
    }

    private void resetCurrent() {
        if (!isConfirming("reset")) {
            confirm("reset");
            return;
        }
        confirmKey = null;
        configManager.resetCurrentConfig();
        info("Reset " + configManager.getCurrentName() + " to the defaults");
    }

    private void startRename(String name) {
        renaming = name;
        renameField.set(name);
        focus(renameField);
    }

    private void finishRename() {
        if (renaming == null)
            return;
        try {
            String newName = configManager.renameConfig(renaming, renameField.value());
            info("Renamed " + renaming + " to " + newName);
            renaming = null;
            unfocus();
        } catch (IllegalArgumentException e) {
            error(e.getMessage());
            focus(renameField);
        }
        refreshLocalList();
    }

    private void cancelRename() {
        renaming = null;
        unfocus();
    }

    private void copyToClipboard(String name) {
        try {
            Minecraft.getInstance().keyboardHandler.setClipboard(configManager.exportConfig(name));
            info("Copied " + (name == null ? configManager.getCurrentName() : name) + " to the clipboard");
        } catch (IllegalArgumentException e) {
            error(e.getMessage());
        }
    }

    private void importFromClipboard() {
        String text = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (text == null || text.trim().isEmpty()) {
            error("The clipboard is empty");
            return;
        }
        try {
            String name = configManager.importConfig(text, "imported", false);
            info("Imported as " + name + ". Click it to load it, or Rename it");
        } catch (IllegalArgumentException e) {
            error(e.getMessage());
        }
        refreshLocalList();
    }

    private void openFolder() {
        File dir = configManager.getConfigDirectory();
        dir.mkdirs();
        new Thread(() -> {
            try {
                java.awt.Desktop.getDesktop().open(dir);
            } catch (Throwable t) {
                try {
                    com.mojang.blaze3d.Blaze3D.openPath(dir.toPath());
                } catch (Throwable t2) {
                    error("Could not open " + dir);
                }
            }
        }, "Arsenic open configs").start();
    }

    private void confirm(String key) {
        confirmKey = key;
        confirmTime = System.currentTimeMillis();
    }

    private boolean isConfirming(String key) {
        return key.equals(confirmKey) && System.currentTimeMillis() - confirmTime <= CONFIRM_MS;
    }

    private void info(String text) {
        status = new Status(text, false);
    }

    private void error(String text) {
        status = new Status(text, true);
    }

    /* ------------------------------------------------------------------ online */

    private static String author() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null)
            return mc.player.getName().getString();
        return mc.getUser() != null ? mc.getUser().getName() : "Unknown";
    }

    private void fetch() {
        if (fetching)
            return;
        fetching = true;
        new Thread(() -> {
            try {
                HttpURLConnection conn = openConnection("/api/fetchall");
                conn.setRequestMethod("GET");
                int code = conn.getResponseCode();
                if (code != 200) {
                    error("The config server answered " + code);
                    return;
                }
                JsonElement parsed = new JsonParser().parse(readStream(conn.getInputStream()));
                JsonArray arr = parsed.isJsonArray() ? parsed.getAsJsonArray()
                        : parsed.isJsonObject() && parsed.getAsJsonObject().has("configs")
                        ? parsed.getAsJsonObject().getAsJsonArray("configs") : null;
                if (arr == null) {
                    error("The config server sent something unexpected");
                    return;
                }
                List<OnlineConfig> fetched = new ArrayList<>();
                for (JsonElement el : arr) {
                    try {
                        JsonObject obj = el.getAsJsonObject();
                        if (!obj.has("config") || !obj.has("name"))
                            continue;
                        String tag = "";
                        if (obj.has("tags") && obj.get("tags").isJsonArray() && obj.getAsJsonArray("tags").size() > 0)
                            tag = obj.getAsJsonArray("tags").get(0).getAsString();
                        fetched.add(new OnlineConfig(
                                obj.has("author") ? obj.get("author").getAsString() : "unknown",
                                obj.get("name").getAsString(),
                                obj.get("config").toString(),
                                tag,
                                obj.has("updatedAt") ? obj.get("updatedAt").getAsLong() : 0));
                    } catch (RuntimeException ignored) {
                        // one bad entry does not hide the rest
                    }
                }
                fetched.sort((a, b) -> Long.compare(b.updatedAt, a.updatedAt));
                online = Collections.unmodifiableList(fetched);
                fetchedOnce = true;
            } catch (Exception e) {
                error("Could not reach the config server: " + e.getMessage());
            } finally {
                fetching = false;
            }
        }, "Arsenic config fetch").start();
    }

    private void upload() {
        String name = ConfigManager.sanitizeName(uploadField.value());
        if (name.isEmpty()) {
            error("Enter a name to share the config as");
            focus(uploadField);
            return;
        }
        // the json is made here, on the game thread; only the request runs on its own thread
        String json;
        try {
            configManager.saveConfig();
            json = configManager.exportConfig(null);
        } catch (IllegalArgumentException e) {
            error(e.getMessage());
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("author", author());
        body.addProperty("name", name);
        body.add("config", new JsonParser().parse(json));

        uploading = false;
        unfocus();
        info("Uploading " + name + "...");
        new Thread(() -> {
            try {
                HttpURLConnection conn = openConnection("/api/upload");
                conn.setRequestMethod("PUT");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                int code = conn.getResponseCode();
                if (code == 200) {
                    info("Shared " + name);
                    fetchedOnce = false;
                    fetch();
                } else {
                    error("Upload failed (" + code + ")");
                }
            } catch (Exception e) {
                error("Upload failed: " + e.getMessage());
            }
        }, "Arsenic config upload").start();
    }

    /** The json came with the list, so this only saves it (and loads it, for "Use"). */
    private void download(OnlineConfig config, boolean use) {
        try {
            String name = configManager.importConfig(config.configJson, config.name, false);
            if (use && configManager.loadConfig(name))
                info("Loaded " + name + " from " + config.author);
            else
                info("Saved " + name + " to My configs");
        } catch (IllegalArgumentException e) {
            error(e.getMessage());
        }
        refreshLocalList();
    }

    private HttpURLConnection openConnection(String path) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(SERVER_URL + path).openConnection();
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        return conn;
    }

    private static String readStream(InputStream stream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null)
                result.append(line).append('\n');
            return result.toString();
        }
    }

    /* ================================================================== input */

    @Override
    public void clickChildren(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0)
            return;
        int before = focusCount;
        for (Hit hit : new ArrayList<>(hits)) {
            if (hit.contains(mouseX, mouseY)) {
                hit.action.run();
                SoundUtils.chordEnum();
                // a click that did not (re)focus a field ends typing in the focused one
                if (focusCount == before && focused != null)
                    blur(focused);
                return;
            }
        }
        if (focused != null)
            blur(focused);
    }

    /** Goes up on every focus(), so a click can tell whether it focused a field. */
    private int focusCount;

    private void focus(Field field) {
        focusCount++;
        focused = field;
        Arsenic.getArsenic().getClickGuiScreen().setAlwaysInputComponent(this);
    }

    /** Clicking away from a field: rename is cancelled, the others just stop taking keys. */
    private void blur(Field field) {
        if (field == renameField)
            renaming = null;
        unfocus();
    }

    private void unfocus() {

        if (focused != null) {
            focused = null;
            if (Arsenic.getArsenic().getClickGuiScreen() != null)
                Arsenic.getArsenic().getClickGuiScreen().setAlwaysInputComponent(null);
        }
    }

    @Override
    public void setNotAlwaysRecieveInput() {
        if (focused == renameField)
            renaming = null;
        focused = null;

    }

    @Override
    public boolean recieveInput(int key) {
        Field field = focused;
        if (field == null)
            return false;
        boolean ctrl = Keys.isKeyDown(InputConstants.KEY_LCONTROL) || Keys.isKeyDown(InputConstants.KEY_RCONTROL)
                || Keys.isKeyDown(InputConstants.KEY_LGUI) || Keys.isKeyDown(InputConstants.KEY_RGUI);

        switch (key) {
            case InputConstants.KEY_RETURN:
            case InputConstants.KEY_NUMPADENTER:
                if (field.onSubmit != null)
                    field.onSubmit.run();
                return true;
            case InputConstants.KEY_ESCAPE:
                Runnable cancel = field.onCancel;
                unfocus();
                if (cancel != null)
                    cancel.run();
                return true;
            case InputConstants.KEY_BACKSPACE:
                if (ctrl) {
                    // delete the last word
                    String s = field.text.toString().replaceAll("\\s+$", "");
                    int cut = s.lastIndexOf(' ');
                    field.set(cut < 0 ? "" : s.substring(0, cut + 1));
                } else if (field.text.length() > 0) {
                    field.text.deleteCharAt(field.text.length() - 1);
                }
                return true;
            default:
                break;
        }
        if (ctrl && key == InputConstants.KEY_V) {
            String paste = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (paste != null)
                for (char c : paste.toCharArray())
                    type(field, c);
            return true;
        }
        if (ctrl && key == InputConstants.KEY_A) {
            field.set("");
            return true;
        }
        return true;
    }

    /** Typed characters arrive separately from key presses. */
    @Override
    public boolean recieveChar(char c) {
        if (focused == null)
            return false;
        type(focused, c);
        return true;
    }

    private static void type(Field field, char c) {
        if (field.text.length() >= ConfigManager.MAX_NAME_LENGTH)
            return;
        if (StringUtil.isAllowedChatCharacter(c) && c != '§')
            field.text.append(c);
    }
}
