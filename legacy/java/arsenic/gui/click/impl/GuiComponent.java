package arsenic.gui.click.impl;

import arsenic.gui.click.GuiStyle;
import arsenic.gui.click.UITheme;
import arsenic.gui.design.Hover;
import arsenic.gui.design.M3;
import arsenic.gui.design.M3Components;
import arsenic.gui.design.M3Icons;
import arsenic.gui.themes.Theme;
import arsenic.main.Arsenic;
import arsenic.module.ModuleCategory;
import arsenic.utils.interfaces.IAlwaysKeyboardInput;
import arsenic.utils.java.MathUtils;
import arsenic.utils.java.SoundUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.PosInfo;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.timer.AnimationTimer;
import arsenic.utils.timer.TickMode;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final Map<String, Boolean> switchStates = new HashMap<>();
    private final Map<String, AnimationTimer> switchAnims = new HashMap<>();
    /** True after the keybind row is clicked, until the next key is pressed. */
    private boolean binding;

    /** The whole-menu zoom slider (GUI tab). */
    private boolean scaleDragging;
    private boolean scaleHover;
    private final AnimationTimer scaleGrabTimer =
            new AnimationTimer(M3.DUR_FAST, () -> scaleDragging, TickMode.CUBIC);
    private float scaleTrackX1, scaleTrackX2, scaleTrackY, scaleRowY1, scaleRowY2;

    private static final float SCALE_MIN = 0.7f, SCALE_MAX = 1.8f;
    private static final Object SCALE_KEY = new Object(), OPACITY_KEY = new Object();

    /** The background shader opacity slider. */
    private boolean opacityDragging;
    private final AnimationTimer opacityGrabTimer =
            new AnimationTimer(M3.DUR_FAST, () -> opacityDragging, TickMode.CUBIC);
    private float opacityTrackX1, opacityTrackX2, opacityTrackY = -1000;

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

        float x = pi.getX() + 2;
        float contentTop = pi.getY() + scroll + 12;
        float right = pi.getX() + ModuleCategoryComponent.contentWidth - 14f;
        float rowW = right - x - 4;
        float y = contentTop;
        float mx = ri.getMouseX(), my = ri.getMouseY();

        sectionLabel(ri, "Appearance", x, y);
        y += 18;

        for (GuiStyle.Preset preset : GuiStyle.Preset.values()) {
            float h = 50;
            boolean active = GuiStyle.get().getPreset() == preset;
            boolean hovered = Hover.test(preset, x, y, x + rowW, y + h);

            int fill = active ? M3.mix(M3.surfaceContainerHigh(), M3.secondaryContainer(), 0.58f)
                    : (hovered ? M3.surfaceContainerHighest() : M3.surfaceContainerHigh());
            DrawUtils.drawRoundedRect(x, y, x + rowW, y + h, 14f, fill);
            DrawUtils.drawRoundedOutline(x, y, x + rowW, y + h, 14f, 1f,
                    active ? M3.alpha(M3.primary(), 165)
                           : (hovered ? M3.alpha(M3.outline(), 150) : M3.alpha(M3.outlineVariant(), 120)));

            float previewW = 78, previewH = 30;
            float previewX = x + rowW - 16 - previewW;
            ri.getFr().drawString(preset.label, x + 16, y + 17,
                    active ? M3.onSurface() : M3.onSurfaceVariant(), ri.getFr().CENTREY);
            ri.getFr().drawString(preset.description, x + 16, y + 33,
                    M3.alpha(M3.onSurfaceVariant(), 205), ri.getFr().getScaleModifier(0.85f), ri.getFr().CENTREY);

            drawPreview(previewX, y + (h - previewH) / 2f, previewW, previewH, preset);

            hits.add(new Hit(x, y, x + rowW, y + h, () -> {
                GuiStyle.get().setPreset(preset);
                Arsenic.getArsenic().getConfigManager().saveConfig();
            }));

            y += h + 8;
        }

        y += 2;
        y = drawSwitch(ri, x, y, rowW, mx, my, "Light mode", "Light Material surfaces with frosted glass",
                GuiStyle.get().isLightMode(), () -> {
                    GuiStyle.get().setLightMode(!GuiStyle.get().isLightMode());
                    Arsenic.getArsenic().getConfigManager().saveConfig();
                });
        y = drawChoice(ri, x, y, rowW, mx, my, "Background", "Animated shader behind the menu",
                GuiStyle.get().getBgChoice().label(), false, () -> {
                    GuiStyle.get().cycleBgChoice();
                    Arsenic.getArsenic().getConfigManager().saveConfig();
                });
        if (GuiStyle.backgroundEnabled())
            y = drawOpacitySlider(ri, x, y, rowW, mx);
        else
            opacityTrackY = -1000;

        y += 6;
        M3Components.divider(x, y, x + rowW);
        y += 16;

        sectionLabel(ri, "Accent colour", x, y);
        y += 18;

        float cardH = 78;
        DrawUtils.drawRoundedRect(x, y, x + rowW, y + cardH, 14f, M3.surfaceContainerHigh());
        DrawUtils.drawRoundedOutline(x, y, x + rowW, y + cardH, 14f, 1f, M3.alpha(M3.outlineVariant(), 120));

        Theme current = Arsenic.getArsenic().getThemeManager().getCurrentTheme();
        ri.getFr().drawString(current.getName(), x + 16, y + 17, M3.onSurface(), ri.getFr().CENTREY);
        ri.getFr().drawString("Recolours every surface and accent",
                x + 16, y + 32, M3.alpha(M3.onSurfaceVariant(), 205),
                ri.getFr().getScaleModifier(0.85f), ri.getFr().CENTREY);

        float swatch = 24, gap = 7, sx = x + 16, sy = y + 44;
        for (Theme theme : Arsenic.getArsenic().getThemeManager().getContents()) {
            boolean active = current == theme;
            boolean hovered = Hover.test(theme, sx, sy, sx + swatch, sy + swatch);

            DrawUtils.drawGradientRoundedRect(sx, sy, sx + swatch, sy + swatch, 8f,
                    theme.getMainColor(), theme.getMainColor(),
                    theme.getGradientColor(), theme.getGradientColor());
            DrawUtils.drawRoundedOutline(sx, sy, sx + swatch, sy + swatch, 8f, active ? 2f : 1f,
                    active ? M3.onSurface()
                           : M3.alpha(M3.onSurface(), hovered ? 145 : 45));

            final Theme picked = theme;
            hits.add(new Hit(sx, sy, sx + swatch, sy + swatch, () -> {
                Arsenic.getArsenic().getThemeManager().setCurrentTheme(picked);
                Arsenic.getArsenic().getConfigManager().saveConfig();
            }));
            sx += swatch + gap;
        }
        y += cardH + 18;

        M3Components.divider(x, y, x + rowW);
        y += 16;

        sectionLabel(ri, "Interface", x, y);
        y += 18;

        y = drawChoice(ri, x, y, rowW, mx, my, "Open GUI key", "Opens this menu from anywhere",
                binding ? "Press a key" : keyName(GuiStyle.get().getClickGuiKey()),
                true, () -> {
                    binding = true;
                    Arsenic.getArsenic().getClickGuiScreen().setAlwaysInputComponent(this);
                });
        y = drawSwitch(ri, x, y, rowW, mx, my, "Custom font", "Inter text across menus and HUD",
                GuiStyle.get().isCustomFont(), () -> {
                    GuiStyle.get().setCustomFont(!GuiStyle.get().isCustomFont());
                    Arsenic.getArsenic().getConfigManager().saveConfig();
                });
        y = drawSwitch(ri, x, y, rowW, mx, my, "Interface sounds", "Clicks, toggles and category cues",
                GuiStyle.get().isSounds(), () -> {
                    GuiStyle.get().setSounds(!GuiStyle.get().isSounds());
                    Arsenic.getArsenic().getConfigManager().saveConfig();
                });
        y = drawChoice(ri, x, y, rowW, mx, my, "Loading screen", "Style used before the game starts",
                GuiStyle.get().getScreenStyle(), false, () -> {
                    GuiStyle.get().setScreenStyle("Ocean".equals(GuiStyle.get().getScreenStyle()) ? "Element" : "Ocean");
                    Arsenic.getArsenic().getConfigManager().saveConfig();
                });
        y = drawSwitch(ri, x, y, rowW, mx, my, "Custom menus", "Themed buttons, lists and main menu",
                GuiStyle.get().isCustomMenus(), () -> {
                    GuiStyle.get().setCustomMenus(!GuiStyle.get().isCustomMenus());
                    Arsenic.getArsenic().getConfigManager().saveConfig();
                });

        arsenic.module.Module postProcessing = Arsenic.getArsenic().getModuleManager()
                .getModuleByClass(arsenic.module.impl.visual.PostProcessing.class);
        if (postProcessing != null) {
            final arsenic.module.Module pp = postProcessing;
            y = drawSwitch(ri, x, y, rowW, mx, my, "Blur and bloom", "Live backdrop blur and glow",
                    pp.isEnabled(), () -> {
                        pp.setEnabled(!pp.isEnabled());
                        Arsenic.getArsenic().getConfigManager().saveConfig();
                    });
        }

        y = drawScaleSlider(ri, x, y, rowW, mx, my);

        maxHeight = y - contentTop + BOTTOM_MARGIN;
    }

    /** The whole-menu zoom: an M3 slider row that relays the fixed canvas live. */
    private float drawScaleSlider(RenderInfo ri, float x, float y, float rowW, float mx, float my) {
        float h = 56;
        scaleRowY1 = y;
        scaleRowY2 = y + h;
        GuiStyle style = GuiStyle.get();
        float trackX1 = x + 16, trackX2 = x + rowW - 16, trackMidY = y + h - 15;
        scaleTrackX1 = trackX1 - 8;
        scaleTrackX2 = trackX2 + 8;
        scaleTrackY = trackMidY;

        float value = style.getClickGuiScale();
        float pct = (value - SCALE_MIN) / (SCALE_MAX - SCALE_MIN);
        boolean rowHover = Hover.test(SCALE_KEY, x, y, x + rowW, y + h);
        scaleHover = rowHover;
        int fill = scaleDragging || rowHover ? M3.surfaceContainerHighest() : M3.surfaceContainerHigh();
        DrawUtils.drawRoundedRect(x, y, x + rowW, y + h, 12f, fill);
        DrawUtils.drawRoundedOutline(x, y, x + rowW, y + h, 12f, 1f, M3.alpha(M3.outlineVariant(), 120));

        ri.getFr().drawString("GUI scale", x + 16, y + 16, M3.onSurface(), ri.getFr().CENTREY);
        ri.getFr().drawString("Zoom of this whole menu", x + 16, y + 31,
                M3.alpha(M3.onSurfaceVariant(), 205), ri.getFr().getScaleModifier(0.85f), ri.getFr().CENTREY);
        String val = Math.round(value * 100f) + " %";
        ri.getFr().drawString(val, x + rowW - 16 - ri.getFr().getWidth(val), y + 16,
                scaleDragging ? M3.primary() : M3.onSurfaceVariant(), ri.getFr().CENTREY);

        if (scaleDragging) {
            Hover.suppress(true);
            pct = MathUtils.clamp((mx - trackX1) / (trackX2 - trackX1), 0f, 1f);
            style.setClickGuiScale(SCALE_MIN + pct * (SCALE_MAX - SCALE_MIN));
        }
        float grab = scaleGrabTimer.getPercent();
        float active = Math.max(grab, scaleHover ? 0.6f : 0f);
        M3Components.sliderControl(trackX1, trackMidY, trackX2 - trackX1, pct, active);
        return y + h + 4;
    }

    /** Background shader opacity, 0-100 %. */
    private float drawOpacitySlider(RenderInfo ri, float x, float y, float rowW, float mx) {
        float h = 56;
        GuiStyle style = GuiStyle.get();
        float trackX1 = x + 16, trackX2 = x + rowW - 16, trackMidY = y + h - 15;
        opacityTrackX1 = trackX1 - 8;
        opacityTrackX2 = trackX2 + 8;
        opacityTrackY = trackMidY;

        if (opacityDragging) {
            Hover.suppress(true);
            float p = MathUtils.clamp((mx - trackX1) / (trackX2 - trackX1), 0f, 1f);
            style.setBgOpacity(Math.round(p * 100f));
        }
        boolean rowHover = Hover.test(OPACITY_KEY, x, y, x + rowW, y + h);
        int fill = opacityDragging || rowHover ? M3.surfaceContainerHighest() : M3.surfaceContainerHigh();
        DrawUtils.drawRoundedRect(x, y, x + rowW, y + h, 12f, fill);
        DrawUtils.drawRoundedOutline(x, y, x + rowW, y + h, 12f, 1f, M3.alpha(M3.outlineVariant(), 120));

        ri.getFr().drawString("Background opacity", x + 16, y + 16, M3.onSurface(), ri.getFr().CENTREY);
        ri.getFr().drawString("How strongly the shader shows through", x + 16, y + 31,
                M3.alpha(M3.onSurfaceVariant(), 205), ri.getFr().getScaleModifier(0.85f), ri.getFr().CENTREY);
        String val = style.getBgOpacity() + " %";
        ri.getFr().drawString(val, x + rowW - 16 - ri.getFr().getWidth(val), y + 16,
                opacityDragging ? M3.primary() : M3.onSurfaceVariant(), ri.getFr().CENTREY);

        float active = Math.max(opacityGrabTimer.getPercent(), rowHover ? 0.6f : 0f);
        M3Components.sliderControl(trackX1, trackMidY, trackX2 - trackX1,
                style.getBgOpacity() / 100f, active);
        return y + h + 4;
    }

    private String transitionLabel(GuiStyle.Transition transition) {
        switch (transition) {
            case BURN: return "Flame";
            case SCALE: return "Scale";
            case FADE: return "Fade";
            case DISSOLVE: return "Dissolve";
            case GLITCH: return "Glitch";
            default: return "Off";
        }
    }

    private void drawPreview(float x, float y, float w, float h, GuiStyle.Preset preset) {
        DrawUtils.drawRoundedRect(x, y, x + w, y + h, 7f, M3.surfaceContainerLowest());
        DrawUtils.drawRoundedOutline(x, y, x + w, y + h, 7f, 1f, M3.alpha(M3.outlineVariant(), 100));
        if (preset.background)
            DrawUtils.drawGradientRoundedRect(x, y, x + w, y + h, 7f,
                    M3.alpha(M3.primary(), preset.backgroundOpacity * 2),
                    M3.alpha(M3.primary(), preset.backgroundOpacity * 2),
                    M3.alpha(M3.primaryContainer(), preset.backgroundOpacity * 2),
                    M3.alpha(M3.primaryContainer(), preset.backgroundOpacity * 2));

        float px1 = x + 8, py1 = y + 6, px2 = x + w - 8, py2 = y + h - 6;
        DrawUtils.drawRoundedRect(px1, py1, px2, py2, 5f,
                preset.glass ? M3.alpha(M3.surfaceContainerHigh(), (int) (preset.glassFrost * 2.2f))
                             : M3.surfaceContainerHighest());
        DrawUtils.drawRoundedRect(px1, py1, px1 + 2.5f, py2, 2f, M3.primary());
    }

    /** A Material settings row: title and supporting text on the left, the current value on the right. */
    private float drawChoice(RenderInfo ri, float x, float y, float rowW, float mx, float my,
                             String label, String hint, String value, boolean binding,
                             Runnable cycle) {
        float h = 44;
        boolean hovered = Hover.test(label, x, y, x + rowW, y + h);

        DrawUtils.drawRoundedRect(x, y, x + rowW, y + h, 12f,
                hovered ? M3.surfaceContainerHighest() : M3.surfaceContainerHigh());
        DrawUtils.drawRoundedOutline(x, y, x + rowW, y + h, 12f, binding ? 1.5f : 1f,
                binding ? M3.alpha(M3.primary(), 220)
                        : (hovered ? M3.alpha(M3.outline(), 150) : M3.alpha(M3.outlineVariant(), 120)));

        ri.getFr().drawString(label, x + 16, y + 16, M3.onSurface(), ri.getFr().CENTREY);
        ri.getFr().drawString(hint, x + 16, y + 31, M3.alpha(M3.onSurfaceVariant(), 205),
                ri.getFr().getScaleModifier(0.85f), ri.getFr().CENTREY);

        float arrowW = M3Icons.font().getWidth(M3Icons.CHEVRON_RIGHT);
        float valueW = ri.getFr().getWidth(value);
        float start = x + rowW - 16 - arrowW - 6 - valueW;
        ri.getFr().drawString(value, start, y + h / 2f,
                binding ? M3.primary() : M3.onSurfaceVariant(), ri.getFr().CENTREY);
        M3Icons.drawLeft(M3Icons.CHEVRON_RIGHT, x + rowW - 16 - arrowW, y + h / 2f,
                binding ? M3.primary() : M3.alpha(M3.onSurfaceVariant(), 185));

        hits.add(new Hit(x, y, x + rowW, y + h, cycle));
        return y + h + 4;
    }

    private float drawSwitch(RenderInfo ri, float x, float y, float rowW, float mx, float my,
                             String label, String hint, boolean on, Runnable toggle) {
        float h = 44;
        boolean hovered = Hover.test(label, x, y, x + rowW, y + h);

        DrawUtils.drawRoundedRect(x, y, x + rowW, y + h, 12f,
                hovered ? M3.surfaceContainerHighest() : M3.surfaceContainerHigh());
        DrawUtils.drawRoundedOutline(x, y, x + rowW, y + h, 12f, 1f,
                hovered ? M3.alpha(M3.outline(), 150) : M3.alpha(M3.outlineVariant(), 120));

        ri.getFr().drawString(label, x + 16, y + 16, M3.onSurface(), ri.getFr().CENTREY);
        ri.getFr().drawString(hint, x + 16, y + 31, M3.alpha(M3.onSurfaceVariant(), 205),
                ri.getFr().getScaleModifier(0.85f), ri.getFr().CENTREY);

        M3Components.switchControl(x + rowW - 42, y + (h - 26) / 2f, 26, switchPct(label, on), hovered ? 1f : 0f);

        hits.add(new Hit(x, y, x + rowW, y + h, toggle));
        return y + h + 4;
    }

    private float switchPct(String key, boolean on) {
        switchStates.put(key, on);
        AnimationTimer anim = switchAnims.get(key);
        if (anim == null) {
            final String k = key;
            anim = new AnimationTimer(M3.DUR_MEDIUM, () -> switchStates.getOrDefault(k, false), TickMode.CUBIC);
            switchAnims.put(key, anim);
        }
        return anim.getPercent();
    }

    private void sectionLabel(RenderInfo ri, String label, float x, float y) {
        ri.getFr().drawString(label.toUpperCase(java.util.Locale.ROOT), x, y,
                M3.alpha(M3.onSurfaceVariant(), 215), ri.getFr().getScaleModifier(0.82f));
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
        if (mouseX >= scaleTrackX1 && mouseX <= scaleTrackX2
                && mouseY >= scaleTrackY - 12 && mouseY <= scaleTrackY + 12) {
            scaleDragging = true;
            SoundUtils.chordSlider();
            return;
        }
        if (mouseX >= opacityTrackX1 && mouseX <= opacityTrackX2
                && mouseY >= opacityTrackY - 12 && mouseY <= opacityTrackY + 12) {
            opacityDragging = true;
            SoundUtils.chordSlider();
            return;
        }
        for (Hit hit : hits) {
            if (hit.contains(mouseX, mouseY)) {
                hit.action.run();
                SoundUtils.chordEnum();
                return;
            }
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int state) {
        if (opacityDragging) {
            opacityDragging = false;
            Arsenic.getArsenic().getConfigManager().saveConfig();
        }
        if (scaleDragging) {
            scaleDragging = false;
            Arsenic.getArsenic().getConfigManager().saveConfig();
            Arsenic.getArsenic().getClickGuiScreen().reapplyScale();
        }
    }
}
