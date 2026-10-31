package arsenic.gui.click.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import arsenic.gui.click.Component;
import arsenic.gui.click.UITheme;
import arsenic.gui.design.Hover;
import arsenic.gui.design.M3;
import arsenic.gui.design.M3Icons;
import arsenic.gui.themes.ThemeManager;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.property.Group;
import arsenic.module.property.Property;
import arsenic.utils.interfaces.IAlwaysKeyboardInput;
import arsenic.utils.java.BindUtils;
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

    /** Every setting; only the selected group's are drawn and clicked (see {@link #getContents()}). */
    private final List<PropertyComponent<?>> allContents = new ArrayList<>();
    private final Map<Group, List<PropertyComponent<?>>> groups = new EnumMap<>(Group.class);
    private Group selected = Group.MAIN;
    /** Where each group's icon was drawn: x1, y1, x2, y2 (all 0 when it is not shown). */
    private final float[][] groupZones = new float[Group.values().length][4];
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
            if (enabled)
                arsenic.gui.design.LabLogo.pulse();
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
        buildContents();
    }

    /** Builds the settings again from the source, for an addon whose module was loaded or reloaded. */
    void refreshContents() {
        buildContents();
    }

    private void buildContents() {
        allContents.clear();
        groups.clear();
        for (Property<?> property : self.getProperties()) {
            PropertyComponent<?> component = property.createComponent();
            allContents.add(component);
            groups.computeIfAbsent(property.getGroup(), g -> new ArrayList<>()).add(component);
        }
        if (!groups.containsKey(selected))
            selected = groups.isEmpty() ? Group.MAIN : groups.keySet().iterator().next();
    }

    /** A group whose settings are all hidden right now (by a reliesOn rule) gets no icon. */
    private boolean hasVisible(Group group) {
        List<PropertyComponent<?>> list = groups.get(group);
        if (list != null)
            for (PropertyComponent<?> component : list)
                if (component.self.isVisible())
                    return true;
        return false;
    }

    /** Moves off the selected group if its settings were all hidden. */
    private void keepSelectionVisible() {
        if (hasVisible(selected))
            return;
        for (Group group : Group.values())
            if (hasVisible(group)) {
                selected = group;
                return;
            }
    }

    private List<PropertyComponent<?>> shown() {
        List<PropertyComponent<?>> list = groups.get(selected);
        return list == null ? Collections.emptyList() : list;
    }

    private void selectGroup(Group group) {
        if (group == selected || !hasVisible(group))
            return;
        closePopups(shown());
        selected = group;
        SoundUtils.chordEnum();
    }

    private static void closePopups(Collection<PropertyComponent<?>> components) {
        components.forEach(component -> {
            if (component instanceof arsenic.utils.interfaces.IAlwaysClickable)
                ((arsenic.utils.interfaces.IAlwaysClickable) component).setNotAlwaysClickable();
        });
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

        float pad = height * 0.4f;
        float radius = 12f;
        float cardY2 = y2 + expandY;

        int fill = M3.mix(M3.surfaceContainerLow(), M3.surfaceContainerHigh(), Math.max(hover * 0.75f, openPct * 0.6f));
        if (enabledPct > 0.01f)
            fill = M3.mix(fill, M3.primaryContainer(), enabledPct * 0.20f);
        DrawUtils.drawRoundedRect(x1, y1, x2, cardY2, radius, fill);
        DrawUtils.drawRoundedOutline(x1, y1, x2, cardY2, radius, 1f,
                M3.alpha(enabledPct > 0.5f ? M3.primary() : M3.outlineVariant(), enabledPct > 0.5f ? 90 : 90));

        float chevronCx = x1 + pad * 1.1f;
        chevronZoneX2 = chevronCx + pad;
        int chevronColor = M3.alpha(M3.onSurfaceVariant(), (int) (160 + 85 * Math.max(hover, openPct)));
        UITheme.chevron(chevronCx, midPointY, height * 0.22f, Math.max(1f, height * 0.045f), chevronColor, openPct);

        buttonComponent.updateComponent(posInfo, ri);
        toggleZoneX1 = Math.min(x2, buttonComponent.getTrackX1()) - pad * 0.5f;
        RenderUtils.resetColorText();

        String bindName = binding ? "..." : BindUtils.getKeyName(self.getKeybind());
        boolean hasBind = self.getKeybind() != 0 || binding;
        float chipHeight = height * 0.46f;
        float chipRight = toggleZoneX1 - pad * 0.6f;
        if (hasBind || hover > 0.02f) {
            float bindPct = bindTimer.getPercent();
            int chipFill = binding ? M3.alpha(M3.primaryContainer(), 245)
                    : M3.alpha(M3.surfaceContainerHighest(), 235);
            int chipText = binding ? M3.onPrimaryContainer()
                    : M3.alpha(M3.onSurfaceVariant(), (int) (255 * Math.max(hover, hasBind ? 0.9f : 0f)));
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
            int hiddenChipFill = M3.alpha(isHidden ? M3.surfaceContainerHighest() : M3.errorContainer(), (int) (230 * chipAlpha));
            int hiddenChipText = M3.alpha(isHidden ? M3.onSurfaceVariant() : M3.error(), (int) (255 * chipAlpha));
            float hiddenChipW = UITheme.chip(ri.getFr(), isHidden ? "Hidden" : "Hide",
                    hiddenChipRight, midPointY, chipHeight, hiddenChipText, hiddenChipFill);
            hiddenZoneX2 = hiddenChipRight;
            hiddenZoneX1 = hiddenChipRight - hiddenChipW;
        } else {
            hiddenZoneX1 = hiddenZoneX2 = hiddenChipRight;
        }

        float textX = chevronZoneX2 + pad * 0.3f;
        int titleColor = M3.mix(M3.onSurfaceVariant(), M3.primary(), enabledPct);
        M3.label().drawString(name, textX, midPointY, titleColor, M3.label().CENTREY);
        RenderUtils.resetColorText();

        PosInfo pi = new PosInfo(x1 + pad * 1.1f, y2);
        for (float[] zone : groupZones)
            Arrays.fill(zone, 0);
        if (openPct > 0.001f) {
            UITheme.divider(x1 + pad, y2, x2 - pad, openPct);
            ScissorUtils.subScissor((int) x1, (int) y2, (int) (x2 + expandX * 2), (int) (y2 + expandY), 2);
            Hover.pushClip(x1, y2, x2 + expandX * 2, y2 + expandY);
            pi.moveY(pad * 0.4f);
            keepSelectionVisible();
            // the description, as addon rows show it; same wrapping and colour. An empty one leaves no gap.
            String description = self.getDescription();
            if (description != null && !description.trim().isEmpty()) {
                float lineH = ri.getFr().getHeight("Ag") + 1.5f;
                for (String line : AddonCardComponent.wrap(ri.getFr(), description, x2 - x1 - pad * 2.2f)) {
                    ri.getFr().drawString(line, x1 + pad * 1.1f, pi.getY() + lineH / 2f,
                            M3.alpha(M3.onSurfaceVariant(), (int) (220 * openPct)), ri.getFr().CENTREY);
                    pi.moveY(lineH);
                }
                pi.moveY(pad * 0.3f);
            }
            // settings span the card, inset by the same padding on both sides
            float previousRow = PropertyComponent.rowWidth;
            PropertyComponent.rowWidth = (x2 - x1) - pad * 2.2f;
            for (PropertyComponent<?> child : shown())
                pi.moveY(child.updateComponent(pi, ri) * 1.06f);
            PropertyComponent.rowWidth = previousRow;

            // group tabs only when there is more than one group to switch between
            if (visibleGroups() > 1) {
                pi.moveY(pad * 0.2f);
                float rowH = height * 0.72f;
                drawGroupIcons(pi.getY(), rowH, pad, openPct);
                pi.moveY(rowH);
            }
            pi.moveY(pad * 0.5f);
            Hover.popClip();
            ScissorUtils.endSubScissor();
        }
        contentHeight = openPct > 0.001f
                ? pi.getY() - y2
                : (shown().size() + 2) * height * 0.98f;
        expandY = (pi.getY() - y2) * openPct;
        queueHint(ri);

        return expandY + height;
    }

    private int visibleGroups() {
        int n = 0;
        for (Group group : Group.values())
            if (hasVisible(group))
                n++;
        return n;
    }

    /** One icon per group that has settings, right-aligned, the selected one lit. */
    private void drawGroupIcons(float y, float rowH, float pad, float openPct) {
        float right = x2 - pad;
        Group[] all = Group.values();
        for (int i = all.length - 1; i >= 0; i--) {
            Group group = all[i];
            if (!hasVisible(group))
                continue;
            float bx1 = right - rowH;
            boolean active = group == selected;
            boolean hover = Hover.test(groupKeys[group.ordinal()], bx1, y, right, y + rowH);
            if (active || hover)
                DrawUtils.drawRoundedRect(bx1, y, right, y + rowH, rowH * 0.4f,
                        active ? M3.alpha(M3.secondaryContainer(), (int) (230 * openPct))
                               : M3.alpha(M3.surfaceContainerHighest(), (int) (150 * openPct)));

            int tint = active ? M3.onSecondaryContainer() : (hover ? M3.onSurface() : M3.onSurfaceVariant());
            float alpha = (active || hover ? 1f : 0.55f) * openPct;
            M3Icons.drawCentered(M3Icons.group(group.name()), (bx1 + right) / 2f, y + rowH / 2f,
                    M3.alpha(tint, (int) (255 * alpha)));

            groupZones[group.ordinal()] = new float[] { bx1, y, right, y + rowH };
            right = bx1 - pad * 0.3f;
        }
    }

    /** Stable hover keys for the group icons, one per group. */
    private final Object[] groupKeys = new Object[Group.values().length];

    {
        for (int i = 0; i < groupKeys.length; i++)
            groupKeys[i] = new Object();
    }

    /** The header counts as hovered while its own switch has the cursor, so the bind/hide chips stay up. */
    @Override
    public void mouseUpdate(int mouseX, int mouseY) {
        hovered = Hover.test(this, x1, y1, x2, y2) || Hover.owns(buttonComponent);
        if (!hovered) {
            hoverSince = 0;
            hintDismissed = false;
        } else if (hoverSince == 0) {
            hoverSince = System.currentTimeMillis();
        }
    }

    private long hoverSince;
    private boolean hintDismissed;
    private static final long HINT_DELAY_MS = 900;
    private static final String HINT = "Click to configure. Toggle the switch. Click the key box to bind.";

    /** The usage hint, once the header has been hovered a while without being clicked. */
    private void queueHint(RenderInfo ri) {
        if (!hovered || hintDismissed || open || binding || hoverSince == 0
                || System.currentTimeMillis() - hoverSince < HINT_DELAY_MS)
            return;
        // below the header: above it, the first row's hint would be cut off by the content scissor
        final float cx = (x1 + x2) / 2f, bottom = y2 + 28f;
        final arsenic.utils.font.FontRendererExtension<?> fr = ri.getFr();
        Arsenic.getArsenic().getClickGuiScreen().addToRenderLastList(() ->
                arsenic.gui.design.M3Components.tooltip(fr, HINT, cx, bottom));
    }

    @Override
    public boolean handleClick(int mouseX, int mouseY, int mouseButton) {
        if (open && mouseButton == 0) {
            for (Group group : Group.values()) {
                float[] z = groupZones[group.ordinal()];
                if (z[2] > z[0] && mouseX >= z[0] && mouseX <= z[2] && mouseY >= z[1] && mouseY <= z[3]) {
                    selectGroup(group);
                    return true;
                }
            }
        }
        return super.handleClick(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void clickComponent(int mouseX, int mouseY, int mouseButton) {
        hintDismissed = true;
        if (mouseButton == 2) {
            binding = !binding;
            Arsenic.getArsenic().getClickGuiScreen().setAlwaysInputComponent(binding ? this : null);
            SoundUtils.chordKeybind();
            return;
        }
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

    /** Pastel tones on dark surfaces, deeper ones on light surfaces so the tag stays legible. */
    private static int tierColour(arsenic.module.ModuleTier tier) {
        if (M3.isLight()) {
            switch (tier) {
                case QOL: return 0xFF5A6270;
                case VISUAL: return 0xFF1F6FB8;
                case BLATANT: return 0xFFB3261E;
                case DEV: return 0xFF8A6100;
                default: return 0xFF2E7D3E;
            }
        }
        switch (tier) {
            case QOL: return 0xFFB8C0CC;
            case VISUAL: return 0xFF8FC7FF;
            case BLATANT: return 0xFFFFB4AB;
            case DEV: return 0xFFFFD37A;
            default: return 0xFF9BE8A8;
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
        if (!open)
            closePopups(allContents);
    }

    /** The selected group's settings only, so clicks never reach settings that are not drawn. */
    @Override
    public final Collection<PropertyComponent<?>> getContents() { return shown(); }

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

    /** Set by the screen each frame: the width of one module column. */
    public static float columnWidth = -1f;

    @Override
    public int getWidth(int i) {
        return columnWidth > 0 ? (int) columnWidth : (int) UITheme.space(i, 30);
    }

    @Override
    public int getHeight(int i) {
        return 42;
    }

    @Override
    public void setNotAlwaysRecieveInput() {
        binding = false;
    }

    @Override
    public boolean acceptsMouseButtons() {
        return binding;
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
