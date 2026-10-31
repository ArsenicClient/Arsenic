package arsenic.gui.click;

import arsenic.gui.design.Hover;
import arsenic.gui.design.M3;
import arsenic.gui.design.M3Components;
import arsenic.gui.design.M3Icons;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.java.MathUtils;
import arsenic.gui.click.impl.AddonPageComponent;
import arsenic.gui.click.impl.ModuleCategoryComponent;
import arsenic.gui.click.impl.ModuleComponent;
import arsenic.gui.click.impl.SearchComponent;
import arsenic.gui.click.impl.UICategoryComponent;
import arsenic.gui.themes.ThemeManager;
import arsenic.main.Arsenic;
import arsenic.module.BuildTiers;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleTier;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.interfaces.IAlwaysClickable;
import arsenic.utils.interfaces.IAlwaysKeyboardInput;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.render.*;
import arsenic.utils.timer.AnimationTimer;
import arsenic.utils.timer.TickMode;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


public class ClickGuiScreen extends CustomGuiScreen {
    private List<UICategoryComponent> components;

    private ModuleCategoryComponent searchComponent;
    private final List<Runnable> renderLastList = new ArrayList<>();
    private ModuleCategoryComponent cmcc,prevCmcc;
    private IAlwaysClickable alwaysClickedComponent;
    private IAlwaysKeyboardInput alwaysKeyboardInput;
    private float winX1, winY1, winX2, winY2;
    private float railX1, railY1, railX2, railY2;
    private float contentX1, contentY1, contentX2, contentY2;
    private float searchX1, searchY1, searchX2, searchY2;
    private float footerX1, footerY1, footerX2, footerY2;
    /** The bottom bar holding the footer buttons; the module list scrolls under its top edge. */
    private float trayX1, trayY1, trayX2, trayY2;
    private float sceneX1, sceneY1, sceneX2, sceneY2;
    private static final Object LOGO_KEY = new Object(), MASCOT_KEY = new Object(), RELOAD_KEY = new Object(),
            BLATANT_KEY = new Object(), ADDON_KEY = new Object(), HUD_KEY = new Object();
    /** In the addon manager the scene keeps this much of the rail; the addon list scrolls above it. */
    private static final float MASCOT_H = 151f;

    private ModuleCategoryComponent slideFrom;
    private boolean slideFromAddon;
    private float slideDir = 1f;
    private long slideStart;
    private static final int SLIDE_MS = 280;

    private float hudBtnX1, hudBtnY1, hudBtnX2, hudBtnY2;
    /** While true the main card shows the addon manager instead of the selected module category. */
    private boolean addonMode;
    /** Whether typing goes to the search box while the addon manager is open. */
    private boolean addonSearchOn;
    private final AddonPageComponent addonPage = new AddonPageComponent(
            () -> searchComponent == null ? "" : ((SearchComponent) searchComponent).getQuery(),
            () -> { if (searchComponent != null) ((SearchComponent) searchComponent).setQuery(""); });

    private float addonBtnX1, addonBtnY1, addonBtnX2, addonBtnY2;
    private boolean addonBtnHovered;
    private final AnimationTimer addonBtnTimer = new AnimationTimer(160, () -> addonBtnHovered, TickMode.SINE);
    private boolean hudBtnHovered;
    private final AnimationTimer hudBtnTimer = new AnimationTimer(160, () -> hudBtnHovered, TickMode.SINE);

    private long openTime;
    private static final int OPEN_ANIMATION_DURATION = 400;

    // open/close transition
    private boolean closing = false;
    private long closeStartTime;
    private static final int BURN_DURATION = 700;

    private int burnDurationMs() {
        try {
            return GuiStyle.transitionTimeMs();
        } catch (Exception e) {
            return BURN_DURATION;
        }
    }

    //  Transition state, exposed so PostProcessing can fade its effects
    //  in step with the open/close transition.

