package arsenic.gui.click.impl;

import arsenic.gui.click.GuiStyle;
import arsenic.gui.click.UITheme;
import arsenic.gui.themes.Theme;
import arsenic.gui.themes.ThemeManager;
import arsenic.main.Arsenic;
import arsenic.module.ModuleCategory;
import arsenic.utils.interfaces.IAlwaysKeyboardInput;
import arsenic.utils.java.SoundUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.PosInfo;
import arsenic.utils.render.RenderInfo;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

public class GuiComponent extends ModuleCategoryComponent implements IAlwaysKeyboardInput {

    private static final class Hit {
        float x1, y1, x2, y2;
        final Runnable action;

        Hit(float x1, float y1, float x2, float y2, Runnable action) {
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
            this.action = action;
        }

        boolean contains(float mx, float my) {
            return mx >= x1 && mx <= x2 && my >= y1 && my <= y2;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    /** True after the keybind row is clicked, until the next key is pressed. */
    private boolean binding;

    private static final float BOTTOM_MARGIN = 28f;

    public GuiComponent() {
        super(ModuleCategory.GUI);
    }

    @Override
    public void drawLeft(PosInfo pi, RenderInfo ri) {
        hits.clear();

        scroll += (targetScroll - scroll) * GuiStyle.scrollEase();
        if (Math.abs(targetScroll - scroll) < 0.5f)
            scroll = targetScroll;

        float x = pi.getX();
        float contentTop = pi.getY() + scroll + 12;
        float y = contentTop;
        float maxX = ri.getGuiScreen().width * 7 / 8f - 10;
        float rowW = maxX - x - 10;
        float mx = ri.getMouseX(), my = ri.getMouseY();

        drawSectionLabel("Appearance", x + 5, y, ri);
        y += 16;

        for (GuiStyle.Preset preset : GuiStyle.Preset.values()) {
            float cardX = x + 5;
            float cardH = 42;
            boolean active = GuiStyle.get().getPreset() == preset;
            boolean hovered = mx >= cardX && mx <= cardX + rowW && my >= y && my <= y + cardH;

            int fill = active
                    ? UITheme.mix(ThemeManager.getConfigsCard(), UITheme.accent(), 0.10f)
                    : ThemeManager.getConfigsCard();
            UITheme.surface(cardX, y, cardX + rowW, y + cardH, 10f, fill,
                    active || hovered ? UITheme.Elevation.RAISED : UITheme.Elevation.FLAT,
                    active ? 1f : 0.6f);
            DrawUtils.drawRoundedOutline(cardX, y, cardX + rowW, y + cardH, 10f, active ? 1.4f : 1f,
                    active ? UITheme.alpha(UITheme.accent(), 190)
                           : (hovered ? ThemeManager.getConfigsHoverBorder() : ThemeManager.getConfigsCardBorder()));

            if (active)
                UITheme.accentBar(cardX, y, cardX + 3, y + cardH, 10f, 1f);

            ri.getFr().drawString(preset.label, cardX + 14, y + 12,
                    active ? ThemeManager.getTextPrimary() : ThemeManager.getTextSecondary());
            ri.getFr().drawString(preset.description, cardX + 14, y + 26,
                    ThemeManager.getTextMuted(),
                    ri.getFr().getScaleModifier(0.85f));

            drawPreview(cardX + rowW - 74, y + 9, 60, cardH - 18, preset);

            hits.add(new Hit(cardX, y, cardX + rowW, y + cardH, () -> {
                GuiStyle.get().setPreset(preset);
                Arsenic.getArsenic().getConfigManager().saveConfig();
            }));

            y += cardH + 6;
        }

        y += 10;
        DrawUtils.drawRect(x + 5, y, maxX, y + 1, ThemeManager.getSeparator());
        y += 14;

        drawSectionLabel("Colour", x + 5, y, ri);
        y += 16;

        float swatch = 22, gap = 6, sx = x + 5;
        for (Theme theme : Arsenic.getArsenic().getThemeManager().getContents()) {
            if (sx + swatch > maxX - 5) {
                sx = x + 5;
                y += swatch + gap;
            }
            boolean active = Arsenic.getArsenic().getThemeManager().getCurrentTheme() == theme;
            boolean hovered = mx >= sx && mx <= sx + swatch && my >= y && my <= y + swatch;

            DrawUtils.drawGradientRoundedRect(sx, y, sx + swatch, y + swatch, 6f,
                    theme.getMainColor(), theme.getMainColor(),
                    theme.getGradientColor(), theme.getGradientColor());
            DrawUtils.drawRoundedOutline(sx, y, sx + swatch, y + swatch, 6f, active ? 1.8f : 1f,
                    active ? ThemeManager.getWhite()
                           : UITheme.alpha(ThemeManager.getWhite(), hovered ? 150 : 50));

            final Theme picked = theme;
            hits.add(new Hit(sx, y, sx + swatch, y + swatch, () -> {
                Arsenic.getArsenic().getThemeManager().setCurrentTheme(picked);
                Arsenic.getArsenic().getConfigManager().saveConfig();
            }));
            sx += swatch + gap;
        }
        y += swatch + 8;

        ri.getFr().drawString(Arsenic.getArsenic().getThemeManager().getCurrentTheme().getName(),
                x + 5, y, ThemeManager.getTextMuted(),
                ri.getFr().getScaleModifier(0.85f));
        y += 18;

        DrawUtils.drawRect(x + 5, y, maxX, y + 1, ThemeManager.getSeparator());
        y += 14;

        drawSectionLabel("Interface", x + 5, y, ri);
        y += 16;

        y = drawChoice(ri, x + 5, y, rowW, mx, my, "Open GUI Key",
                binding ? "Press a key" : keyName(GuiStyle.get().getClickGuiKey()), () -> {
                    binding = true;
                    Arsenic.getArsenic().getClickGuiScreen().setAlwaysInputComponent(this);
                });
        y = drawSwitch(ri, x + 5, y, rowW, mx, my, "Custom Font",
                GuiStyle.get().isCustomFont(), () -> {
                    GuiStyle.get().setCustomFont(!GuiStyle.get().isCustomFont());
                    Arsenic.getArsenic().getConfigManager().saveConfig();
                });
        y = drawSwitch(ri, x + 5, y, rowW, mx, my, "Interface Sounds",
                GuiStyle.get().isSounds(), () -> {
                    GuiStyle.get().setSounds(!GuiStyle.get().isSounds());
                    Arsenic.getArsenic().getConfigManager().saveConfig();
                });
        y = drawChoice(ri, x + 5, y, rowW, mx, my, "Menu Style",
                "Element".equals(GuiStyle.get().getScreenStyle()) ? "Element 33" : GuiStyle.get().getScreenStyle(), () -> {
                    GuiStyle.get().setScreenStyle("Ocean".equals(GuiStyle.get().getScreenStyle()) ? "Element" : "Ocean");
                    Arsenic.getArsenic().getConfigManager().saveConfig();
                });
        y = drawSwitch(ri, x + 5, y, rowW, mx, my, "Custom Main Menu", GuiStyle.get().isCustomMainMenu(), () -> {
            GuiStyle.get().setCustomMainMenu(!GuiStyle.get().isCustomMainMenu());
            Arsenic.getArsenic().getConfigManager().saveConfig();
        });

        arsenic.module.Module postProcessing = Arsenic.getArsenic().getModuleManager()
                .getModuleByClass(arsenic.module.impl.visual.PostProcessing.class);
        if (postProcessing != null) {
            final arsenic.module.Module pp = postProcessing;
            y = drawSwitch(ri, x + 5, y, rowW, mx, my, "Blur & Bloom", pp.isEnabled(), () -> {
                pp.setEnabled(!pp.isEnabled());
                Arsenic.getArsenic().getConfigManager().saveConfig();
            });
        }

        maxHeight = y - contentTop + BOTTOM_MARGIN;
    }

    private void drawPreview(float x, float y, float w, float h, GuiStyle.Preset preset) {
        DrawUtils.drawRoundedRect(x, y, x + w, y + h, 4f, ThemeManager.getConfigsBackground());
        if (preset.background)
            DrawUtils.drawGradientRoundedRect(x, y, x + w, y + h, 4f,
                    UITheme.alpha(UITheme.accent(), preset.backgroundOpacity * 2),
                    UITheme.alpha(UITheme.accent(), preset.backgroundOpacity * 2),
                    UITheme.alpha(UITheme.accentAlt(), preset.backgroundOpacity * 2),
                    UITheme.alpha(UITheme.accentAlt(), preset.backgroundOpacity * 2));

        float px1 = x + 7, py1 = y + 5, px2 = x + w - 7, py2 = y + h - 5;
        if (preset.depth)
            DrawUtils.drawShadow(px1, py1, px2, py2, 3f,
                    preset.elevation / 40f, Math.min(160, preset.shadowStrength), 4);
        DrawUtils.drawRoundedRect(px1, py1, px2, py2, 3f,
                preset.glass ? UITheme.alpha(ThemeManager.getModuleBackground(), (int) (preset.glassFrost * 2.2f))
                             : ThemeManager.getModuleBackground());
        DrawUtils.drawRoundedRect(px1, py1, px1 + 2, py2, 1f, UITheme.accent());
    }

    /** Label on the left, the current choice on the right in the accent; clicking cycles it. */
    private float drawChoice(RenderInfo ri, float x, float y, float rowW, float mx, float my,
                             String label, String value, Runnable cycle) {
        float h = 26;
        boolean hovered = mx >= x && mx <= x + rowW && my >= y && my <= y + h;

        UITheme.surface(x, y, x + rowW, y + h, 8f, ThemeManager.getConfigsCard(),
                hovered ? UITheme.Elevation.RAISED : UITheme.Elevation.FLAT, 0.6f);
        DrawUtils.drawRoundedOutline(x, y, x + rowW, y + h, 8f, 1f,
                hovered ? ThemeManager.getConfigsHoverBorder() : ThemeManager.getConfigsCardBorder());

        ri.getFr().drawString(label, x + 12, y + h / 2f, ThemeManager.getTextPrimary(), ri.getFr().CENTREY);
        ri.getFr().drawString(value, x + rowW - 12 - ri.getFr().getWidth(value), y + h / 2f,
                UITheme.accent(), ri.getFr().CENTREY);

        hits.add(new Hit(x, y, x + rowW, y + h, cycle));
        return y + h + 6;
    }

    private float drawSwitch(RenderInfo ri, float x, float y, float rowW, float mx, float my,
                             String label, boolean on, Runnable toggle) {
        float h = 26;
        boolean hovered = mx >= x && mx <= x + rowW && my >= y && my <= y + h;

        UITheme.surface(x, y, x + rowW, y + h, 8f, ThemeManager.getConfigsCard(),
                hovered ? UITheme.Elevation.RAISED : UITheme.Elevation.FLAT, 0.6f);
        DrawUtils.drawRoundedOutline(x, y, x + rowW, y + h, 8f, 1f,
                hovered ? ThemeManager.getConfigsHoverBorder() : ThemeManager.getConfigsCardBorder());

        ri.getFr().drawString(label, x + 12, y + h / 2f,
                on ? ThemeManager.getTextPrimary() : ThemeManager.getTextSecondary(), ri.getFr().CENTREY);

        float tw = 26, th = 12;
        float tx = x + rowW - 12 - tw, ty = y + (h - th) / 2f;
        DrawUtils.drawRoundedRect(tx, ty, tx + tw, ty + th, th / 2f,
                on ? UITheme.alpha(UITheme.accent(), 235) : ThemeManager.getButtonBackground());
        float knob = th * 0.36f;
        float knobX = on ? tx + tw - th / 2f : tx + th / 2f;
        DrawUtils.drawCircle(knobX, ty + th / 2f, knob, ThemeManager.getWhite());

        hits.add(new Hit(x, y, x + rowW, y + h, toggle));
        return y + h + 6;
    }

    private void drawSectionLabel(String label, float x, float y, RenderInfo ri) {
        StringBuilder sb = new StringBuilder();
        String upper = label.toUpperCase();
        for (int i = 0; i < upper.length(); i++) {
            sb.append(upper.charAt(i));
            if (i < upper.length() - 1)
                sb.append(' ');
        }
        ri.getFr().drawString(sb.toString(), x, y, ThemeManager.getTextMuted(),
                ri.getFr().getScaleModifier(0.78f));
    }

    private static String keyName(int key) {
        if (key == 0)
            return "None";
        String name = Keyboard.getKeyName(key);
        return name == null ? "Key " + key : name;
    }

    @Override
    public void setNotAlwaysRecieveInput() {
        binding = false;
    }

    /** Escape cancels, so the GUI cannot be left without a key. Any other key becomes the GUI key. */
    @Override
    public boolean recieveInput(int key) {
        Arsenic.getArsenic().getClickGuiScreen().setAlwaysInputComponent(null);
        binding = false;
        SoundUtils.chordKeybind();
        if (key != Keyboard.KEY_ESCAPE) {
            GuiStyle.get().setClickGuiKey(key);
            Arsenic.getArsenic().getConfigManager().saveConfig();
        }
        return true;
    }

    @Override
    public void drawRight(PosInfo pi, RenderInfo ri) {
    }

    @Override
    public void clickChildren(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0)
            return;
        for (Hit hit : hits) {
            if (hit.contains(mouseX, mouseY)) {
                hit.action.run();
                SoundUtils.chordEnum();
                return;
            }
        }
    }
}
