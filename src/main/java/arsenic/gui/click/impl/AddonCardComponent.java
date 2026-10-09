package arsenic.gui.click.impl;

import arsenic.addon.AddonManager;
import arsenic.gui.click.Component;
import arsenic.gui.click.UITheme;
import arsenic.gui.themes.ThemeManager;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.render.DrawUtils;
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

import java.util.ArrayList;
import java.util.List;

/**
 * The full-width blocks of the Addon Manager: the selected pack's header (icon, description and how many addons are
 * on), a plain section header, or a load error. Addons are drawn as module rows ({@link ModuleComponent}).
 */
public class AddonCardComponent extends Component {

    private enum Kind { PACK, SECTION, ERROR }

    private final Kind kind;
    private final AddonManager.PackInfo pack;
    private final String title;
    private final String text;

    private float pixelWidth = 100f;
    private float contentH = 1f;
    private float pad, lh, icon;
    private List<String> lines = new ArrayList<>();

    private AddonCardComponent(Kind kind, AddonManager.PackInfo pack, String title, String text) {
        this.kind = kind;
        this.pack = pack;
        this.title = title;
        this.text = text;
    }

    public static AddonCardComponent pack(AddonManager.PackInfo pack) {
        return new AddonCardComponent(Kind.PACK, pack, null, null);
    }

    public static AddonCardComponent section(String title, String text) {
        return new AddonCardComponent(Kind.SECTION, null, title, text);
    }

    public static AddonCardComponent error(String text) {
        return new AddonCardComponent(Kind.ERROR, null, "Error", text);
    }

    /** Width of the block; they are as wide as the addon column. */
    public void setPixelWidth(float w) {
        this.pixelWidth = w;
    }

    @Override
    public int getWidth(int i) {
        return (int) pixelWidth;
    }

    @Override
    public int getHeight(int i) {
        return (int) Math.ceil(contentH);
    }

    @Override
    public float updateComponent(arsenic.utils.render.PosInfo pi, RenderInfo ri) {
        contentH = measure(ri);
        return super.updateComponent(pi, ri);
    }

    private String body() {
        if (kind == Kind.PACK)
            return pack.meta.description;
        return text == null ? "" : text;
    }

    /** Line wrapping and height of the block for the current gui size. */
    private float measure(RenderInfo ri) {
        FontRendererExtension<?> fr = ri.getFr();
        float gh = ri.getGuiScreen().height;
        pad = gh / 100f * 1.5f;
        lh = fr.getHeight("Ag") + 1.5f;
        icon = lh * 2.4f;
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
        height = contentH;
        y2 = y1 + height;
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
        fr.drawString(pack.meta.name, tx, iy + icon * 0.3f, UITheme.textPrimary(), fr.CENTREY);
        String status = enabled + " of " + pack.addons.size() + " on" + (pack.installed ? "" : "  (not installed)");
        fr.drawString(status, tx, iy + icon * 0.74f, enabled > 0 ? UITheme.accent() : UITheme.textMuted(), fr.CENTREY);

        float y = iy + icon + pad * 0.6f;
        for (int i = 0; i < lines.size(); i++)
            fr.drawString(lines.get(i), x1 + pad * 1.25f, y + i * lh + lh / 2f, UITheme.textSecondary(), fr.CENTREY);
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