    public float currentBurnProgress() {
        if (!GuiStyle.transitionEnabled())
            return 1f;
        int dur = burnDurationMs();
        long nowMs = System.currentTimeMillis();
        if (closing)
            return Math.max(0f, 1f - (nowMs - closeStartTime) / (float) dur);
        return Math.min(1f, (nowMs - openTime) / (float) dur);
    }

    public boolean isBurnActive() {
        return GuiStyle.transitionEnabled() && currentBurnProgress() < 1f;
    }

    /** {@link GuiStyle.Transition} ordinal. */
    public int getTransitionStyleId() {
        return GuiStyle.transition().ordinal();
    }

    /** The ClickGUI lays out on the fixed canvas, so it looks the same on every monitor. */
    @Override
    protected boolean fixedCanvas() {
        return true;
    }

    /** The window's rect and corner radius in design units: {x1, y1, x2, y2, radius}. */
    public float[] getBurnBoxPx() {
        layout();
        return new float[]{winX1, winY1, winX2, winY2, 28f};
    }

    public void buildComponents() {
        components = Arrays.stream(UICategory.values()).map(UICategoryComponent::new).distinct()
                .collect(Collectors.toList());
        cmcc = (ModuleCategoryComponent) components.get(0).getContents().toArray()[0];
        cmcc.setCurrentCategory(true);
        searchComponent = new SearchComponent(ModuleCategory.SEARCH);
    }

    @Override
    public void doInit() {
        super.doInit();
        openTime = System.currentTimeMillis();
        closing = false;
        Hover.reset();
    }

    private void layout() {
        winX1 = 44f;
        winY1 = 30f;
        winX2 = width - 44f;
        winY2 = height - 30f;
        railX1 = winX1 + 14f;
        railY1 = winY1 + 14f;
        railX2 = winX1 + 206f;
        railY2 = winY2 - 14f;
        contentX1 = railX2 + 16f;
        contentY1 = winY1 + 66f;
        contentX2 = winX2 - 14f;
        footerX1 = contentX1;
        footerX2 = contentX2;
        footerY2 = winY2 - 18f;
        footerY1 = footerY2 - 36f;
        trayX1 = railX2 + 10f;
        trayX2 = winX2 - 10f;
        trayY1 = footerY1 - 10f;
        trayY2 = winY2 - 10f;
        contentY2 = trayY1;
        searchX2 = contentX2 - 2f;
        searchX1 = searchX2 - 258f;
        searchY1 = winY1 + 18f;
        searchY2 = searchY1 + 38f;
    }

    /** The glow PostProcessing draws behind the panel. */
    public void drawBloom() {
        if (getFontRenderer() == null || components == null)
            return;
        layout();
        float openProgress = Math.min(1f, (System.currentTimeMillis() - openTime) / (float) OPEN_ANIMATION_DURATION);
        float glowFactor = MathUtils.clamp01((openProgress - 0.5f) / 0.5f);
        int glowAlpha = (int) (glowFactor * 255);

        int mainC = ColorUtils.setColor(M3.primary(), 0, glowAlpha);
        int gradientC = ColorUtils.setColor(M3.colors().primaryContainer, 0, glowAlpha);
        ((SearchComponent) searchComponent).setupGlowAndBlur(glowAlpha);
        DrawUtils.drawGradientRoundedRect(winX1, winY1, winX2, winY2, 28f, mainC, mainC, gradientC, gradientC);
    }

