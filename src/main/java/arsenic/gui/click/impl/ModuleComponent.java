package arsenic.gui.click.impl;

import java.util.ArrayList;
import java.util.Collection;

import arsenic.gui.click.Component;
import arsenic.gui.click.UITheme;
import arsenic.gui.themes.ThemeManager;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.utils.interfaces.IAlwaysKeyboardInput;
import arsenic.utils.interfaces.IContainer;
import arsenic.utils.java.SoundUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.PosInfo;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.render.ScissorUtils;
import arsenic.utils.timer.AnimationTimer;
import arsenic.utils.timer.TickMode;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.input.Keyboard;

public class ModuleComponent extends Component implements IContainer<PropertyComponent<?>>, IAlwaysKeyboardInput {

    private final Collection<PropertyComponent<?>> contents = new ArrayList<>();
    private boolean open, binding;
    private ModuleSource self;
    private PosInfo posInfo;

    private final AnimationTimer openTimer = new AnimationTimer(UITheme.DUR_EXPAND, () -> open, TickMode.CUBIC);
    private float contentHeight;
    private final AnimationTimer enabledTimer = new AnimationTimer(UITheme.DUR_TOGGLE, () -> self.isEnabled(), TickMode.CUBIC);
    private final AnimationTimer bindTimer = new AnimationTimer(UITheme.DUR_HOVER, () -> binding, TickMode.CUBIC);

    private float chevronZoneX2, bindZoneX1, bindZoneX2, toggleZoneX1, hiddenZoneX1, hiddenZoneX2;

    private final ButtonComponent buttonComponent = new ButtonComponent(this) {
        @Override
        protected boolean isEnabled() {
            return self.isEnabled();
        }

        @Override
        protected void setEnabled(boolean enabled) {
            self.setEnabled(enabled);
        }

        @Override
        public int getWidth(int i) {
            return (int) (super.getWidth(i) * 0.95);
        }
    };

    private final String name;

    public ModuleComponent(@NotNull Module self) {
        this(ModuleSource.of(self));
    }

    public ModuleComponent(@NotNull ModuleSource self) {
        this.self = self;
        this.name = self.getName();
        self.getProperties().forEach(property -> contents.add(property.createComponent()));
    }

    /** Builds the settings again from the source, for an addon whose module was loaded or reloaded. */
    void refreshContents() {
        contents.clear();
        self.getProperties().forEach(property -> contents.add(property.createComponent()));
    }

    @Override
    public float updateComponent(PosInfo pi, RenderInfo ri) {
        posInfo = pi;
        return super.updateComponent(pi, ri);
    }

    @Override
    protected float drawComponent(RenderInfo ri) {
        float enabledPct = enabledTimer.getPercent();
        openTimer.setMaxMs(UITheme.expandDuration(contentHeight));
        float openPct = openTimer.getPercent();
        float hover = hoverPct();

        float pad = height * 0.42f;
        float radius = UITheme.radiusCard(height);
        float cardY2 = y2 + expandY;

        float elevation = Math.max(hover * 0.7f, Math.max(openPct * 0.5f, enabledPct * 0.35f)) + 0.3f;
        UITheme.surface(x1, y1, x2, cardY2, radius, ThemeManager.getModuleBackground(),
                UITheme.Elevation.RAISED, Math.min(1f, elevation));
        UITheme.hoverWash(x1, y1, x2, cardY2, radius, hover);

        if (enabledPct > 0.01f) {
            float barW = Math.max(1.5f, height * 0.07f);
            UITheme.accentBar(x1, y1, x1 + barW, cardY2, radius, enabledPct);
        }

        float chevronCx = x1 + pad * 1.25f;
        chevronZoneX2 = chevronCx + pad;
        int chevronColor = UITheme.alpha(UITheme.textMuted(), (int) (140 + 115 * Math.max(hover, openPct)));
        UITheme.chevron(chevronCx, midPointY, height * 0.26f, Math.max(1f, height * 0.055f), chevronColor, openPct);

        buttonComponent.updateComponent(posInfo, ri);
        toggleZoneX1 = Math.min(x2, buttonComponent.getTrackX1()) - pad * 0.5f;
        RenderUtils.resetColorText();

        String bindName = binding ? "..." : Keyboard.getKeyName(self.getKeybind());
        boolean hasBind = self.getKeybind() != 0 || binding;
        float chipHeight = height * 0.46f;
        float chipRight = toggleZoneX1 - pad * 0.6f;
        if (hasBind || hover > 0.02f) {
            float bindPct = bindTimer.getPercent();
            int chipFill = chipBacking(Math.max(hover, hasBind ? 1f : 0f), bindPct);
            int chipText = binding ? UITheme.accent()
                    : UITheme.alpha(UITheme.textMuted(), (int) (255 * Math.max(hover, hasBind ? 0.8f : 0f)));
            float chipW = UITheme.chip(ri.getFr(), bindName.isEmpty() ? "-" : bindName,
                    chipRight, midPointY, chipHeight, chipText, chipFill);
            bindZoneX2 = chipRight;
            bindZoneX1 = chipRight - chipW;
        } else {
            bindZoneX1 = bindZoneX2 = chipRight;
        }

        boolean isHidden = self.isHidden();
        float hiddenChipRight = bindZoneX1 - pad * 0.6f;
        if (isHidden || hover > 0.02f) {
            // hidden: a grey "Hidden" chip; not hidden: a red "Hide" chip, shown while the row is hovered
            float chipAlpha = isHidden ? 1f : hover;
            int hiddenChipFill = UITheme.alpha(isHidden ? 0xFF6E6E6E : 0xFFD94040, (int) (200 * chipAlpha));
            int hiddenChipText = UITheme.alpha(0xFFFFFFFF, (int) (255 * chipAlpha));
            float hiddenChipW = UITheme.chip(ri.getFr(), isHidden ? "Hidden" : "Hide",
                    hiddenChipRight, midPointY, chipHeight, hiddenChipText, hiddenChipFill);
            hiddenZoneX2 = hiddenChipRight;
            hiddenZoneX1 = hiddenChipRight - hiddenChipW;
        } else {
            hiddenZoneX1 = hiddenZoneX2 = hiddenChipRight;
        }

        float textX = chevronZoneX2 + pad * 0.35f;
        int titleColor = UITheme.mix(UITheme.textSecondary(), UITheme.textPrimary(), enabledPct);
        ri.getFr().drawString(name, textX, midPointY, titleColor, ri.getFr().CENTREY);
        RenderUtils.resetColorText();

        PosInfo pi = new PosInfo(x1 + pad * 1.1f, y2);
        if (openPct > 0.001f) {
            UITheme.divider(x1 + pad, y2, x2 - pad, openPct);
            ScissorUtils.subScissor((int) x1, (int) y2, (int) (x2 + expandX * 2), (int) (y2 + expandY), 2);
            pi.moveY(pad * 0.4f);
            float tagH = height * 0.4f;
            int tierColor = tierColour(self.getTier());
            UITheme.chip(ri.getFr(), self.getTier().getDisplayName(), x2 - pad, pi.getY() + tagH / 2f, tagH,
                    UITheme.alpha(tierColor, (int) (255 * openPct)), UITheme.alpha(tierColor, (int) (40 * openPct)));
            pi.moveY(tagH + pad * 0.3f);
            // the description, as addon rows show it; same wrapping and colour. An empty one leaves no gap.
            String description = self.getDescription();
            if (description != null && !description.trim().isEmpty()) {
                float lineH = ri.getFr().getHeight("Ag") + 1.5f;
                for (String line : AddonCardComponent.wrap(ri.getFr(), description, x2 - x1 - pad * 2.2f)) {
                    ri.getFr().drawString(line, x1 + pad * 1.1f, pi.getY() + lineH / 2f,
                            UITheme.alpha(UITheme.textSecondary(), (int) (255 * openPct)), ri.getFr().CENTREY);
                    pi.moveY(lineH);
                }
                pi.moveY(pad * 0.3f);
            }
            for (PropertyComponent<?> child : contents)
                pi.moveY(child.updateComponent(pi, ri) * 1.06f);
            pi.moveY(pad * 0.6f);
            ScissorUtils.endSubScissor();
        }
        contentHeight = openPct > 0.001f
                ? pi.getY() - y2
                : contents.size() * height * 0.98f;
        expandY = (pi.getY() - y2) * openPct;

        return expandY + height;
    }

