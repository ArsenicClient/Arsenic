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
import arsenic.gui.themes.ThemeManager;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.property.Group;
import arsenic.module.property.Property;
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
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import arsenic.utils.io.Keys;
import com.mojang.blaze3d.platform.InputConstants;

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

        String bindName = binding ? "..." : Keys.getKeyName(self.getKeybind());
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

        PosInfo pi = new PosInfo(x1 + pad * 1.1f, y2);
        for (float[] zone : groupZones)
            Arrays.fill(zone, 0);
        if (openPct > 0.001f) {
            UITheme.divider(x1 + pad, y2, x2 - pad, openPct);
            ScissorUtils.subScissor((int) x1, (int) y2, (int) (x2 + expandX * 2), (int) (y2 + expandY), 2);
            pi.moveY(pad * 0.4f);
            keepSelectionVisible();
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
            for (PropertyComponent<?> child : shown())
                pi.moveY(child.updateComponent(pi, ri) * 1.06f);

            // the bottom row closes the card: the tier tag on the left, the group icons on the right
            pi.moveY(pad * 0.2f);
            float rowH = height * 0.72f;
            float tagH = height * 0.4f;
            int tierColor = tierColour(self.getTier());
            String tier = self.getTier().getDisplayName();
            float tagW = ri.getFr().getWidth(tier) + tagH * 0.84f;
            UITheme.chip(ri.getFr(), tier, x1 + pad * 1.1f + tagW, pi.getY() + rowH / 2f, tagH,
                    UITheme.alpha(tierColor, (int) (255 * openPct)), UITheme.alpha(tierColor, (int) (40 * openPct)));
            if (hasVisible(selected))
                drawGroupIcons(pi.getY(), rowH, pad, openPct);
            pi.moveY(rowH + pad * 0.5f);
            ScissorUtils.endSubScissor();
        }
        contentHeight = openPct > 0.001f
                ? pi.getY() - y2
                : (shown().size() + 2) * height * 0.98f;
        expandY = (pi.getY() - y2) * openPct;

        return expandY + height;
    }

    /** One icon per group that has settings, right-aligned, the selected one lit. */
    private void drawGroupIcons(float y, float rowH, float pad, float openPct) {
        float mx = lastMouseX, my = lastMouseY;
        float right = x2 - pad;
        Group[] all = Group.values();
        for (int i = all.length - 1; i >= 0; i--) {
            Group group = all[i];
            if (!hasVisible(group))
                continue;
            float bx1 = right - rowH;
            boolean active = group == selected;
            boolean hover = mx >= bx1 && mx <= right && my >= y && my <= y + rowH;
            if (active || hover)
                DrawUtils.drawRoundedRect(bx1, y, right, y + rowH, UITheme.radiusChip(rowH),
                        active ? UITheme.alpha(UITheme.accent(), (int) (55 * openPct))
                               : UITheme.alpha(ThemeManager.getBlack(), (int) (60 * openPct)));

            int tint = active ? UITheme.accent() : (hover ? UITheme.textPrimary() : UITheme.textMuted());
            float icon = rowH * 0.76f, ix = bx1 + (rowH - icon) / 2f, iy = y + (rowH - icon) / 2f;
            int iconAlpha = (int) (255 * (active || hover ? 1f : 0.55f) * openPct);
            DrawUtils.drawTexture(icon(group), ix, iy, icon, icon, (Math.max(0, Math.min(255, iconAlpha)) << 24) | (tint & 0xFFFFFF));

            groupZones[group.ordinal()] = new float[] { bx1, y, right, y + rowH };
            right = bx1 - pad * 0.3f;
        }
    }

    private static final Map<Group, Identifier> ICONS = new EnumMap<>(Group.class);

    private static Identifier icon(Group group) {
        return ICONS.computeIfAbsent(group, g -> Identifier.fromNamespaceAndPath("arsenic", "icons/" + g.getIcon() + ".png"));
    }

    private float lastMouseX, lastMouseY;

    @Override
    public void mouseUpdate(int mouseX, int mouseY) {
        super.mouseUpdate(mouseX, mouseY);
        lastMouseX = mouseX;
        lastMouseY = mouseY;
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
            case QOL: return 0xFF9E9E9E;
            case VISUAL: return 0xFF55CCFF;
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