    @Override
    public void drawScr(int mouseX, int mouseY, float partialTicks) {
        if (components == null)
            return;

        // transition: 1 = fully present, <1 = fading in or out
        boolean burn = GuiStyle.transitionEnabled();
        float burnProgress = burn ? currentBurnProgress() : 1f;
        if (burn && closing && burnProgress <= 0f) { // fully gone -> actually close
            RenderContext.setAlpha(1f);
            minecraft.gui.setScreen(null);
            return;
        }

        layout();
        Hover.beginFrame(mouseX, mouseY);
        if (closing)
            Hover.suppress(true);

        // 1.8 dissolved the panel through a shader on a captured framebuffer; here the whole GUI
        // fades and grows into place instead, which needs nothing but the pose and an opacity.
        float eased = 1f - (1f - burnProgress) * (1f - burnProgress);
        RenderContext.setAlpha(eased);
        var pose = RenderContext.graphics().pose();
        pose.pushMatrix();
        if (eased < 1f) {
            float s = 0.94f + 0.06f * eased;
            float cx = width / 2f, cy = height / 2f;
            pose.translate(cx, cy);
            pose.scale(s, s);
            pose.translate(-cx, -cy);
        }

        try {
            drawBackdrop();
            drawPanel(mouseX, mouseY);
            drawScanlines();
        } finally {
            pose.popMatrix();
            ScissorUtils.resetScissor();
            getFontRenderer().resetScale();
            M3.label().resetScale();
            M3.title().resetScale();
            M3.headline().resetScale();
            RenderContext.setAlpha(1f);
        }
    }

    private void drawPanel(int mouseX, int mouseY) {
        RenderInfo ri = new RenderInfo(mouseX, mouseY, getFontRenderer(), this);
        float fontScale = height / 470f;
        getFontRenderer().setScale(fontScale);
        M3.body().setScale(fontScale);
        M3.label().setScale(fontScale);
        M3.title().setScale(fontScale);
        M3.headline().setScale(fontScale);

        M3Components.window(winX1, winY1, winX2, winY2, 28f);
        M3Components.glass(railX1, railY1, railX2, railY2, 22f, M3.alpha(M3.surfaceContainer(), 200), 0.25f);

        // as large as the rail's width allows, centred in the header band above the categories
        float logoX = railX1 + 16f;
        float logoH = arsenic.gui.design.LabLogo.heightFor(railX2 - railX1 - 32f, 32f);
        float logoY = railY1 + (60f - logoH) / 2f;
        boolean logoHover = Hover.test(LOGO_KEY, logoX, logoY, logoX + arsenic.gui.design.LabLogo.width(logoH), logoY + logoH);
        arsenic.gui.design.LabLogo.draw(logoX, logoY, logoH, logoHover);

        float mascotTop = railY2 - 10f - MASCOT_H;
        if (addonMode) {
            // the addon list scrolls in the space above the mascot slot
            float sideX1 = railX1 + 8f, sideX2 = railX2 - 8f, sideY1 = railY1 + 60f, sideY2 = mascotTop - 6f;
            addonPage.drawSidebar(railX1 + 9f, sideY1, sideX1, sideX2, sideY2, ri);
            drawMascot(mascotTop - 6f, mouseX, mouseY);
        } else {
            PosInfo pi = new PosInfo(railX1 + 9f, railY1 + 60f);
            components.forEach(component -> pi.moveY(component.updateComponent(pi, ri)));
            drawMascot(mascotTop - 6f, mouseX, mouseY);
        }

        searchComponent.updateComponent(new PosInfo(searchX1, searchY1), ri);

        String title = addonMode ? "Addons" : (cmcc == searchComponent ? "Search" : cmcc.getName());
        M3.headline().drawString(title, contentX1 + 2f, (searchY1 + searchY2) / 2f,
                M3.onSurface(), M3.headline().CENTREY);

        ScissorUtils.subScissor((int) contentX1, (int) contentY1, (int) contentX2, (int) contentY2, 2);
        Hover.pushClip(contentX1, contentY1, contentX2, contentY2);
        float colGap = 10f;
        // the columns sit 2 units inside the clip so the outline of a card on its edge is not cut off
        float colX = contentX1 + 2f;
        float colW = (contentX2 - 8f - colX - colGap) / 2f;   // 8: the scrollbar lane
        ModuleComponent.columnWidth = colW;
        ModuleCategoryComponent.contentWidth = contentX2 - contentX1;
        float top = contentY1 + 2f;
        float slide = slideProgress();
        float span = contentX2 - contentX1;
        if (addonMode) {
            if (slide < 1f && slideFrom != null) {
                float off = -slideDir * span * slide;
                boolean wasSuppressed = Hover.isSuppressed();
                Hover.suppress(true);
                slideFrom.drawLeft(new PosInfo(colX + off, top), ri);
                slideFrom.drawRight(new PosInfo(colX + colW + colGap + off, top), ri);
                Hover.suppress(wasSuppressed);
            }
            float off = slide < 1f && slideFrom != null ? slideDir * span * (1f - slide) : 0f;
            addonPage.drawContent(colX + off, colX + colW + colGap + off, contentY1, contentX2 - colX - 4f, ri);
            addonPage.subtractFromMaxScrollHeight(contentY2 - contentY1);
        } else {
            if (slide < 1f && slideFromAddon) {
                float off = -slideDir * span * slide;
                boolean wasSuppressed = Hover.isSuppressed();
                Hover.suppress(true);
                addonPage.drawContent(colX + off, colX + colW + colGap + off, contentY1, contentX2 - colX - 4f, ri);
                Hover.suppress(wasSuppressed);
            } else if (slide < 1f && slideFrom != null && slideFrom != cmcc) {
                // the old category leaves, and cannot take the cursor on its way out
                float off = -slideDir * span * slide;
                boolean wasSuppressed = Hover.isSuppressed();
                Hover.suppress(true);
                slideFrom.drawLeft(new PosInfo(colX + off, top), ri);
                slideFrom.drawRight(new PosInfo(colX + colW + colGap + off, top), ri);
                Hover.suppress(wasSuppressed);
            }
            float off = slide < 1f ? slideDir * span * (1f - slide) : 0f;
            cmcc.drawLeft(new PosInfo(colX + off, top), ri);
            cmcc.drawRight(new PosInfo(colX + colW + colGap + off, top), ri);
            cmcc.subtractFromMaxScrollHeight(contentY2 - contentY1);
        }
        Hover.popClip();
        // popups (dropdowns, colour pickers) draw last, so they also win the cursor over rows beneath
        renderLastList.forEach(Runnable::run);
        renderLastList.clear();

        ScissorUtils.endSubScissor();
        if (addonMode)
            addonPage.drawScrollbar(contentX2, contentY1, contentY2 - contentY1, ri);
        else
            cmcc.drawScrollbar(contentX2, contentY1, contentY2 - contentY1, ri);

        drawTray();
        drawFooter(mouseX, mouseY);
        if (addonMode)
            drawReloadButton(mouseX, mouseY);
    }

