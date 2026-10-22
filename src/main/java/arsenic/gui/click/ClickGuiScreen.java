package arsenic.gui.click;

import arsenic.utils.render.RenderUtils;
import arsenic.utils.java.MathUtils;
import arsenic.gui.click.impl.AddonPageComponent;
import arsenic.gui.click.impl.ModuleCategoryComponent;
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
    private int vLineX, hLineY, x1, y1;

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

    /** Main box rect + corner radius in design units: {x1, y1, x2, y2, radius}. */
    public float[] getBurnBoxPx() {
        int bx = width / 8, by = height / 6;
        return new float[]{bx, by, width - bx, height - by, 30f};
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
    }

    /** The glow PostProcessing draws behind the panel. */
    public void drawBloom() {
        if (getFontRenderer() == null || components == null)
            return;
        int x = width / 8;
        int y = height / 6;
        x1 = width - x;
        y1 = height - y;

        float openProgress = Math.min(1f, (System.currentTimeMillis() - openTime) / (float) OPEN_ANIMATION_DURATION);
        float glowFactor = MathUtils.clamp01((openProgress - 0.5f) / 0.5f);
        int glowAlpha = (int) (glowFactor * 255);

        int mainC = ColorUtils.setColor(ThemeManager.getMainColor(), 0, glowAlpha);
        int gradientC = ColorUtils.setColor(ThemeManager.getGradientColor(), 0, glowAlpha);
        ((SearchComponent) searchComponent).setupGlowAndBlur(glowAlpha);
        DrawUtils.drawGradientRoundedRect(x, y, x1, y1, 30f, mainC, mainC, gradientC, gradientC);
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

        int x = width / 8;
        int y = height / 6;
        x1 = width - x;
        y1 = height - y;

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
            drawPanel(mouseX, mouseY, x, y);
            drawHudEditorButton(mouseX, mouseY);
            drawAddonManagerButton(mouseX, mouseY);
            if (addonMode)
                drawReloadButton(mouseX, mouseY);
            else
                drawTierToggles(mouseX, mouseY);
            drawScanlines();
        } finally {
            pose.popMatrix();
            ScissorUtils.resetScissor();
            getFontRenderer().resetScale();
            RenderContext.setAlpha(1f);
        }
    }

    private void drawPanel(int mouseX, int mouseY, int x, int y) {
        RenderInfo ri = new RenderInfo(mouseX, mouseY, getFontRenderer(), this);
        getFontRenderer().setScale(height/450f);
        Identifier logoPath = GuiStyle.logoMode() == GuiStyle.LogoMode.MODERN
                ? Arsenic.getArsenic().getThemeManager().getCurrentTheme().getAltLogoPath()
                : Arsenic.getArsenic().getThemeManager().getCurrentTheme().getLogoPath();

        // main container - base layer, lifted off the backdrop
        DrawUtils.drawShadow(x, y, x1, y1, 30f, GuiStyle.shadowSpread(10f), GuiStyle.shadowAlpha(190), 7);
        DrawUtils.drawRoundedRect(x, y, x1, y1, 30f, GuiStyle.glassify(ThemeManager.getClickGuiBackground()));
        DrawUtils.drawEdgeHighlight(x, y, x1, y1, 30f, ThemeManager.getMainColor(), GuiStyle.edgeAlpha(28));
        if (GuiStyle.glassEnabled())
            DrawUtils.drawGlassRect(x, y, x1, y1, 30f,
                    ColorUtils.setColor(ThemeManager.getMainColor(), 0, 18), ThemeManager.getWhite(), GuiStyle.glassStrength());

        vLineX = 2 * x;
        hLineY = (int) (1.5 * y);

        int catWidth = 10 * (width / 100);
        float leftColW = vLineX - x;
        float catMargin = leftColW * 0.06f;
        float catStartX = x + leftColW * 0.10f;
        float expandMax = catWidth / 40f;
        float sx1 = catStartX - catMargin, sy1 = hLineY + catMargin;
        float sx2 = catStartX + catWidth + expandMax + catMargin, sy2 = y1 - catMargin;
        // The addon manager keeps the same frame: its packs are listed where the module categories are.
        DrawUtils.drawShadow(sx1, sy1, sx2, sy2, 12f, GuiStyle.shadowSpread(6f), GuiStyle.shadowAlpha(150), 6);
        DrawUtils.drawRoundedRect(sx1, sy1, sx2, sy2, 12f, GuiStyle.glassify(ThemeManager.getModuleBackground()));
        DrawUtils.drawEdgeHighlight(sx1, sy1, sx2, sy2, 12f, ThemeManager.getMainColor(), GuiStyle.edgeAlpha(22));
        if (GuiStyle.glassEnabled())
            DrawUtils.drawGlassRect(sx1, sy1, sx2, sy2, 12f,
                    ColorUtils.setColor(ThemeManager.getMainColor(), 0, 14), ThemeManager.getWhite(), GuiStyle.glassStrength());

        DrawUtils.drawRect(vLineX, y, vLineX + 1.0f, y1, ThemeManager.getClickGuiSeparator());
        DrawUtils.drawRect(x, hLineY, x1, hLineY + 1.0f, ThemeManager.getClickGuiSeparator());

        //logo, tinted with the theme colour
        int tempExpand = (int) (x * 0.1f);
        DrawUtils.drawTexture(logoPath, x + tempExpand, y + tempExpand,
                vLineX - x - (tempExpand * 2), hLineY - y - (tempExpand * 2), 0xFF000000 | ThemeManager.getMainColor());

        if (addonMode) {
            addonPage.drawSidebar(catStartX, sy1 + catMargin, sx1, sx2 + expandMax, sy2 - catMargin, ri);
        } else {
            PosInfo pi = new PosInfo(catStartX, sy1 + catMargin);
            components.forEach(component -> pi.moveY(component.updateComponent(pi, ri)));
        }

        searchComponent.updateComponent(new PosInfo((vLineX + 5), (float) ((y + hLineY) / 2.05)), ri);

        ScissorUtils.subScissor(vLineX + 1, hLineY, x1, y1);

        PosInfo piL = new PosInfo(vLineX + 5, hLineY);
        PosInfo piR = new PosInfo(vLineX + (x1 - vLineX) / 2f, hLineY);
        if (addonMode) {
            float columnWidth = UITheme.space(width, 30);
            addonPage.drawContent(piL.getX(), piR.getX(), hLineY, piR.getX() + columnWidth - piL.getX(), ri);
            addonPage.subtractFromMaxScrollHeight(y1 - hLineY);
        } else {
            cmcc.drawLeft(piL, ri);
            cmcc.drawRight(piR, ri);
            cmcc.subtractFromMaxScrollHeight(y1 - hLineY);
        }

        renderLastList.forEach(Runnable::run);
        renderLastList.clear();

        ScissorUtils.endSubScissor();
        if (addonMode)
            addonPage.drawScrollbar(x1, hLineY, y1 - hLineY, ri);
        else
            cmcc.drawScrollbar(x1, hLineY, y1 - hLineY, ri);
    }

    /** Draws one of the pill buttons around the panel. */
    private void drawPill(String label, float bx1, float by1, float bx2, float by2, float hover) {
        float h = by2 - by1;
        float radius = h / 2f;
        DrawUtils.drawShadow(bx1, by1, bx2, by2, radius,
                GuiStyle.shadowSpread(h * 0.35f), GuiStyle.shadowAlpha((int) (110 + 60 * hover)), 5);
        DrawUtils.drawRoundedRect(bx1, by1, bx2, by2, radius,
                GuiStyle.glassify(ThemeManager.getClickGuiBackground()));
        DrawUtils.drawRoundedOutline(bx1, by1, bx2, by2, radius, 1f,
                ColorUtils.setColor(ThemeManager.getMainColor(), 0, (int) (70 + 150 * hover)));

        getFontRenderer().drawString(label, (bx1 + bx2) / 2f, (by1 + by2) / 2f,
                RenderUtils.interpolateColoursInt(ThemeManager.getTextSecondary(), ThemeManager.getWhite(), hover),
                getFontRenderer().CENTREX, getFontRenderer().CENTREY);
    }

    private void drawHudEditorButton(int mouseX, int mouseY) {
        String label = "HUD Editor";
        float pad = height / 100f * 1.6f;
        float h = height / 100f * 4.2f;
        float w = getFontRenderer().getWidth(label) + pad * 3f;
        float margin = height / 100f * 2.5f;

        hudBtnX1 = margin;
        // the bottom left corner; the tier pill(s) stack above it
        hudBtnY1 = height - margin - h;
        hudBtnX2 = margin + w;
        hudBtnY2 = hudBtnY1 + h;

        hudBtnHovered = MathUtils.inside(mouseX, mouseY, hudBtnX1, hudBtnY1, hudBtnX2, hudBtnY2);
        drawPill(label, hudBtnX1, hudBtnY1, hudBtnX2, hudBtnY2, hudBtnTimer.getPercent());
    }

    /** Bottom right corner: opens the addon manager. */
    private void drawAddonManagerButton(int mouseX, int mouseY) {
        String label = addonMode ? "ClickGUI" : "Addon Manager";
        float pad = height / 100f * 1.6f;
        float h = height / 100f * 4.2f;
        float w = getFontRenderer().getWidth(label) + pad * 3f;
        float margin = height / 100f * 2.5f;

        addonBtnX2 = width - margin;
        addonBtnX1 = addonBtnX2 - w;
        addonBtnY2 = height - margin;
        addonBtnY1 = addonBtnY2 - h;

        addonBtnHovered = MathUtils.inside(mouseX, mouseY, addonBtnX1, addonBtnY1, addonBtnX2, addonBtnY2);
        drawPill(label, addonBtnX1, addonBtnY1, addonBtnX2, addonBtnY2, addonBtnTimer.getPercent());
    }

    private float reloadBtnX1, reloadBtnY1, reloadBtnX2, reloadBtnY2;

    /** Top right, in the addon manager: loads the addons' current state (switches and binds apply here). */
    private void drawReloadButton(int mouseX, int mouseY) {
        String label = "Reload addons";
        float pad = height / 100f * 1.6f;
        float h = height / 100f * 4.2f;
        float w = getFontRenderer().getWidth(label) + pad * 3f;
        float margin = height / 100f * 2.5f;

        reloadBtnX2 = width - margin;
        reloadBtnX1 = reloadBtnX2 - w;
        reloadBtnY1 = margin;
        reloadBtnY2 = margin + h;

        boolean hovered = MathUtils.inside(mouseX, mouseY, reloadBtnX1, reloadBtnY1, reloadBtnX2, reloadBtnY2);
        drawPill(label, reloadBtnX1, reloadBtnY1, reloadBtnX2, reloadBtnY2, hovered ? 1f : 0f);
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
            tierTickTimers[i] = new AnimationTimer(180, () -> GuiStyle.get().isTierShown(TIER_TOGGLES[idx]), TickMode.SINE);
        }
    }

    /** The Blatant toggle pill, stacked above the HUD Editor button in the bottom-left corner. */
    private void drawTierToggles(int mouseX, int mouseY) {
        float pad = height / 100f * 1.6f;
        float h = height / 100f * 4.2f;
        float box = h * 0.5f;
        float margin = height / 100f * 2.5f;
        float step = h * 1.3f;
        float radius = h / 2f;

        float w = 0;
        for (ModuleTier tier : TIER_TOGGLES)
            w = Math.max(w, pad * 1.4f + box + pad + getFontRenderer().getWidth(tier.getDisplayName()) + pad * 1.4f);

        for (int i = 0; i < TIER_TOGGLES.length; i++) {
            ModuleTier tier = TIER_TOGGLES[i];
            // a build without this tier has nothing to switch, so its pill is not drawn
            if (!BuildTiers.allows(tier))
                continue;
            float tx1 = hudBtnX1, tx2 = tx1 + w;
            float ty1 = height - margin - h - (i + 1) * step, ty2 = ty1 + h;
            tierRects[i][0] = tx1;
            tierRects[i][1] = ty1;
            tierRects[i][2] = tx2;
            tierRects[i][3] = ty2;

            tierHovered[i] = MathUtils.inside(mouseX, mouseY, tx1, ty1, tx2, ty2);
            float hover = tierHoverTimers[i].getPercent();
            float tick = tierTickTimers[i].getPercent();

            DrawUtils.drawShadow(tx1, ty1, tx2, ty2, radius,
                    GuiStyle.shadowSpread(h * 0.35f), GuiStyle.shadowAlpha((int) (110 + 60 * hover)), 5);
            DrawUtils.drawRoundedRect(tx1, ty1, tx2, ty2, radius, GuiStyle.glassify(ThemeManager.getClickGuiBackground()));
            DrawUtils.drawRoundedOutline(tx1, ty1, tx2, ty2, radius, 1f,
                    ColorUtils.setColor(ThemeManager.getMainColor(), 0, (int) (70 + 150 * hover)));

            float midY = (ty1 + ty2) / 2f;
            float bx1 = tx1 + pad * 1.4f, bx2 = bx1 + box;
            float by1 = midY - box / 2f, by2 = midY + box / 2f;
            float boxRadius = Math.max(2f, box * 0.28f);
            DrawUtils.drawRoundedRect(bx1, by1, bx2, by2, boxRadius,
                    UITheme.alpha(UITheme.accent(), (int) (235 * tick)));
            DrawUtils.drawRoundedOutline(bx1, by1, bx2, by2, boxRadius, 1f,
                    UITheme.mix(UITheme.alpha(ThemeManager.getTextMuted(), 200), UITheme.accent(), Math.max(tick, hover * 0.6f)));
            if (tick > 0.02f)
                UITheme.check((bx1 + bx2) / 2f, midY, box * 0.7f, Math.max(1f, box * 0.14f),
                        UITheme.alpha(UITheme.readableOn(UITheme.accent()), (int) (255 * tick)));

            getFontRenderer().drawString(tier.getDisplayName(), bx2 + pad, midY,
                    RenderUtils.interpolateColoursInt(ThemeManager.getTextSecondary(), ThemeManager.getWhite(),
                            Math.max(hover, tick)),
                    getFontRenderer().CENTREY);
        }
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
        int tint = ThemeManager.getMainColor();
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
            if (mouseX > vLineX && mouseX < x1 && mouseY > hLineY && mouseY < y1)
                addonPage.clickContent(mouseX, mouseY, mouseButton);
            return;
        }
        components.forEach(panel -> panel.handleClick(mouseX, mouseY, mouseButton));
        if(mouseX > vLineX && mouseX < x1 && mouseY > hLineY && mouseY < y1)
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
            cmcc.setCurrentCategory(false);
            mcc.setCurrentCategory(true);
            cmcc = mcc;
        }
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
                    Arsenic.getInstance().getFonts().Comfortaa.getFontRendererExtension() :
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
