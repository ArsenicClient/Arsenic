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
import arsenic.utils.render.PosInfo;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.render.RenderUtils;
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

/** One card on the addon page: an addon, an addon pack, or a load error. Sized and styled like a module card. */
public class AddonCardComponent extends Component {

    private enum Kind { PACK, ADDON, ERROR }

    private final Kind kind;
    private final AddonManager.Info info;
    private final AddonManager.PackInfo pack;
    private final String errorText;
    /** Opens the pack's own page when its card is clicked; null on a pack's own page. */
    private final Runnable onOpen;
    private float widthSteps = 30f;

    private float contentH = 1f;
    private float pad, rowH, lh, icon, btnH;
    private List<String> lines = new ArrayList<>();
    private float btnX1, btnY1, btnX2, btnY2;

    private AddonCardComponent(Kind kind, AddonManager.Info info, AddonManager.PackInfo pack, String errorText, Runnable onOpen) {
        this.kind = kind;
        this.onOpen = onOpen;
        this.info = info;
        this.pack = pack;
        this.errorText = errorText;
    }

    public static AddonCardComponent addon(AddonManager.Info info) {
        return new AddonCardComponent(Kind.ADDON, info, null, null, null);
    }

    public static AddonCardComponent pack(AddonManager.PackInfo pack, Runnable onOpen) {
        return new AddonCardComponent(Kind.PACK, null, pack, null, onOpen);
    }

    public static AddonCardComponent error(String text) {
        return new AddonCardComponent(Kind.ERROR, null, null, text, null);
    }

    public void setWidthSteps(float steps) {
        this.widthSteps = steps;
    }

    public boolean isError() {
        return kind == Kind.ERROR;
    }

    public String getName() {
        return kind == Kind.PACK ? pack.meta.name : kind == Kind.ADDON ? info.name : "Error";
    }

    public boolean matches(String query) {
        if (kind == Kind.ERROR)
            return false;
        if (contains(getName(), query) || contains(description(), query))
            return true;
        if (kind == Kind.ADDON && info.pack != null && contains(info.pack.name, query))
            return true;
        if (kind == Kind.PACK)
            for (AddonManager.Info addon : pack.addons)
                if (contains(addon.name, query) || contains(addon.description, query))
                    return true;
        return false;
    }