    private void drawMascot(float navBottom, int mouseX, int mouseY) {
        float y2 = railY2 - 10f;
        float y1 = navBottom + 6f;
        float x1 = railX1 + 10f, x2 = railX2 - 10f;
        boolean hover = Hover.test(MASCOT_KEY, x1, y1, x2, y2);
        sceneX1 = x1;
        sceneY1 = y1;
        sceneX2 = x2;
        sceneY2 = y2;
        arsenic.gui.design.DistilleryScene.draw(x1, y1, x2, y2, hover, mouseX, mouseY);
    }

    /** Re-opens the screen so a canvas scale change lays out at once. */
    public void reapplyScale() {
        minecraft.gui.setScreen(this);
    }

    private float reloadBtnX1, reloadBtnY1, reloadBtnX2, reloadBtnY2;

    private void drawReloadButton(int mouseX, int mouseY) {
        reloadBtnX2 = searchX1 - 10f;
        reloadBtnX1 = reloadBtnX2 - 108f;
        reloadBtnY1 = searchY1;
        reloadBtnY2 = searchY2;

        boolean hovered = Hover.test(RELOAD_KEY, reloadBtnX1, reloadBtnY1, reloadBtnX2, reloadBtnY2);
        M3Components.button(getFontRenderer(), "Reload", reloadBtnX1, reloadBtnY1, reloadBtnX2, reloadBtnY2,
                hovered ? 1f : 0f, false);
    }

