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
import arsenic.utils.io.Keys;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * A module's card in the ClickGUI.
 * <p>
 * The card is laid out as a single row of fixed zones - disclosure chevron, title, keybind
 * chip, toggle - with the properties expanding underneath. Every zone's x-range is computed once
 * during the draw and stored, so {@link #clickComponent} hit-tests against exactly the rectangles
 * that were drawn instead of re-deriving them from magic fractions and drifting out of sync (which
 * is what made the old bind hitbox land in the wrong place at some resolutions).
 */
public class ModuleComponent extends Component implements IContainer<PropertyComponent<?>>, IAlwaysKeyboardInput {

    private final Collection<PropertyComponent<?>> contents = new ArrayList<>();
    private boolean open, binding;
    // Deliberately not final: the animation timers below are field initialisers that capture this
    // reference in a lambda, and a blank final is not definitely assigned at that point. Assigning
    // it in the constructor instead would mean building the timers there too, for no gain.
    private Module self;
    private PosInfo posInfo;

    private final AnimationTimer openTimer = new AnimationTimer(UITheme.DUR_EXPAND, () -> open, TickMode.CUBIC);
    /** Height the settings occupy when fully open; drives the expansion duration. */
    private float contentHeight;
    private final AnimationTimer enabledTimer = new AnimationTimer(UITheme.DUR_TOGGLE, () -> self.isEnabled(), TickMode.CUBIC);
    private final AnimationTimer bindTimer = new AnimationTimer(UITheme.DUR_HOVER, () -> binding, TickMode.CUBIC);

    /** Zone boundaries, written during draw and read during click. */
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
        self.getProperties().forEach(property -> contents.add(property.createComponent()));
        this.self = self;
        this.name = self.getName();
    }

    @Override
    public float updateComponent(PosInfo pi, RenderInfo ri) {
        posInfo = pi;
        return super.updateComponent(pi, ri);
    }

    @Override
    protected float drawComponent(RenderInfo ri) {
        float enabledPct = enabledTimer.getPercent();
        // Retune before sampling, so this frame already animates at the right rate.
        openTimer.setMaxMs(UITheme.expandDuration(contentHeight));
        float openPct = openTimer.getPercent();
        float hover = hoverPct();

        float pad = height * 0.42f;
        float radius = UITheme.radiusCard(height);
        float cardY2 = y2 + expandY;

        // The whole card lifts on hover and while open, so the layer the pointer is on is always
        // the layer nearest the viewer.
        float elevation = Math.max(hover * 0.7f, Math.max(openPct * 0.5f, enabledPct * 0.35f)) + 0.3f;
        UITheme.surface(x1, y1, x2, cardY2, radius, ThemeManager.getModuleBackground(),
                UITheme.Elevation.RAISED, Math.min(1f, elevation));
        UITheme.hoverWash(x1, y1, x2, cardY2, radius, hover);

        // Accent spine down the left edge - the card's "on" indicator.
        if (enabledPct > 0.01f) {
            float barW = Math.max(1.5f, height * 0.07f);
            UITheme.accentBar(x1, y1, x1 + barW, cardY2, radius, enabledPct);
        }

        // ---- disclosure chevron ----
        float chevronCx = x1 + pad * 1.25f;
        chevronZoneX2 = chevronCx + pad;
        int chevronColor = UITheme.alpha(UITheme.textMuted(), (int) (140 + 115 * Math.max(hover, openPct)));
        UITheme.chevron(chevronCx, midPointY, height * 0.26f, Math.max(1f, height * 0.055f), chevronColor, openPct);

        // ---- toggle ----
        buttonComponent.updateComponent(posInfo, ri);
        // Take the boundary from the switch itself rather than guessing: the button sizes
        // relative to this card, so a change to either stays in step automatically.
        toggleZoneX1 = Math.min(x2, buttonComponent.getTrackX1()) - pad * 0.5f;

        // ---- keybind chip ----
        String bindName = binding ? "..." : Keys.getKeyName(self.getKeybind());
        boolean hasBind = self.getKeybind() != 0 || binding;
        float chipHeight = height * 0.46f;
        float chipRight = toggleZoneX1 - pad * 0.6f;
        if (hasBind || hover > 0.02f) {
            float bindPct = bindTimer.getPercent();
            int chipFill = UITheme.mix(
                    UITheme.alpha(ThemeManager.getBlack(), (int) (70 * Math.max(hover, hasBind ? 1f : 0f))),
                    UITheme.alpha(UITheme.accent(), 90), bindPct);
            int chipText = binding ? UITheme.accent()
                    : UITheme.alpha(UITheme.textMuted(), (int) (255 * Math.max(hover, hasBind ? 0.8f : 0f)));
            float chipW = UITheme.chip(ri.getFr(), bindName.isEmpty() ? "-" : bindName,
                    chipRight, midPointY, chipHeight, chipText, chipFill);
            bindZoneX2 = chipRight;
            bindZoneX1 = chipRight - chipW;
        } else {
            // No bind and not hovered: nothing is drawn, so nothing should be clickable either.
            bindZoneX1 = bindZoneX2 = chipRight;
        }

        // ---- hidden toggle chip ----
        // Same background formula as the bind chip's own resting state, so the two chips read as
        // one family - only the text inside shifts between a muted grey and full white.
        boolean isHidden = self.isHidden();
        float hiddenChipRight = bindZoneX1 - pad * 0.6f;
        if (isHidden || hover > 0.02f) {
            int hiddenChipFill = UITheme.alpha(ThemeManager.getBlack(), (int) (70 * Math.max(hover, isHidden ? 1f : 0f)));
            int hiddenChipText = isHidden
                    ? UITheme.textPrimary()
                    : UITheme.alpha(UITheme.textMuted(), (int) (255 * Math.max(hover, 0.35f)));
            float hiddenChipW = UITheme.chip(ri.getFr(), "Hidden",
                    hiddenChipRight, midPointY, chipHeight, hiddenChipText, hiddenChipFill);
            hiddenZoneX2 = hiddenChipRight;
            hiddenZoneX1 = hiddenChipRight - hiddenChipW;
        } else {
            hiddenZoneX1 = hiddenZoneX2 = hiddenChipRight;
        }

        // ---- title ----
        float textX = chevronZoneX2 + pad * 0.35f;
        int titleColor = UITheme.mix(UITheme.textSecondary(), UITheme.textPrimary(), enabledPct);
        ri.getFr().drawString(name, textX, midPointY, titleColor, ri.getFr().CENTREY);

        // ---- expanded properties ----
        PosInfo pi = new PosInfo(x1 + pad * 1.1f, y2);
        if (openPct > 0.001f) {
            UITheme.divider(x1 + pad, y2, x2 - pad, openPct);
            ScissorUtils.subScissor((int) x1, (int) y2, (int) (x2 + expandX * 2), (int) (y2 + expandY), 2);
            pi.moveY(pad * 0.4f);
            for (PropertyComponent<?> child : contents)
                pi.moveY(child.updateComponent(pi, ri) * 1.06f);
            pi.moveY(pad * 0.6f);
            ScissorUtils.endSubScissor();
        }
        // Children are only laid out while the card is open, so on the very frame an expansion
        // starts there is nothing to measure yet. Fall back to an estimate from the row count,
        // or the first open of every card would time itself against a content height of zero.
        contentHeight = openPct > 0.001f
                ? pi.getY() - y2
                : contents.size() * height * 0.98f;
        expandY = (pi.getY() - y2) * openPct;

        return expandY + height;
    }

    @Override
    protected void clickComponent(int mouseX, int mouseY, int mouseButton) {
        // Right-click anywhere on the card body expands it - the fastest way to reach settings.
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

    private void toggleOpen() {
        open = !open;
        SoundUtils.chordOpen();
        if (!open) {
            // Collapsing must also dismiss anything a child left floating (an open dropdown),
            // otherwise the popup outlives the card it belongs to.
            contents.forEach(component -> {
                if (component instanceof arsenic.utils.interfaces.IAlwaysClickable)
                    ((arsenic.utils.interfaces.IAlwaysClickable) component).setNotAlwaysClickable();
            });
        }
    }

    @Override
    public final Collection<PropertyComponent<?>> getContents() { return contents; }

    public final String getName() { return name; }

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
        if (key == InputConstants.KEY_ESCAPE) {
            self.setKeybind(Keys.NONE);
            Arsenic.getArsenic().getConfigManager().saveConfig();
            return true;
        }
        self.setKeybind(key);
        Arsenic.getArsenic().getConfigManager().saveConfig();
        return false;
    }
}
