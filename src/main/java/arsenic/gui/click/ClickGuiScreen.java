package arsenic.gui.click;

import arsenic.utils.render.RenderUtils;
import arsenic.utils.java.MathUtils;
import arsenic.gui.click.impl.ModuleCategoryComponent;
import arsenic.gui.click.impl.ModuleComponent;
import arsenic.gui.click.impl.SearchComponent;
import arsenic.gui.click.impl.UICategoryComponent;
import arsenic.gui.themes.ThemeManager;
import arsenic.main.Arsenic;
import arsenic.module.ModuleCategory;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.interfaces.IAlwaysClickable;
import arsenic.utils.interfaces.IAlwaysKeyboardInput;
import arsenic.utils.font.VanillaFontRenderer;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.render.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Mouse;

import java.awt.*;
import java.io.IOException;
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
    private final arsenic.gui.click.impl.AddonPageComponent addonPage = new arsenic.gui.click.impl.AddonPageComponent(
            () -> searchComponent == null ? "" : ((SearchComponent) searchComponent).getQuery(),
            () -> { if (searchComponent != null) ((SearchComponent) searchComponent).setQuery(""); });

    private float addonBtnX1, addonBtnY1, addonBtnX2, addonBtnY2;
    private boolean addonBtnHovered;
    private final arsenic.utils.timer.AnimationTimer addonBtnTimer =
            new arsenic.utils.timer.AnimationTimer(160, () -> addonBtnHovered,
                    arsenic.utils.timer.TickMode.SINE);
    private boolean hudBtnHovered;
    private final arsenic.utils.timer.AnimationTimer hudBtnTimer =
            new arsenic.utils.timer.AnimationTimer(160, () -> hudBtnHovered,
                    arsenic.utils.timer.TickMode.SINE);

    private long openTime;
    private static final int OPEN_ANIMATION_DURATION = 400;

    private boolean closing = false;
    private long closeStartTime;
    private static final int BURN_DURATION = 700;
    private net.minecraft.client.shader.Framebuffer burnFbo;

    private int burnDurationMs() {
        try {
            return GuiStyle.transitionTimeMs();
        } catch (Exception e) {
            return BURN_DURATION;
        }
    }


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

    public int getTransitionStyleId() {
        return GuiStyle.transition().ordinal();
    }

    public float[] getBurnBoxPx() {
        float s = this.scale;
        int bx = width / 8, by = height / 6;
        return new float[]{bx * s, by * s, (width - bx) * s, (height - by) * s, 30f * s};
    }

    public void init() {
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
        RenderUtils.captureCoverage = false;
    }

    public void drawBloom() {
        if (getFontRenderer() == null)
            return;
        rescale(this.scale);
        DrawUtils.overrideScaleFactor = this.scale;
        int x = width / 8;
        int y = height / 6;
        x1 = width - x;
        y1 = height - y;

        float openProgress = Math.min(1f, (System.currentTimeMillis() - openTime) / (float) OPEN_ANIMATION_DURATION);
        float glowFactor = MathUtils.clamp01((openProgress - 0.5f) / 0.5f);
        int glowAlpha = (int) (glowFactor * 255);

        RenderUtils.resetColor();
        int mainC = ColorUtils.setColor(ThemeManager.getMainColor(), 0, glowAlpha);
        int gradientC = ColorUtils.setColor(ThemeManager.getGradientColor(), 0, glowAlpha);
        ((SearchComponent) searchComponent).setupGlowAndBlur(glowAlpha);
        DrawUtils.drawGradientRoundedRect(x, y, x1, y1, 30f, mainC,mainC,gradientC, gradientC);
        DrawUtils.overrideScaleFactor = -1f;
        rescaleMC();
    }

    @Override
    public void drawScr(int mouseX, int mouseY, float partialTicks) {
        DrawUtils.overrideScaleFactor = this.scale;

        boolean burn = GuiStyle.transitionEnabled();
        float burnProgress = burn ? currentBurnProgress() : 1f;
        if (burn && closing && burnProgress <= 0f) {
            DrawUtils.overrideScaleFactor = -1f;
            mc.displayGuiScreen(null);
            return;
        }

        boolean captured = false;
        if (burn && burnProgress < 1f) {
            try {
                burnFbo = arsenic.utils.render.shader.ShaderUtil.createFrameBuffer(burnFbo);
                burnFbo.framebufferColor = new float[]{0f, 0f, 0f, 0f};
                burnFbo.framebufferClear();
                burnFbo.bindFramebuffer(false);
                captured = true;
                RenderUtils.captureCoverage = true;
            } catch (Exception e) {
                captured = false;
                RenderUtils.captureCoverage = false;
            }
        }

        drawShaderBackdrop();
        RenderInfo ri = new RenderInfo(mouseX, mouseY, getFontRenderer(), this);
        getFontRenderer().setScale(height/450f);
        int x = width / 8;
        int y = height / 6;
        x1 = width - x;
        y1 = height - y;
        ResourceLocation logoPath = GuiStyle.logoMode() == GuiStyle.LogoMode.MODERN
                ? Arsenic.getArsenic().getThemeManager().getCurrentTheme().getAltLogoPath()
                : Arsenic.getArsenic().getThemeManager().getCurrentTheme().getLogoPath();

        GlStateManager.pushMatrix();

        RenderUtils.resetColor();
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

        mc.getTextureManager().bindTexture(logoPath);
        int tempExpand = (int) (x * 0.1f);
        int logoCol = ThemeManager.getMainColor();
        RenderUtils.color2(logoCol, 1f);
        Gui.drawModalRectWithCustomSizedTexture(x + tempExpand, y + tempExpand, 0, 0, vLineX - x - (tempExpand * 2), hLineY - y - (tempExpand * 2), vLineX - x - (tempExpand * 2), hLineY - y - (tempExpand * 2) );
        GlStateManager.color(1f, 1f, 1f, 1f);

        if (addonMode) {
            addonPage.drawSidebar(catStartX, sy1 + catMargin, sx1, sx2 + expandMax, sy2 - catMargin, ri);
        } else {
            PosInfo pi = new PosInfo(catStartX, sy1 + catMargin);
            components.forEach(component -> pi.moveY(component.updateComponent(pi, ri)));
        }

        searchComponent.updateComponent(new PosInfo((vLineX + 5), (float) ((y + hLineY) / 2.05)), ri);

        ScissorUtils.subScissor(vLineX + 1, hLineY, x1, y1, 2);

        PosInfo piL = new PosInfo(vLineX + 5, hLineY);
        PosInfo piR = new PosInfo(vLineX + (x1 - vLineX) / 2f, hLineY);
        if (addonMode) {
            float columnWidth = arsenic.gui.click.UITheme.space(width, 30);
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
        ScissorUtils.resetScissor();

        GlStateManager.popMatrix();

        if (!addonMode)
            drawHudEditorButton(mouseX, mouseY);
        drawAddonManagerButton(mouseX, mouseY);
        if (!addonMode)
            drawTierToggles(mouseX, mouseY);

        getFontRenderer().resetScale();

        drawShaderOverlay();

        RenderUtils.captureCoverage = false;

        if (captured) {
            mc.getFramebuffer().bindFramebuffer(true);
            try {
                float s = this.scale;
                arsenic.utils.render.shader.ShaderUtil.renderBurnComposite(
                        burnFbo.framebufferTexture, burnProgress, ThemeManager.getMainColor(),
                        getTransitionStyleId(), x * s, y * s, x1 * s, y1 * s, 30f * s);
            } catch (Exception e) {
                try { burnFbo.framebufferRender(mc.displayWidth, mc.displayHeight); } catch (Exception ignored) {}
            }
        }

        DrawUtils.overrideScaleFactor = -1f;
    }

    private void drawHudEditorButton(int mouseX, int mouseY) {
        String label = "HUD Editor";
        float pad = height / 100f * 1.6f;
        float h = height / 100f * 4.2f;
        float w = getFontRenderer().getWidth(label) + pad * 3f;
        float margin = height / 100f * 2.5f;

        hudBtnX1 = margin;
        // sits above the tier pill(s) that take the corner below it
        hudBtnY1 = height - margin - h - TIER_TOGGLES.length * h * 1.3f;
        hudBtnX2 = margin + w;
        hudBtnY2 = hudBtnY1 + h;

        hudBtnHovered = MathUtils.inside(mouseX, mouseY, hudBtnX1, hudBtnY1, hudBtnX2, hudBtnY2);
        float hover = hudBtnTimer.getPercent();
        float radius = h / 2f;

        DrawUtils.drawShadow(hudBtnX1, hudBtnY1, hudBtnX2, hudBtnY2, radius,
                GuiStyle.shadowSpread(h * 0.35f), GuiStyle.shadowAlpha((int) (110 + 60 * hover)), 5);
        DrawUtils.drawRoundedRect(hudBtnX1, hudBtnY1, hudBtnX2, hudBtnY2, radius,
                GuiStyle.glassify(ThemeManager.getClickGuiBackground()));
        DrawUtils.drawRoundedOutline(hudBtnX1, hudBtnY1, hudBtnX2, hudBtnY2, radius, 1f,
                ColorUtils.setColor(ThemeManager.getMainColor(), 0, (int) (70 + 150 * hover)));

        getFontRenderer().drawString(label, (hudBtnX1 + hudBtnX2) / 2f, (hudBtnY1 + hudBtnY2) / 2f,
                RenderUtils.interpolateColoursInt(ThemeManager.getTextSecondary(), ThemeManager.getWhite(), hover),
                getFontRenderer().CENTREX, getFontRenderer().CENTREY);
        RenderUtils.resetColorText();
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
        float hover = addonBtnTimer.getPercent();
        float radius = h / 2f;

        DrawUtils.drawShadow(addonBtnX1, addonBtnY1, addonBtnX2, addonBtnY2, radius,
                GuiStyle.shadowSpread(h * 0.35f), GuiStyle.shadowAlpha((int) (110 + 60 * hover)), 5);
        DrawUtils.drawRoundedRect(addonBtnX1, addonBtnY1, addonBtnX2, addonBtnY2, radius,
                GuiStyle.glassify(ThemeManager.getClickGuiBackground()));
        DrawUtils.drawRoundedOutline(addonBtnX1, addonBtnY1, addonBtnX2, addonBtnY2, radius, 1f,
                ColorUtils.setColor(ThemeManager.getMainColor(), 0, (int) (70 + 150 * hover)));

        getFontRenderer().drawString(label, (addonBtnX1 + addonBtnX2) / 2f, (addonBtnY1 + addonBtnY2) / 2f,
                RenderUtils.interpolateColoursInt(ThemeManager.getTextSecondary(), ThemeManager.getWhite(), hover),
                getFontRenderer().CENTREX, getFontRenderer().CENTREY);
        RenderUtils.resetColorText();
    }

    private static final arsenic.module.ModuleTier[] TIER_TOGGLES = {
            arsenic.module.ModuleTier.BLATANT};
    private final float[][] tierRects = new float[TIER_TOGGLES.length][4];
    private final boolean[] tierHovered = new boolean[TIER_TOGGLES.length];
    private final arsenic.utils.timer.AnimationTimer[] tierHoverTimers = new arsenic.utils.timer.AnimationTimer[TIER_TOGGLES.length];
    private final arsenic.utils.timer.AnimationTimer[] tierTickTimers = new arsenic.utils.timer.AnimationTimer[TIER_TOGGLES.length];

    {
        for (int i = 0; i < TIER_TOGGLES.length; i++) {
            final int idx = i;
            tierHoverTimers[i] = new arsenic.utils.timer.AnimationTimer(160, () -> tierHovered[idx],
                    arsenic.utils.timer.TickMode.SINE);
            tierTickTimers[i] = new arsenic.utils.timer.AnimationTimer(180,
                    () -> GuiStyle.get().isTierShown(TIER_TOGGLES[idx]), arsenic.utils.timer.TickMode.SINE);
        }
    }

    /** The Blatant toggle pill in the bottom-left corner. */
    private void drawTierToggles(int mouseX, int mouseY) {
        float pad = height / 100f * 1.6f;
        float h = height / 100f * 4.2f;
        float box = h * 0.5f;
        float margin = height / 100f * 2.5f;
        float step = h * 1.3f;
        float radius = h / 2f;

        float w = 0;
        for (arsenic.module.ModuleTier tier : TIER_TOGGLES)
            w = Math.max(w, pad * 1.4f + box + pad + getFontRenderer().getWidth(tier.getDisplayName()) + pad * 1.4f);

        for (int i = 0; i < TIER_TOGGLES.length; i++) {
            arsenic.module.ModuleTier tier = TIER_TOGGLES[i];
            boolean locked = tier == arsenic.module.ModuleTier.LEGIT;
            float x1 = hudBtnX1, x2 = x1 + w;
            float y1 = height - margin - h - (TIER_TOGGLES.length - 1 - i) * step, y2 = y1 + h;
            tierRects[i][0] = x1;
            tierRects[i][1] = y1;
            tierRects[i][2] = x2;
            tierRects[i][3] = y2;

            tierHovered[i] = !locked && MathUtils.inside(mouseX, mouseY, x1, y1, x2, y2);
            float hover = tierHoverTimers[i].getPercent();
            float tick = tierTickTimers[i].getPercent();

            DrawUtils.drawShadow(x1, y1, x2, y2, radius,
                    GuiStyle.shadowSpread(h * 0.35f), GuiStyle.shadowAlpha((int) (110 + 60 * hover)), 5);
            DrawUtils.drawRoundedRect(x1, y1, x2, y2, radius, GuiStyle.glassify(ThemeManager.getClickGuiBackground()));
            DrawUtils.drawRoundedOutline(x1, y1, x2, y2, radius, 1f,
                    ColorUtils.setColor(ThemeManager.getMainColor(), 0, (int) (70 + 150 * hover)));

            float midY = (y1 + y2) / 2f;
            if (locked) {
                // always on, so no checkbox: just the label, centred on the same backing
                getFontRenderer().drawString(tier.getDisplayName(), (x1 + x2) / 2f, midY, ThemeManager.getWhite(),
                        getFontRenderer().CENTREX, getFontRenderer().CENTREY);
                RenderUtils.resetColorText();
                continue;
            }
            float bx1 = x1 + pad * 1.4f, bx2 = bx1 + box;
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
            RenderUtils.resetColorText();
        }
    }

    private void drawShaderBackdrop() {
        if (!GuiStyle.backgroundEnabled())
            return;

        String fsh = GuiStyle.backgroundShader();
        float alpha = GuiStyle.backgroundOpacity();
        float speed = GuiStyle.backgroundSpeed();
        if (alpha <= 0.001f)
            return;

        RenderUtils.resetColor();
        arsenic.utils.render.shader.ShaderUtil.renderFullscreen(
                fsh, alpha, speed,
                arsenic.utils.render.shader.ShaderUtil.BlendMode.NORMAL,
                ThemeManager.getMainColor(), 0.45f);
        RenderUtils.resetColor();
    }

    private void drawShaderOverlay() {
        if (!GuiStyle.scanlinesEnabled())
            return;
        RenderUtils.resetColor();
        arsenic.utils.render.shader.ShaderUtil.renderFullscreen(
                "vhsGlitch", 0.10f, 1.0f,
                arsenic.utils.render.shader.ShaderUtil.BlendMode.NORMAL,
                ThemeManager.getMainColor(), 0f);
        RenderUtils.resetColor();
    }

    @Override
    public void mouseClick(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0 && MathUtils.inside(mouseX, mouseY, addonBtnX1, addonBtnY1, addonBtnX2, addonBtnY2)) {
            arsenic.utils.java.SoundUtils.chordOpen();
            setAddonMode(!addonMode);
            return;
        }
        if (!addonMode && mouseButton == 0 && MathUtils.inside(mouseX, mouseY, hudBtnX1, hudBtnY1, hudBtnX2, hudBtnY2)) {
            arsenic.utils.java.SoundUtils.chordOpen();
            mc.displayGuiScreen(new arsenic.gui.hud.HudEditorScreen());
            return;
        }
        for (int i = 0; i < TIER_TOGGLES.length; i++) {
            if (!addonMode && mouseButton == 0 && MathUtils.inside(mouseX, mouseY, tierRects[i][0], tierRects[i][1], tierRects[i][2], tierRects[i][3])) {
                if (TIER_TOGGLES[i] == arsenic.module.ModuleTier.LEGIT)
                    return;
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
        String typed = searchComponent == null ? "" : ((SearchComponent) searchComponent).getQuery();
        init();
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
                    VanillaFontRenderer.of(mc.fontRendererObj).getFontRendererExtension();
        } catch (NullPointerException e) {
            return null;
        }
    }

    public void addToRenderLastList(Runnable v) {
        renderLastList.add(v);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int i = Mouse.getEventDWheel();
        i = Integer.compare(i, 0);
        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (addonMode && addonPage.isOverSidebar(mouseX, mouseY))
            addonPage.scrollSidebar(i * 30);
        else if (addonMode)
            addonPage.scroll(i * 30);
        else
            cmcc.scroll(i * 30);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        arsenic.utils.java.SoundUtils.tick();
        if(alwaysKeyboardInput != null) {
            alwaysKeyboardInput.recieveInput(keyCode);
            return;
        }
        if (keyCode == org.lwjgl.input.Keyboard.KEY_ESCAPE
                && GuiStyle.transitionEnabled()) {
            if (!closing) {
                closing = true;
                closeStartTime = System.currentTimeMillis();
            } else {
                mc.displayGuiScreen(null);
            }
            return;
        }
        ((SearchComponent) searchComponent).recieveInput(keyCode);
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void mouseRelease(int mouseX, int mouseY, int state) {
        components.forEach(component -> component.handleRelease(mouseX, mouseY, state));
        if (cmcc != null) cmcc.handleRelease(mouseX, mouseY, state);
        if (searchComponent != null) searchComponent.handleRelease(mouseX, mouseY, state);
        super.mouseRelease(mouseX, mouseY, state);
    }

    @Override
    public void onResize(Minecraft mcIn, int p_175273_2_, int p_175273_3_) {
        super.onResize(mcIn, p_175273_2_, p_175273_3_);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

}