    private static final ModuleTier[] TIER_TOGGLES = {ModuleTier.BLATANT};
    private final float[][] tierRects = new float[TIER_TOGGLES.length][4];
    private final boolean[] tierHovered = new boolean[TIER_TOGGLES.length];
    private final AnimationTimer[] tierHoverTimers = new AnimationTimer[TIER_TOGGLES.length];
    private final AnimationTimer[] tierTickTimers = new AnimationTimer[TIER_TOGGLES.length];

    {
        for (int i = 0; i < TIER_TOGGLES.length; i++) {
            final int idx = i;
            tierHoverTimers[i] = new AnimationTimer(160, () -> tierHovered[idx], TickMode.SINE);
            tierTickTimers[i] = new AnimationTimer(220, () -> GuiStyle.get().isTierShown(TIER_TOGGLES[idx]), TickMode.SINE);
        }
    }

    private void drawFooter(int mouseX, int mouseY) {
        float gap = 10f;
        float w = (footerX2 - footerX1 - gap * 2f) / 3f;
        float cy = (footerY1 + footerY2) / 2f;

        tierRects[0][0] = footerX1;
        tierRects[0][1] = footerY1;
        tierRects[0][2] = footerX1 + w;
        tierRects[0][3] = footerY2;
        addonBtnX1 = footerX1 + w + gap;
        addonBtnX2 = addonBtnX1 + w;
        addonBtnY1 = footerY1;
        addonBtnY2 = footerY2;
        hudBtnX1 = footerX1 + (w + gap) * 2f;
        hudBtnX2 = hudBtnX1 + w;
        hudBtnY1 = footerY1;
        hudBtnY2 = footerY2;

        tierHovered[0] = Hover.test(BLATANT_KEY, tierRects[0][0], tierRects[0][1], tierRects[0][2], tierRects[0][3]);
        addonBtnHovered = Hover.test(ADDON_KEY, addonBtnX1, addonBtnY1, addonBtnX2, addonBtnY2);
        hudBtnHovered = Hover.test(HUD_KEY, hudBtnX1, hudBtnY1, hudBtnX2, hudBtnY2);

        // a build without the Blatant tier has nothing to switch, so its card is left out
        if (BuildTiers.allows(ModuleTier.BLATANT)) {
            float blatantHoverPct = tierHoverTimers[0].getPercent();
            float tick = tierTickTimers[0].getPercent();
            footerCard(tierRects[0][0], footerY1, tierRects[0][2], footerY2, blatantHoverPct, false);
            int blatantFg = M3.mix(M3.onSurfaceVariant(), M3.onSurface(), blatantHoverPct);
            footerLabel(tierRects[0][0], tierRects[0][2] - 59f, cy, M3Icons.BLATANT, "Blatant modules", blatantFg);
            M3Components.switchControl(tierRects[0][2] - 51f, footerY1, footerY2 - footerY1, tick, blatantHoverPct);
        }

        float addonHoverPct = addonBtnTimer.getPercent();
        footerCard(addonBtnX1, footerY1, addonBtnX2, footerY2, addonHoverPct, addonMode);
        int addonFg = addonMode ? M3.onPrimaryContainer() : M3.mix(M3.onSurfaceVariant(), M3.onSurface(), addonHoverPct);
        footerLabel(addonBtnX1, addonBtnX2 - 12f, cy,
                addonMode ? M3Icons.ARROW_BACK : M3Icons.ADDONS,
                addonMode ? "Back to modules" : "Addon Manager", addonFg);

        float hudHoverPct = hudBtnTimer.getPercent();
        footerCard(hudBtnX1, footerY1, hudBtnX2, footerY2, hudHoverPct, false);
        int hudFg = M3.mix(M3.onSurfaceVariant(), M3.onSurface(), hudHoverPct);
        footerLabel(hudBtnX1, hudBtnX2 - 12f, cy, M3Icons.HUD, "HUD Editor", hudFg);
    }

