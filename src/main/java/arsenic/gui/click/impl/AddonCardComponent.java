package arsenic.gui.click.impl;

import arsenic.addon.AddonManager;
import arsenic.gui.click.Component;
import arsenic.gui.click.UITheme;
import arsenic.gui.themes.ThemeManager;
import arsenic.main.Arsenic;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.java.MathUtils;
import arsenic.utils.java.SoundUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.render.ScissorUtils;
import arsenic.utils.timer.AnimationTimer;
import arsenic.utils.timer.TickMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One block in the Addon Manager. An addon is drawn exactly like a module row (chevron, name, the module on/off
 * switch); opening it with the chevron or a right click shows its description, its pack and what it needs. The page
 * also uses this class for the full-width blocks above the rows: the selected pack's header (icon, description,
 * Enable all / Disable all), a plain section header, or a load error.
 */
public class AddonCardComponent extends Component {

    private enum Kind { PACK, SECTION, ADDON, ERROR }

    private final Kind kind;
    private AddonManager.Info info;
    private final AddonManager.PackInfo pack;
    private final String title;
    private final String text;
    /** Show which pack an addon belongs to (in the All view and search results). */
    private boolean showPack;

    private float pixelWidth = 100f;
    private float contentH = 1f;
    private float pad, lh, icon, btnH;
    private List<String> lines = new ArrayList<>();
    private float btnX1, btnY1, btnX2, btnY2;

    // addon rows
    private boolean on, open;
    private float chevronZoneX2, toggleZoneX1, contentHeight;
    private final AnimationTimer openTimer = new AnimationTimer(UITheme.DUR_EXPAND, () -> open, TickMode.CUBIC);
    private final AnimationTimer enabledTimer = new AnimationTimer(UITheme.DUR_TOGGLE, () -> on, TickMode.CUBIC);
    private arsenic.utils.render.PosInfo posInfo;
    private final ButtonComponent switchComponent = new ButtonComponent(this) {
        @Override
        protected boolean isEnabled() {
            return on;
        }

        @Override
        protected void setEnabled(boolean enabled) {
            on = enabled;
            AddonManager manager = Arsenic.getArsenic().getAddonManager();
            try {
                manager.setEnabled(info, enabled);
            } catch (IOException e) {
                Arsenic.getArsenic().getLogger().error("Could not change " + info.name, e);
            }
            reload(manager);
        }

        @Override
        public int getWidth(int i) {
            return (int) (super.getWidth(i) * 0.95);
        }
    };

    private AddonCardComponent(Kind kind, AddonManager.Info info, AddonManager.PackInfo pack, String title, String text) {
        this.kind = kind;
        this.info = info;
        this.pack = pack;
        this.title = title;
        this.text = text;
        this.on = info != null && info.state == AddonManager.State.ENABLED;
    }

    public static AddonCardComponent addon(AddonManager.Info info) {
        return new AddonCardComponent(Kind.ADDON, info, null, null, null);
    }

    public static AddonCardComponent pack(AddonManager.PackInfo pack) {
        return new AddonCardComponent(Kind.PACK, null, pack, null, null);
    }

    public static AddonCardComponent section(String title, String text) {
        return new AddonCardComponent(Kind.SECTION, null, null, title, text);
    }

    public static AddonCardComponent error(String text) {
        return new AddonCardComponent(Kind.ERROR, null, null, "Error", text);
    }

    /** Points a reused addon row at fresh data after a reload; the switch animates from its old position. */
    public void update(AddonManager.Info info) {
        this.info = info;
        this.on = info.state == AddonManager.State.ENABLED;
    }

    public void setShowPack(boolean showPack) {
        this.showPack = showPack;
    }

    /** Width of a full-width block (pack header, section, error); addon rows are as wide as a module row. */
    public void setPixelWidth(float w) {
        this.pixelWidth = w;
    }

    public boolean isRow() {
        return kind == Kind.ADDON;
    }

    public boolean matches(String query) {
        if (kind != Kind.ADDON)
            return false;
        return contains(info.name, query) || contains(info.description, query)
                || (info.pack != null && contains(info.pack.name, query));
    }