    private static boolean contains(String text, String query) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(query);
    }

    private String description() {
        if (kind == Kind.ADDON && !info.requires.isEmpty())
            return info.description + " Needs " + String.join(", ", info.requires) + ".";
        return kind == Kind.PACK ? pack.meta.description : kind == Kind.ADDON ? info.description : errorText;
    }

    private boolean isOn() {
        return kind == Kind.PACK ? pack.allEnabled() : kind == Kind.ADDON && info.state == AddonManager.State.ENABLED;
    }

    @Override
    public int getWidth(int i) {
        return (int) UITheme.space(i, widthSteps);
    }

    @Override
    public int getHeight(int i) {
        return (int) Math.ceil(contentH);
    }

    @Override
    public float updateComponent(PosInfo pi, RenderInfo ri) {
        contentH = measure(ri);
        return super.updateComponent(pi, ri);
    }

    /** Works out the line wrapping and the card height for the current gui size. */
    private float measure(RenderInfo ri) {
        FontRendererExtension<?> fr = ri.getFr();
        float gh = ri.getGuiScreen().height;
        float w = (int) UITheme.space(ri.getGuiScreen().width, widthSteps);
        pad = gh / 100f * 1.6f;
        rowH = gh / 100f * 5f;
        lh = fr.getHeight("Ag") + 1.5f;
        icon = rowH * 1.5f;
        btnH = rowH * 0.62f;

        float textW = w - pad * 2.5f;
        lines = wrap(fr, description(), textW);
        switch (kind) {
            case PACK:
                return pad + icon + pad * 0.5f + lines.size() * lh + pad * 0.5f + btnH + pad;
            case ADDON:
                return rowH + lines.size() * lh + pad * 0.9f;
            default:
                return pad + lh + lines.size() * lh + pad * 0.8f;
        }
    }

    @Override
    protected float drawComponent(RenderInfo ri) {
        FontRendererExtension<?> fr = ri.getFr();
        height = contentH;
        y2 = y1 + height;

        float hover = hoverPct();
        float radius = UITheme.radiusCard(rowH);
        UITheme.surface(x1, y1, x2, y2, radius, ThemeManager.getModuleBackground(),
                UITheme.Elevation.RAISED, Math.min(1f, hover * 0.7f + 0.3f));
        UITheme.hoverWash(x1, y1, x2, y2, radius, hover);

        float barW = Math.max(1.5f, rowH * 0.07f);
        if (kind == Kind.ERROR)
            DrawUtils.drawRoundedRect(x1, y1, x1 + barW, y2, radius, 0xFFFF5555);
        else if (isOn())
            UITheme.accentBar(x1, y1, x1 + barW, y2, radius, 1f);

        btnX1 = btnX2 = btnY1 = btnY2 = 0;
        switch (kind) {
            case PACK:
                drawPack(ri, fr);
                break;
            case ADDON:
                drawAddon(ri, fr);
                break;
            default:
                drawError(fr);
        }
        RenderUtils.resetColorText();
        return height;
    }

    private void drawAddon(RenderInfo ri, FontRendererExtension<?> fr) {
        boolean on = isOn();
        float midY = y1 + rowH / 2f;
        String label = info.state == AddonManager.State.ENABLED ? "Uninstall" : "Install";

        float right = x2 - pad;
        float bw = drawPill(ri, fr, label, right, midY, !on);
        right -= bw + pad * 0.5f;

        if (info.pack != null) {
            String chip = info.pack.name;
            float chipH = btnH * 0.8f;
            float w = UITheme.chip(fr, chip, right, midY, chipH, UITheme.textSecondary(),
                    UITheme.alpha(ThemeManager.getBlack(), 70));
            right -= w + pad * 0.4f;
        }

        float tx = x1 + pad * 1.25f;
        int title = UITheme.mix(UITheme.textSecondary(), UITheme.textPrimary(), on ? 1f : 0f);
        fr.drawString(fit(fr, info.name, right - tx), tx, midY, title, fr.CENTREY);
        drawLines(fr, tx, y1 + rowH);
    }

    private void drawPack(RenderInfo ri, FontRendererExtension<?> fr) {
        float ix = x1 + pad * 1.25f, iy = y1 + pad;
        drawIcon(pack.meta.icon, ix, iy, icon);

        float tx = ix + icon + pad * 0.8f;
        int enabled = pack.enabledCount();
        boolean all = pack.allEnabled();
        fr.drawString(pack.meta.name, tx, iy + icon * 0.32f, UITheme.textPrimary(), fr.CENTREY);
        fr.drawString(enabled + "/" + pack.addons.size() + " installed", tx, iy + icon * 0.72f,
                all ? UITheme.accent() : UITheme.textMuted(), fr.CENTREY);

        drawLines(fr, x1 + pad * 1.25f, iy + icon + pad * 0.5f);
        float btnMid = y2 - pad - btnH / 2f;
        drawPill(ri, fr, all ? "Uninstall" : "Install", x2 - pad, btnMid, !all);
    }

    private void drawError(FontRendererExtension<?> fr) {
        float tx = x1 + pad * 1.25f;
        fr.drawString("Error", tx, y1 + pad + lh / 2f, 0xFFFF5555, fr.CENTREY);
        float y = y1 + pad + lh;
        for (int i = 0; i < lines.size(); i++)
            fr.drawString(lines.get(i), tx, y + i * lh + lh / 2f, 0xFFFF8888, fr.CENTREY);
    }

    private void drawLines(FontRendererExtension<?> fr, float x, float top) {
        for (int i = 0; i < lines.size(); i++)
            fr.drawString(lines.get(i), x, top + i * lh + lh / 2f, UITheme.textMuted(), fr.CENTREY);
    }

    /** A pill button with its right edge at {@code rightX}; remembers its area for clicks. Returns its width. */
    private float drawPill(RenderInfo ri, FontRendererExtension<?> fr, String label, float rightX, float midY, boolean primary) {
        float w = Math.max(fr.getWidth("Uninstall"), fr.getWidth(label)) + btnH * 1.1f;
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
        RenderUtils.resetColorText();
        return w;
    }

    /** A texture path (textures/...) is drawn flat; anything else is an item id drawn like an inventory slot. */
    private void drawIcon(String icon, float x, float y, float size) {
        if (icon == null)
            return;
        if (icon.startsWith("textures/"))
            drawTexture(icon, x, y, size);
        else
            drawItem(icon, x, y, size);
    }

    /** Renders an item exactly as the game does in an inventory slot, e.g. minecraft:dirt or minecraft:wool@14. */
    private void drawItem(String id, float x, float y, float size) {
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

    private void drawTexture(String path, float x, float y, float size) {
        if (path == null)
            return;
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
        if (kind == Kind.ERROR)
            return;
        boolean onButton = btnX2 > btnX1 && MathUtils.inside(mouseX, mouseY, btnX1, btnY1, btnX2, btnY2);
        if (kind == Kind.PACK && onOpen != null && !(onButton && mouseButton == 0)) {
            // left or right click anywhere else on a pack card opens it
            SoundUtils.chordOpen();
            onOpen.run();
            return;
        }
        if (mouseButton != 0 || !onButton)
            return;

        SoundUtils.chordClick();
        AddonManager manager = Arsenic.getArsenic().getAddonManager();
        try {
            if (kind == Kind.PACK)
                manager.setPackEnabled(pack, !pack.allEnabled());
            else
                manager.setEnabled(info, info.state != AddonManager.State.ENABLED);
        } catch (IOException e) {
            Arsenic.getArsenic().getLogger().error("Could not change " + getName(), e);
        }
        try {
            manager.reload();
        } catch (Throwable t) {
            Arsenic.getArsenic().getLogger().error("Addon reload failed", t);
        }
    }

    @Override
    protected void playClickSound() {
        // the button plays its own sound; clicking the card body is silent
    }

    private static List<String> wrap(FontRendererExtension<?> fr, String text, float maxWidth) {
        List<String> lines = new ArrayList<>();
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

    private static String fit(FontRendererExtension<?> fr, String text, float maxWidth) {
        String t = text;
        while (t.length() > 3 && fr.getWidth(t) > maxWidth)
            t = t.substring(0, t.length() - 1);
        return t;
    }
}