    /** The bottom bar behind the footer buttons: a rounded panel, inset like the rail. */
    private void drawTray() {
        int tray = M3.mix(M3.surfaceContainer(), M3.surfaceContainerLow(), 0.5f) | 0xFF000000;
        float r = 16f;
        DrawUtils.drawRoundedRect(trayX1, trayY1, trayX2, trayY2, r, tray);
        DrawUtils.drawRoundedOutline(trayX1, trayY1, trayX2, trayY2, r, 1f, M3.alpha(M3.outlineVariant(), 130));
    }

    private void footerLabel(float x1, float x2, float cy, String glyph, String label, int color) {
        float textW = getFontRenderer().getWidth(label);
        float iconW = M3Icons.font().getWidth(glyph);
        float groupW = iconW + 7f + textW;
        float start = x1 + (x2 - x1 - groupW) / 2f;
        M3Icons.drawCentered(glyph, start + iconW / 2f, cy, color);
        getFontRenderer().drawString(label, start + iconW + 7f, cy, color, getFontRenderer().CENTREY);
    }

    private void footerCard(float x1, float y1, float x2, float y2, float hover, boolean active) {
        int fill = active
                ? M3.primaryContainer()
                : M3.mix(M3.surfaceContainerHigh(), M3.surfaceContainerHighest(), hover);
        float r = 10f;
        M3Components.glass(x1, y1, x2, y2, r, fill, 0f);
        if (active)
            DrawUtils.drawRoundedOutline(x1, y1, x2, y2, r, 1f, M3.alpha(M3.primary(), 140));
        if (hover > 0.01f)
            DrawUtils.drawRoundedRect(x1, y1, x2, y2, r, M3.alpha(M3.onSurface(), (int) (14 * hover)));
    }

    /**
     * Backdrop behind the panel. The 1.8 client ran an animated fullscreen shader here; modern
     * Minecraft gives screens a proper blur of the world for free, so the backdrop is that blur
     * under a theme-tinted wash at the configured opacity.
     */
    private void drawBackdrop() {
        if (!GuiStyle.backgroundEnabled())
            return;
        float alpha = GuiStyle.backgroundOpacity();
        if (alpha <= 0.001f)
            return;
        RenderContext.graphics().blurBeforeThisStratum();
        int tint = M3.primary();
        int top = ColorUtils.setColor(tint, 0, (int) (alpha * 70));
        int bottom = ColorUtils.setColor(0x000000, 0, (int) (alpha * 140));
        // the canvas is letterboxed, so the wash covers the whole window around it
        float left = -GuiCanvas.offsetX() / scale, topY = -GuiCanvas.offsetY() / scale;
        DrawUtils.drawGradientRect(left, topY, left + curWidth, topY + curHeight, top, bottom);
    }

    /** Faint scanlines layered over everything, standing in for the old VHS shader. */
    private void drawScanlines() {
        if (!GuiStyle.scanlinesEnabled())
            return;
        int line = ColorUtils.setColor(0x000000, 0, 26);
        QuadBatch batch = new QuadBatch();
        float left = -GuiCanvas.offsetX() / scale, top = -GuiCanvas.offsetY() / scale;
        float offset = (System.currentTimeMillis() % 4000L) / 4000f * 4f;
        for (float ly = top + offset; ly < top + curHeight; ly += 4f)
            batch.rect(left, ly, left + curWidth, ly + 1f, line);
        batch.submit();
    }

