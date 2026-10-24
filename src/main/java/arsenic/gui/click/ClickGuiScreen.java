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
import arsenic.gui.design.Backdrop;
import arsenic.gui.design.M3;
import arsenic.gui.design.M3Components;
import arsenic.gui.design.M3Icons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

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
    private int x1, y1;

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
    private net.minecraft.client.shader.Framebuffer scaleFbo;

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

    public float currentScaleProgress() {
        if (!GuiStyle.transitionEnabled() || GuiStyle.transition() != GuiStyle.Transition.SCALE)
            return 1f;
        int dur = Math.max(180, burnDurationMs());
        long nowMs = System.currentTimeMillis();
        if (closing)
            return Math.max(0f, 1f - (nowMs - closeStartTime) / (float) dur);
        return Math.min(1f, (nowMs - openTime) / (float) dur);
    }

    public boolean isBurnActive() {
        return GuiStyle.transitionEnabled() && GuiStyle.transition() == GuiStyle.Transition.BURN
                && currentBurnProgress() < 1f;
    }

    public int getTransitionStyleId() {
        int id = GuiStyle.transition().ordinal();
        return id > 3 ? 0 : id;
    }

    /** Starts the shell close animation; pressing the GUI key again skips it. */
    public void startClosing() {
        if (closing) {
            mc.displayGuiScreen(null);
            return;
        }
        closing = true;
        closeStartTime = System.currentTimeMillis();
        setAlwaysInputComponent(null);
        setAlwaysClickedComponent(null);
    }

    /** 1 while the shell is hidden and offset for the opening/closing transition, 0 when settled. */
    private float shellPhase() {
        if (GuiStyle.transitionEnabled() && GuiStyle.transition() == GuiStyle.Transition.SCALE)
            return 0f;
        long now = System.currentTimeMillis();
        float p;
        if (closing) {
            if (GuiStyle.transitionEnabled()) {
                float burn = MathUtils.clamp01(currentBurnProgress());
                p = MathUtils.clamp01((1f - burn - 0.55f) / 0.45f);
            } else {
                p = MathUtils.clamp01((now - closeStartTime) / 220f);
            }
        } else if (GuiStyle.transitionEnabled()) {
            float burn = MathUtils.clamp01(currentBurnProgress());
            p = MathUtils.clamp01(1f - burn / 0.42f);
        } else {
            p = 1f - MathUtils.clamp01((now - openTime) / 280f);
        }
        return p * p * (3f - 2f * p);
    }

    private void offsetShell(float dy) {
        winY1 += dy;
        winY2 += dy;
        railY1 += dy;
        railY2 += dy;
        contentY1 += dy;
        contentY2 += dy;
        searchY1 += dy;
        searchY2 += dy;
        footerY1 += dy;
        footerY2 += dy;
    }

    @Override
    protected boolean fixedCanvas() {
        return true;
    }

    public float[] getBurnBoxPx() {
        int bx = width / 8, by = height / 6;
        return new float[]{GuiCanvas.pixelX(bx), GuiCanvas.pixelY(by),
                GuiCanvas.pixelX(width - bx), GuiCanvas.pixelY(height - by), 30f * GuiCanvas.scale()};
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
        GuiCanvas.setScaleFactor(arsenic.gui.click.GuiStyle.get().getClickGuiScale());
        openTime = System.currentTimeMillis();
        contentSwitchTime = openTime;
        closing = false;
        RenderUtils.captureCoverage = false;
    }

    /** Re-inits the current screen so a canvas scale change relays out immediately. */
    public void reapplyScale() {
        net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(this);
    }

    private float winX1, winY1, winX2, winY2;
    private float railX1, railY1, railX2, railY2;
    private long contentSwitchTime = System.currentTimeMillis() - 1000L;
    private float contentX1, contentY1, contentX2, contentY2;
    private float searchX1, searchY1, searchX2, searchY2;
    private float footerX1, footerY1, footerX2, footerY2;

    private void layout() {
        winX1 = 44f;
        winY1 = 30f;
        winX2 = width - 44f;
        winY2 = height - 30f;
        railX1 = winX1 + 14f;
        railY1 = winY1 + 14f;
        railX2 = winX1 + 206f;
        railY2 = winY2 - 14f;
        contentX1 = railX2 + 22f;
        contentY1 = winY1 + 76f;
        contentX2 = winX2 - 18f;
        footerX1 = contentX1;
        footerX2 = contentX2 - 8f;
        footerY2 = winY2 - 16f;
        footerY1 = footerY2 - 40f;
        contentY2 = footerY1 - 12f;
        searchX2 = contentX2 - 2f;
        searchX1 = searchX2 - 258f;
        searchY1 = winY1 + 18f;
        searchY2 = searchY1 + 38f;
    }

    public void drawBloom() {
        if (getFontRenderer() == null)
            return;
        GuiCanvas.setActive(true);
        try {
            rescaleGui();
            DrawUtils.overrideScaleFactor = this.scale;
            layout();
            x1 = (int) winX1;
            y1 = (int) winY1;

            float openProgress = Math.min(1f, (System.currentTimeMillis() - openTime) / (float) OPEN_ANIMATION_DURATION);
            float glowFactor = MathUtils.clamp01((openProgress - 0.5f) / 0.5f) * (1f - shellPhase());
            int glowAlpha = (int) (glowFactor * 255);

            RenderUtils.resetColor();
            int mainC = ColorUtils.setColor(M3.primary(), 0, glowAlpha);
            int gradientC = ColorUtils.setColor(M3.colors().primaryContainer, 0, glowAlpha);
            ((SearchComponent) searchComponent).setupGlowAndBlur(glowAlpha);
            DrawUtils.drawGradientRoundedRect(winX1, winY1, winX2, winY2, 28f, mainC, mainC, gradientC, gradientC);
            DrawUtils.overrideScaleFactor = -1f;
            rescaleMC();
        } finally {
            GuiCanvas.setActive(false);
        }
    }

    @Override
    public void drawScr(int mouseX, int mouseY, float partialTicks) {
        DrawUtils.overrideScaleFactor = this.scale;
        layout();

        boolean transition = GuiStyle.transitionEnabled();
        boolean scaleStyle = transition && GuiStyle.transition() == GuiStyle.Transition.SCALE;
        boolean burn = transition && !scaleStyle;
        float burnProgress = burn ? currentBurnProgress() : 1f;
        float scaleProgress = scaleStyle ? currentScaleProgress() : 1f;
        float shell = scaleStyle ? 0f : shellPhase();
        float shellOffset = shell * 16f;
        if (closing && (scaleStyle ? scaleProgress <= 0f : burn ? burnProgress <= 0f : shell >= 0.999f)) {
            DrawUtils.overrideScaleFactor = -1f;
            mc.displayGuiScreen(null);
            return;
        }
        if (shellOffset > 0.01f)
            offsetShell(shellOffset);

        boolean captured = false;
        if (scaleStyle) {
            Backdrop.draw(width, height);
            if (scaleProgress < 1f) {
                try {
                    scaleFbo = arsenic.utils.render.shader.ShaderUtil.createFrameBuffer(scaleFbo);
                    scaleFbo.framebufferColor = new float[]{0f, 0f, 0f, 0f};
                    scaleFbo.framebufferClear();
                    scaleFbo.bindFramebuffer(false);
                    captured = true;
                    RenderUtils.captureCoverage = true;
                } catch (Exception e) {
                    captured = false;
                    RenderUtils.captureCoverage = false;
                }
            }
        } else {
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

            Backdrop.draw(width, height);
        }

        RenderInfo ri = new RenderInfo(mouseX, mouseY, getFontRenderer(), this);
        float fontScale = height / 470f;
        M3.body().setScale(fontScale);
        M3.label().setScale(fontScale);
        M3.title().setScale(fontScale);
        M3.headline().setScale(fontScale);

        M3Components.window(winX1, winY1, winX2, winY2, 28f);
        M3Components.card(railX1, railY1, railX2, railY2, 22f, M3.alpha(M3.surfaceContainer(), 185));

        ResourceLocation logoPath = GuiStyle.logoMode() == GuiStyle.LogoMode.MODERN
                ? Arsenic.getArsenic().getThemeManager().getCurrentTheme().getAltLogoPath()
                : Arsenic.getArsenic().getThemeManager().getCurrentTheme().getLogoPath();
        float logoW = 96f;
        float logoH = logoW * 166f / 588f;
        mc.getTextureManager().bindTexture(logoPath);
        RenderUtils.color2(M3.onSurface(), 0.94f);
        Gui.drawModalRectWithCustomSizedTexture((int) (railX1 + 18f), (int) (railY1 + 18f), 0, 0,
                (int) logoW, (int) logoH, (int) logoW, (int) logoH);
        GlStateManager.color(1f, 1f, 1f, 1f);

        M3.body().drawString(Arsenic.getArsenic().getClientVersionString(), railX1 + 19f, railY1 + 52f,
                M3.alpha(M3.onSurfaceVariant(), 190), M3.body().CENTREY);
        RenderUtils.resetColorText();

        float reveal = contentReveal();

        GlStateManager.pushMatrix();
        if (addonMode) {
            addonPage.drawSidebar(railX1 + 9f, railY1 + 64f + reveal * 0.45f,
                    railX1 + 8f, railX2 - 8f, railY2 - 132f, ri);
        } else {
            PosInfo pi = new PosInfo(railX1 + 9f, railY1 + 64f + reveal * 0.45f);
            components.forEach(component -> pi.moveY(component.updateComponent(pi, ri)));
        }

        searchComponent.updateComponent(new PosInfo(searchX1, searchY1), ri);

        String title = addonMode ? "Addons" : (cmcc == searchComponent ? "Search" : cmcc.getName());
        String subtitle = addonMode ? "Manage the addons installed on this client" : categorySubtitle();
        M3.headline().drawString(title, contentX1, winY1 + 38f + reveal * 0.35f,
                M3.onSurface(), M3.headline().CENTREY);
        M3.body().drawString(subtitle, contentX1, winY1 + 55f + reveal * 0.35f,
                M3.alpha(M3.onSurfaceVariant(), 200), M3.body().CENTREY);
        RenderUtils.resetColorText();

        ScissorUtils.subScissor((int) contentX1, (int) contentY1, (int) contentX2, (int) contentY2, 2);
        float half = (contentX2 - contentX1) / 2f;
        PosInfo piL = new PosInfo(contentX1 + 2f, contentY1 + 2f + reveal);
        PosInfo piR = new PosInfo(contentX1 + half + 5f, contentY1 + 2f + reveal);
        if (addonMode) {
            addonPage.drawContent(piL.getX(), piR.getX(), contentY1, contentX2 - contentX1 - 4f, ri);
            addonPage.subtractFromMaxScrollHeight(contentY2 - contentY1);
        } else {
            cmcc.drawLeft(piL, ri);
            cmcc.drawRight(piR, ri);
            cmcc.subtractFromMaxScrollHeight(contentY2 - contentY1);
        }

        renderLastList.forEach(Runnable::run);
        renderLastList.clear();

        ScissorUtils.endSubScissor();
        if (addonMode)
            addonPage.drawScrollbar(contentX2, contentY1, contentY2 - contentY1, ri);
        else
            cmcc.drawScrollbar(contentX2, contentY1, contentY2 - contentY1, ri);
        ScissorUtils.resetScissor();

        GlStateManager.popMatrix();

        drawFooter(mouseX, mouseY);
        if (addonMode)
            drawReloadButton(mouseX, mouseY);

        getFontRenderer().resetScale();
        M3.label().resetScale();
        M3.title().resetScale();
        M3.headline().resetScale();

        RenderUtils.captureCoverage = false;

        if (captured) {
            mc.getFramebuffer().bindFramebuffer(true);
            if (scaleStyle) {
                float eased = scaleProgress * scaleProgress * (3f - 2f * scaleProgress);
                drawScaledFbo(scaleFbo, 0.92f + 0.08f * eased, scaleProgress);
            } else {
                try {
                    arsenic.utils.render.shader.ShaderUtil.renderBurnComposite(
                            burnFbo.framebufferTexture, burnProgress, M3.primary(),
                            getTransitionStyleId(), GuiCanvas.pixelX(winX1), GuiCanvas.pixelY(winY1),
                            GuiCanvas.pixelX(winX2), GuiCanvas.pixelY(winY2), 28f * GuiCanvas.scale());
                } catch (Exception e) {
                    try { burnFbo.framebufferRender(mc.displayWidth, mc.displayHeight); } catch (Exception ignored) {}
                }
            }
        }

        if (shell > 0.001f) {
            DrawUtils.drawRect(0f, 0f, width, height, M3.alpha(M3.scrim(), (int) (shell * 155f)));
            RenderUtils.resetColorText();
        }

        DrawUtils.overrideScaleFactor = -1f;
    }

    private void drawScaledFbo(net.minecraft.client.shader.Framebuffer fbo, float scale, float alpha) {
        int w = mc.displayWidth;
        int h = mc.displayHeight;

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, w, h, 0, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.disableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.color(1f, 1f, 1f, Math.max(0f, Math.min(1f, alpha)));

        GlStateManager.translate(w / 2f, h / 2f, 0f);
        GlStateManager.scale(scale, scale, 1f);
        GlStateManager.translate(-w / 2f, -h / 2f, 0f);

        RenderUtils.bindTexture(fbo.framebufferTexture);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0f, 1f);
        GL11.glVertex2f(0f, 0f);
        GL11.glTexCoord2f(0f, 0f);
        GL11.glVertex2f(0f, h);
        GL11.glTexCoord2f(1f, 0f);
        GL11.glVertex2f(w, h);
        GL11.glTexCoord2f(1f, 1f);
        GL11.glVertex2f(w, 0f);
        GL11.glEnd();

        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
    }

    private float contentReveal() {
        if (!GuiStyle.transitionEnabled())
            return 0f;
        float p = MathUtils.clamp01((System.currentTimeMillis() - contentSwitchTime) / (float) M3.DUR_MEDIUM);
        p = 1f - (1f - p) * (1f - p) * (1f - p);
        return (1f - p) * 10f;
    }

    private String categorySubtitle() {
        if (cmcc == searchComponent)
            return "Type to filter every module and addon";
        int count = cmcc.getContents().size();
        return count == 1 ? "1 module" : count + " modules";
    }

    private float reloadBtnX1, reloadBtnY1, reloadBtnX2, reloadBtnY2;

    private void drawReloadButton(int mouseX, int mouseY) {
        reloadBtnX2 = searchX1 - 10f;
        reloadBtnX1 = reloadBtnX2 - 108f;
        reloadBtnY1 = searchY1;
        reloadBtnY2 = searchY2;

        boolean hovered = MathUtils.inside(mouseX, mouseY, reloadBtnX1, reloadBtnY1, reloadBtnX2, reloadBtnY2);
        M3Components.button(getFontRenderer(), "Reload", reloadBtnX1, reloadBtnY1, reloadBtnX2, reloadBtnY2,
                hovered ? 1f : 0f, false);
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
            tierTickTimers[i] = new arsenic.utils.timer.AnimationTimer(220,
                    () -> GuiStyle.get().isTierShown(TIER_TOGGLES[idx]), arsenic.utils.timer.TickMode.SINE);
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

        boolean blatantHover = MathUtils.inside(mouseX, mouseY, tierRects[0][0], tierRects[0][1], tierRects[0][2], tierRects[0][3]);
        boolean addonHover = MathUtils.inside(mouseX, mouseY, addonBtnX1, addonBtnY1, addonBtnX2, addonBtnY2);
        boolean hudHover = MathUtils.inside(mouseX, mouseY, hudBtnX1, hudBtnY1, hudBtnX2, hudBtnY2);
        tierHovered[0] = blatantHover;
        addonBtnHovered = addonHover;
        hudBtnHovered = hudHover;

        float blatantHoverPct = tierHoverTimers[0].getPercent();
        float tick = tierTickTimers[0].getPercent();
        footerCard(tierRects[0][0], footerY1, tierRects[0][2], footerY2, blatantHoverPct, false);
        int blatantFg = M3.mix(M3.onSurfaceVariant(), M3.onSurface(), blatantHoverPct);
        footerLabel(tierRects[0][0], tierRects[0][2] - 59f, cy, M3Icons.BLATANT, "Blatant modules", blatantFg);
        M3Components.switchControl(tierRects[0][2] - 51f, footerY1, footerY2 - footerY1, tick, blatantHoverPct);

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

    private void footerLabel(float x1, float x2, float cy, String glyph, String label, int color) {
        float textW = getFontRenderer().getWidth(label);
        float iconW = M3Icons.font().getWidth(glyph);
        float groupW = iconW + 7f + textW;
        float start = x1 + (x2 - x1 - groupW) / 2f;
        M3Icons.drawCentered(glyph, start + iconW / 2f, cy, color);
        getFontRenderer().drawString(label, start + iconW + 7f, cy, color, getFontRenderer().CENTREY);
        RenderUtils.resetColorText();
    }

    private void footerCard(float x1, float y1, float x2, float y2, float hover, boolean active) {
        int fill = active
                ? M3.primaryContainer()
                : M3.mix(M3.surfaceContainerHigh(), M3.surfaceContainerHighest(), hover);
        DrawUtils.drawRoundedRect(x1, y1, x2, y2, (y2 - y1) / 2f, fill);
        DrawUtils.drawRoundedOutline(x1, y1, x2, y2, (y2 - y1) / 2f, 1f,
                M3.alpha(active ? M3.primary() : M3.outlineVariant(), active ? 120 : 100));
    }

    @Override
    public void mouseClick(int mouseX, int mouseY, int mouseButton) {
        if (closing || shellPhase() > 0.02f)
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
            mc.displayGuiScreen(new arsenic.gui.hud.HudEditorScreen());
            return;
        }
        for (int i = 0; i < TIER_TOGGLES.length; i++) {
            if (mouseButton == 0 && MathUtils.inside(mouseX, mouseY, tierRects[i][0], tierRects[i][1], tierRects[i][2], tierRects[i][3])) {
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
        contentSwitchTime = System.currentTimeMillis();
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
            contentSwitchTime = System.currentTimeMillis();
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
            contentSwitchTime = System.currentTimeMillis();
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
                    Arsenic.getInstance().getFonts().Inter.getFontRendererExtension() :
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
        int mouseX = (int) GuiCanvas.unitX(Mouse.getEventX());
        int mouseY = (int) GuiCanvas.unitY(mc.displayHeight - Mouse.getEventY());
        if (addonMode && addonPage.isOverSidebar(mouseX, mouseY))
            addonPage.scrollSidebar(i * 30);
        else if (addonMode)
            addonPage.scroll(i * 30);
        else
            cmcc.scroll(i * 30);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (closing) {
            if (keyCode == org.lwjgl.input.Keyboard.KEY_ESCAPE)
                mc.displayGuiScreen(null);
            return;
        }
        arsenic.utils.java.SoundUtils.tick();
        if(alwaysKeyboardInput != null) {
            alwaysKeyboardInput.recieveInput(keyCode);
            return;
        }
        if (keyCode == org.lwjgl.input.Keyboard.KEY_ESCAPE) {
            startClosing();
            return;
        }
        ((SearchComponent) searchComponent).recieveInput(keyCode);
        super.keyTyped(typedChar, keyCode);
    }

    /** Settings changed with sliders, switches and the like are saved here, once, instead of on every change. */
    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        setAlwaysInputComponent(null);
        Arsenic.getArsenic().getConfigManager().saveConfig();
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