    @Override
    protected void clickComponent(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 1 && mouseX < toggleZoneX1) {
            toggleOpen();
            return;
        }
        if (mouseX <= chevronZoneX2) {
            toggleOpen();
            return;
        }
        if (mouseX >= bindZoneX1 && mouseX <= bindZoneX2 && bindZoneX2 > bindZoneX1) {
            binding = !binding;
            Arsenic.getArsenic().getClickGuiScreen().setAlwaysInputComponent(binding ? this : null);
            return;
        }
        if (mouseX >= hiddenZoneX1 && mouseX <= hiddenZoneX2 && hiddenZoneX2 > hiddenZoneX1) {
            self.setHidden(!self.isHidden());
            SoundUtils.chordKeybind();
            Arsenic.getArsenic().getConfigManager().saveConfig();
            return;
        }
        buttonComponent.handleClick(mouseX, mouseY, mouseButton);
    }

    private static int tierColour(arsenic.module.ModuleTier tier) {
        switch (tier) {
            case BLATANT: return 0xFFFF5555;
            case DEV: return 0xFFFFAA00;
            default: return 0xFF55FF55;
        }
    }

    private static int chipBacking(float emphasis, float accentPct) {
        return UITheme.mix(
                UITheme.alpha(ThemeManager.getBlack(), (int) (70 * emphasis)),
                UITheme.alpha(UITheme.accent(), 90), accentPct);
    }

    private void toggleOpen() {
        open = !open;
        SoundUtils.chordOpen();
        if (!open) {
            contents.forEach(component -> {
                if (component instanceof arsenic.utils.interfaces.IAlwaysClickable)
                    ((arsenic.utils.interfaces.IAlwaysClickable) component).setNotAlwaysClickable();
            });
        }
    }

    @Override
    public final Collection<PropertyComponent<?>> getContents() { return contents; }

    public final String getName() { return name; }

    /** Whether a module matches a search. {@code query} is already trimmed and lower case; empty matches everything. */
    public static boolean matches(Module module, String query) {
        if (query.isEmpty())
            return true;
        String description = module.getDescription();
        return module.getName().toLowerCase(java.util.Locale.ROOT).contains(query)
                || (description != null && description.toLowerCase(java.util.Locale.ROOT).contains(query));
    }

    public final arsenic.module.ModuleTier getTier() { return self.getTier(); }

    @Override
    public int getWidth(int i) {
        return (int) UITheme.space(i, 30);
    }

    @Override
    public void setNotAlwaysRecieveInput() {
        binding = false;
    }

    @Override
    public boolean recieveInput(int key) {
        Arsenic.getArsenic().getClickGuiScreen().setAlwaysInputComponent(null);
        SoundUtils.chordKeybind();
        binding = false;
        if (key == Keyboard.KEY_ESCAPE) {
            self.setKeybind(0);
            Arsenic.getArsenic().getConfigManager().saveConfig();
            return true;
        }
        self.setKeybind(key);
        Arsenic.getArsenic().getConfigManager().saveConfig();
        return false;
    }
}