    @Override
    public void mouseClick(int mouseX, int mouseY, int mouseButton) {
        if (components == null)
            return;
        if (closing)
            return;
        if (mouseButton == 0 && MathUtils.inside(mouseX, mouseY, sceneX1, sceneY1, sceneX2, sceneY2)
                && arsenic.gui.design.DistilleryScene.click(mouseX, mouseY))
            return;
        if (addonMode && mouseButton == 0 && MathUtils.inside(mouseX, mouseY, reloadBtnX1, reloadBtnY1, reloadBtnX2, reloadBtnY2)) {
            arsenic.utils.java.SoundUtils.chordOpen();
            Arsenic.getArsenic().getAddonManager().reload();
            return;
        }
        if (mouseButton == 0 && MathUtils.inside(mouseX, mouseY, addonBtnX1, addonBtnY1, addonBtnX2, addonBtnY2)) {
            arsenic.utils.java.SoundUtils.chordOpen();
            setAddonMode(!addonMode);
            return;
        }
        if (mouseButton == 0 && MathUtils.inside(mouseX, mouseY, hudBtnX1, hudBtnY1, hudBtnX2, hudBtnY2)) {
            arsenic.utils.java.SoundUtils.chordOpen();
            minecraft.gui.setScreen(new arsenic.gui.hud.HudEditorScreen());
            return;
        }
        for (int i = 0; i < TIER_TOGGLES.length; i++) {
            if (!addonMode && mouseButton == 0 && BuildTiers.allows(TIER_TOGGLES[i])
                    && MathUtils.inside(mouseX, mouseY, tierRects[i][0], tierRects[i][1], tierRects[i][2], tierRects[i][3])) {
                GuiStyle.get().toggleTier(TIER_TOGGLES[i]);
                Arsenic.getArsenic().getConfigManager().saveConfig();
                arsenic.utils.java.SoundUtils.chordEnum();
                return;
            }
        }
        if(alwaysClickedComponent != null) {
            if(alwaysClickedComponent.clickAlwaysClickable(mouseX, mouseY, mouseButton)) return;
        }
        searchComponent.handleClick(mouseX, mouseY, mouseButton);
        if (addonMode) {
            addonPage.clickSidebar(mouseX, mouseY, mouseButton);
            if (mouseX > contentX1 && mouseX < contentX2 && mouseY > contentY1 && mouseY < contentY2)
                addonPage.clickContent(mouseX, mouseY, mouseButton);
            return;
        }
        components.forEach(panel -> panel.handleClick(mouseX, mouseY, mouseButton));
        if (mouseX > contentX1 && mouseX < contentX2 && mouseY > contentY1 && mouseY < contentY2)
            cmcc.clickChildren(mouseX, mouseY, mouseButton);
    }

    public boolean isAddonMode() {
        return addonMode;
    }

    /** Switches the main card between the selected module category and the addon manager. */
    public void setAddonMode(boolean on) {
        if (addonMode == on)
            return;
        addonMode = on;
        slideFrom = on ? cmcc : null;
        slideFromAddon = !on;
        slideDir = on ? 1f : -1f;
        slideStart = System.currentTimeMillis();
        Hover.reset();
        if (searchComponent != null)
            ((SearchComponent) searchComponent).setQuery("");
        if (on) {
            cmcc.setCurrentCategory(false);
            addonSearchOn = true;
            addonPage.refresh();
            addonPage.reset();
        } else {
            addonSearchOn = false;
            cmcc.setCurrentCategory(true);
        }
    }

    /** True while typing goes to the search box (module search, or addon search in the addon manager). */
    public boolean isSearchActive() {
        return addonMode ? addonSearchOn : cmcc == searchComponent;
    }

    public void toggleAddonSearch() {
        addonSearchOn = !addonSearchOn;
        if (!addonSearchOn && searchComponent != null)
            ((SearchComponent) searchComponent).setQuery("");
    }

    /** Rebuilds the GUI after addons were loaded or unloaded, keeping what was typed in the search box. */
    public void onAddonsReloaded() {
        if (components == null)
            return;
        String typed = ((SearchComponent) searchComponent).getQuery();
        buildComponents();
        ((SearchComponent) searchComponent).setQuery(typed);
        if (addonMode) {
            cmcc.setCurrentCategory(false);
            addonPage.refresh();
        }
    }