    private static boolean contains(String text, String query) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(query);
    }

    @Override
    public int getWidth(int i) {
        return kind == Kind.ADDON ? (int) UITheme.space(i, 30) : (int) pixelWidth;
    }

    @Override
    public int getHeight(int i) {
        return kind == Kind.ADDON ? super.getHeight(i) : (int) Math.ceil(contentH);
    }

    @Override
    public float updateComponent(arsenic.utils.render.PosInfo pi, RenderInfo ri) {
        posInfo = pi;
        if (kind != Kind.ADDON)
            contentH = measure(ri);
        return super.updateComponent(pi, ri);
    }

    private String body() {
        switch (kind) {
            case PACK: return pack.meta.description;
            case ADDON: return info.description;
            default: return text == null ? "" : text;
        }
    }

    /** Line wrapping and height of a full-width block for the current gui size. */
    private float measure(RenderInfo ri) {
        FontRendererExtension<?> fr = ri.getFr();
        float gh = ri.getGuiScreen().height;
        pad = gh / 100f * 1.5f;
        lh = fr.getHeight("Ag") + 1.5f;
        icon = lh * 2.4f;
        btnH = lh * 1.35f;
        switch (kind) {
            case PACK:
                lines = wrap(fr, body(), pixelWidth - pad * 2.5f);
                return pad + icon + pad * 0.6f + lines.size() * lh + pad;
            case SECTION:
                lines = wrap(fr, body(), pixelWidth - pad * 2f);
                return pad * 0.6f + lh * (1 + lines.size()) + pad * 0.4f;
            default:
                lines = wrap(fr, body(), pixelWidth - pad * 2.5f);
                return pad * 0.8f + lh * (1 + lines.size()) + pad * 0.8f;
        }
    }

    @Override
    protected float drawComponent(RenderInfo ri) {
        FontRendererExtension<?> fr = ri.getFr();
        if (kind == Kind.ADDON)
            return drawAddon(ri, fr);
        height = contentH;
        y2 = y1 + height;
        btnX1 = btnX2 = btnY1 = btnY2 = 0;
        if (kind == Kind.PACK)
            drawPack(ri, fr);
        else if (kind == Kind.SECTION)
            drawSection(fr);
        else
            drawError(fr);
        RenderUtils.resetColorText();
        return height;
    }

    private void card() {
        UITheme.surface(x1, y1, x2, y2, UITheme.radiusCard(lh * 2f), ThemeManager.getModuleBackground(),
                UITheme.Elevation.RAISED, 0.3f);
    }

    private void drawPack(RenderInfo ri, FontRendererExtension<?> fr) {
        card();
        float ix = x1 + pad * 1.25f, iy = y1 + pad;
        drawIcon(pack.meta.icon, ix, iy, icon);

        float tx = ix + icon + pad * 0.8f;
        int enabled = pack.enabledCount();
        boolean all = pack.allEnabled();
        fr.drawString(pack.meta.name, tx, iy + icon * 0.3f, UITheme.textPrimary(), fr.CENTREY);
        String status = enabled + " of " + pack.addons.size() + " on" + (pack.installed ? "" : "  (not installed)");
        fr.drawString(status, tx, iy + icon * 0.74f, enabled > 0 ? UITheme.accent() : UITheme.textMuted(), fr.CENTREY);

        drawPill(ri, fr, all ? "Disable all" : "Enable all", x2 - pad, iy + icon / 2f, !all);

        float y = iy + icon + pad * 0.6f;
        for (int i = 0; i < lines.size(); i++)
            fr.drawString(lines.get(i), x1 + pad * 1.25f, y + i * lh + lh / 2f, UITheme.textSecondary(), fr.CENTREY);
    }

    /** Drawn like ModuleComponent: same card, accent bar, chevron, title colour and switch. */
    private float drawAddon(RenderInfo ri, FontRendererExtension<?> fr) {
        float enabledPct = enabledTimer.getPercent();
        openTimer.setMaxMs(UITheme.expandDuration(contentHeight));
        float openPct = openTimer.getPercent();
        float hover = hoverPct();

        float rowPad = height * 0.42f;
        float radius = UITheme.radiusCard(height);
        float cardY2 = y2 + expandY;

        float elevation = Math.max(hover * 0.7f, Math.max(openPct * 0.5f, enabledPct * 0.35f)) + 0.3f;
        UITheme.surface(x1, y1, x2, cardY2, radius, ThemeManager.getModuleBackground(),
                UITheme.Elevation.RAISED, Math.min(1f, elevation));
        UITheme.hoverWash(x1, y1, x2, cardY2, radius, hover);
        if (enabledPct > 0.01f)
            UITheme.accentBar(x1, y1, x1 + Math.max(1.5f, height * 0.07f), cardY2, radius, enabledPct);

        float chevronCx = x1 + rowPad * 1.25f;
        chevronZoneX2 = chevronCx + rowPad;
        int chevronColor = UITheme.alpha(UITheme.textMuted(), (int) (140 + 115 * Math.max(hover, openPct)));
        UITheme.chevron(chevronCx, midPointY, height * 0.26f, Math.max(1f, height * 0.055f), chevronColor, openPct);

        switchComponent.updateComponent(posInfo, ri);
        toggleZoneX1 = Math.min(x2, switchComponent.getTrackX1()) - rowPad * 0.5f;
        RenderUtils.resetColorText();

        float textX = chevronZoneX2 + rowPad * 0.35f;
        float chipRight = toggleZoneX1 - rowPad * 0.6f;
        if (showPack && info.pack != null) {
            float chipH = height * 0.46f;
            String chip = fit(fr, info.pack.name, Math.max(10f, (chipRight - textX) * 0.45f));
            chipRight -= UITheme.chip(fr, chip, chipRight, midPointY, chipH,
                    UITheme.alpha(UITheme.textMuted(), 200), UITheme.alpha(ThemeManager.getBlack(), 60)) + rowPad * 0.4f;
        }
        int titleColor = UITheme.mix(UITheme.textSecondary(), UITheme.textPrimary(), enabledPct);
        fr.drawString(fit(fr, info.name, chipRight - textX), textX, midPointY, titleColor, fr.CENTREY);
        RenderUtils.resetColorText();

        // the dropdown: description, pack, what it needs
        float lineH = fr.getHeight("Ag") + 1.5f;
        float innerW = x2 - x1 - rowPad * 2.2f;
        List<String> drop = new ArrayList<>(wrap(fr, info.description, innerW));
        boolean packLine = showPack && info.pack != null;
        if (packLine)
            drop.add("Pack: " + info.pack.name);
        if (!info.requires.isEmpty())
            drop.add("Needs: " + String.join(", ", info.requires));
        contentHeight = rowPad * 1.2f + drop.size() * lineH;
        if (openPct > 0.001f) {
            UITheme.divider(x1 + rowPad, y2, x2 - rowPad, openPct);
            ScissorUtils.subScissor((int) x1, (int) y2, (int) x2, (int) (y2 + expandY), 2);
            float y = y2 + rowPad * 0.5f;
            int descLines = drop.size() - (packLine ? 1 : 0) - (info.requires.isEmpty() ? 0 : 1);
            for (int i = 0; i < drop.size(); i++) {
                int color = i < descLines ? UITheme.textSecondary() : UITheme.alpha(UITheme.accent(), 220);
                fr.drawString(drop.get(i), x1 + rowPad * 1.1f, y + lineH / 2f, UITheme.alpha(color, (int) (255 * openPct)), fr.CENTREY);
                y += lineH;
            }
            ScissorUtils.endSubScissor();
        }
        expandY = contentHeight * openPct;
        RenderUtils.resetColorText();
        return expandY + height;
    }

    private void drawSection(FontRendererExtension<?> fr) {
        float tx = x1 + pad * 0.4f;
        float y = y1 + pad * 0.6f;
        fr.drawString(title, tx, y + lh / 2f, UITheme.textPrimary(), fr.CENTREY);
        y += lh;
        for (String line : lines) {
            fr.drawString(line, tx, y + lh / 2f, UITheme.textMuted(), fr.CENTREY);
            y += lh;
        }
    }

    private void drawError(FontRendererExtension<?> fr) {
        card();
        DrawUtils.drawRoundedRect(x1, y1, x1 + Math.max(1.5f, lh * 0.18f), y2, UITheme.radiusCard(lh * 2f), 0xFFFF5555);
        float tx = x1 + pad * 1.25f;
        float y = y1 + pad * 0.8f;
        fr.drawString(title, tx, y + lh / 2f, 0xFFFF5555, fr.CENTREY);
        for (int i = 0; i < lines.size(); i++)
            fr.drawString(lines.get(i), tx, y + lh + i * lh + lh / 2f, 0xFFFF8888, fr.CENTREY);
    }

    /** A pill button with its right edge at {@code rightX}; remembers its area for clicks. */
    private void drawPill(RenderInfo ri, FontRendererExtension<?> fr, String label, float rightX, float midY, boolean primary) {
        float w = Math.max(fr.getWidth("Disable all"), fr.getWidth(label)) + btnH * 1.1f;
        btnX1 = rightX - w;
        btnX2 = rightX;
        btnY1 = midY - btnH / 2f;
        btnY2 = midY + btnH / 2f;
        boolean over = MathUtils.inside(ri.getMouseX(), ri.getMouseY(), btnX1, btnY1, btnX2, btnY2);
        float radius = btnH / 2f;
        if (primary) {
            DrawUtils.drawGradientRoundedRect(btnX1, btnY1, btnX2, btnY2, radius,
                    UITheme.accent(), UITheme.accent(), UITheme.accentAlt(), UITheme.accentAlt());
            DrawUtils.drawRoundedOutline(btnX1, btnY1, btnX2, btnY2, radius, 1f,
                    UITheme.alpha(ThemeManager.getWhite(), over ? 150 : 60));
            fr.drawString(label, (btnX1 + btnX2) / 2f, midY, ThemeManager.getWhite(), fr.CENTREX, fr.CENTREY);
        } else {
            UITheme.surface(btnX1, btnY1, btnX2, btnY2, radius, UITheme.alpha(0x000000, over ? 190 : 150),
                    UITheme.Elevation.RAISED, 0.6f);
            DrawUtils.drawRoundedOutline(btnX1, btnY1, btnX2, btnY2, radius, 1f,
                    UITheme.alpha(ThemeManager.getWhite(), over ? 150 : 70));
            fr.drawString(label, (btnX1 + btnX2) / 2f, midY,
                    over ? ThemeManager.getWhite() : UITheme.alpha(ThemeManager.getWhite(), 200), fr.CENTREX, fr.CENTREY);
        }
    }

    /** A texture path (textures/...) is drawn flat; anything else is an item id drawn like an inventory slot. */
    public static void drawIcon(String icon, float x, float y, float size) {
        if (icon == null)
            return;
        if (icon.startsWith("textures/"))
            drawTexture(icon, x, y, size);
        else
            drawItem(icon, x, y, size);
    }

    /** Renders an item exactly as the game does in an inventory slot, e.g. minecraft:dirt or minecraft:wool@14. */
    private static void drawItem(String id, float x, float y, float size) {
        try {
            String name = id;
            int meta = 0;
            int at = name.indexOf('@');
            if (at >= 0) {
                meta = Integer.parseInt(name.substring(at + 1));
                name = name.substring(0, at);
            }
            Item item = (Item) Item.itemRegistry.getObject(new ResourceLocation(name));
            if (item == null)
                return;
            ItemStack stack = new ItemStack(item, 1, meta);
            GlStateManager.pushMatrix();
            GlStateManager.translate(x, y, 0f);
            GlStateManager.scale(size / 16f, size / 16f, 1f);
            RenderHelper.enableGUIStandardItemLighting();
            Minecraft.getMinecraft().getRenderItem().renderItemAndEffectIntoGUI(stack, 0, 0);
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableRescaleNormal();
            GlStateManager.disableLighting();
            GlStateManager.popMatrix();
            GlStateManager.color(1f, 1f, 1f, 1f);
        } catch (Exception ignored) {
            // unknown item: leave the space empty
        }
    }

    private static void drawTexture(String path, float x, float y, float size) {
        try {
            Minecraft.getMinecraft().getTextureManager().bindTexture(new ResourceLocation(path));
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GlStateManager.enableBlend();
            GlStateManager.color(1f, 1f, 1f, 1f);
            Gui.drawModalRectWithCustomSizedTexture((int) x, (int) y, 0, 0, (int) size, (int) size, size, size);
        } catch (Exception ignored) {
            // missing texture: leave the space empty
        }
    }

    @Override
    protected void clickComponent(int mouseX, int mouseY, int mouseButton) {
        if (kind == Kind.ADDON) {
            // same zones as a module row: right click or the chevron opens it, the switch turns it on or off
            if ((mouseButton == 1 && mouseX < toggleZoneX1) || mouseX <= chevronZoneX2) {
                open = !open;
                SoundUtils.chordOpen();
                return;
            }
            switchComponent.handleClick(mouseX, mouseY, mouseButton);
            return;
        }
        if (kind != Kind.PACK || mouseButton != 0 || btnX2 <= btnX1 || !MathUtils.inside(mouseX, mouseY, btnX1, btnY1, btnX2, btnY2))
            return;
        SoundUtils.chordClick();
        AddonManager manager = Arsenic.getArsenic().getAddonManager();
        try {
            manager.setPackEnabled(pack, !pack.allEnabled());
        } catch (IOException e) {
            Arsenic.getArsenic().getLogger().error("Could not change " + pack.meta.name, e);
        }
        reload(manager);
    }

    private static void reload(AddonManager manager) {
        try {
            manager.reload();
        } catch (Throwable t) {
            Arsenic.getArsenic().getLogger().error("Addon reload failed", t);
        }
    }

    @Override
    protected void playClickSound() {
        // the switch and the button play their own sounds
    }

    static List<String> wrap(FontRendererExtension<?> fr, String text, float maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty())
            return lines;
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (line.length() > 0 && fr.getWidth(candidate) > maxWidth) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (line.length() > 0)
            lines.add(line.toString());
        return lines;
    }

    static String fit(FontRendererExtension<?> fr, String text, float maxWidth) {
        String t = text;
        while (t.length() > 3 && fr.getWidth(t) > maxWidth)
            t = t.substring(0, t.length() - 1);
        return t;
    }
}