    public void setCmcc(ModuleCategoryComponent mcc) {
        if (addonMode)
            setAddonMode(false);
        if (cmcc != mcc) {
            prevCmcc = cmcc;
            int from = navIndex(cmcc), to = navIndex(mcc);
            slideDir = to >= from ? 1f : -1f;
            slideFrom = cmcc;
            slideFromAddon = false;
            slideStart = System.currentTimeMillis();
            Hover.reset();
            cmcc.setCurrentCategory(false);
            mcc.setCurrentCategory(true);
            cmcc = mcc;
        }
    }
    /** 0 just after a category switch, 1 once the new page is in place (ease-out). */
    private float slideProgress() {
        float t = MathUtils.clamp01((System.currentTimeMillis() - slideStart) / (float) SLIDE_MS);
        float u = 1f - t;
        return 1f - u * u * u;
    }

    /** Position in the rail, so going down the list slides left and going up slides right; search is last. */
    private int navIndex(ModuleCategoryComponent mcc) {
        int i = 0;
        for (UICategoryComponent group : components)
            for (ModuleCategoryComponent c : group.getContents()) {
                if (c == mcc)
                    return i;
                i++;
            }
        return i;
    }

    public ModuleCategoryComponent getCmcc() {
        return cmcc;
    }
    public ModuleCategoryComponent getPrevCmcc() {
        return prevCmcc;
    }
    public <T extends Component & IAlwaysClickable> void setAlwaysClickedComponent(T component) {
        if(alwaysClickedComponent != null)
            alwaysClickedComponent.setNotAlwaysClickable();
        this.alwaysClickedComponent = component;
    }

    public <T extends Component & IAlwaysKeyboardInput> void setAlwaysInputComponent(T component) {
        if(alwaysKeyboardInput != null && alwaysKeyboardInput != component)
            alwaysKeyboardInput.setNotAlwaysRecieveInput();
        this.alwaysKeyboardInput = component;
    }

    public final FontRendererExtension<?> getFontRenderer() {
        try {
            return GuiStyle.fontEnabled() ?
                    Arsenic.getInstance().getFonts().Inter.getFontRendererExtension() :
                    Arsenic.getInstance().getFonts().Minecraft.getFontRendererExtension();
        } catch (NullPointerException e) {
            return null;
        }
    }

    public void addToRenderLastList(Runnable v) {
        renderLastList.add(v);
    }

    @Override
    public void mouseScroll(int mouseX, int mouseY, double amount) {
        int i = (int) Math.signum(amount);
        if (addonMode && addonPage.isOverSidebar(mouseX, mouseY))
            addonPage.scrollSidebar(i * 30);
        else if (addonMode)
            addonPage.scroll(i * 30);
        else if (cmcc != null)
            cmcc.scroll(i * 30);
    }

    @Override
    public boolean keyTyped(int key, KeyEvent event) {
        arsenic.utils.java.SoundUtils.tick();
        if(alwaysKeyboardInput != null) {
            alwaysKeyboardInput.recieveInput(key);
            return true;
        }
        // ESC plays the close transition; a second ESC while it runs closes instantly
        if (key == InputConstants.KEY_ESCAPE && GuiStyle.transitionEnabled()) {
            if (!closing) {
                closing = true;
                closeStartTime = System.currentTimeMillis();
            } else {
                minecraft.gui.setScreen(null);
            }
            return true;
        }
        if (searchComponent != null)
            ((SearchComponent) searchComponent).recieveInput(key);
        return false;
    }

    @Override
    public boolean charTyped(char c) {
        if (alwaysKeyboardInput != null)
            return alwaysKeyboardInput.recieveChar(c);
        return searchComponent != null && ((SearchComponent) searchComponent).recieveChar(c);
    }

    @Override
    public void mouseRelease(int mouseX, int mouseY, int state) {
        if (components == null)
            return;
        components.forEach(component -> component.handleRelease(mouseX, mouseY, state));
        if (cmcc != null) cmcc.handleRelease(mouseX, mouseY, state);
        if (searchComponent != null) searchComponent.handleRelease(mouseX, mouseY, state);
    }

    /** Settings changed with sliders, switches and the like are saved here, once, instead of on every change. */
    @Override
    public void removed() {
        RenderContext.setAlpha(1f);
        super.removed();
        setAlwaysInputComponent(null);
        Arsenic.getArsenic().getConfigManager().saveConfig();
    }
}
