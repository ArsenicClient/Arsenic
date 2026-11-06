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
import arsenic.gui.design.Hover;
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
        // the burn itself reveals and removes the GUI; a sliding shell and dark scrim on top would hide it
        if (GuiStyle.transitionEnabled())
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

    /** The burn's box is the window, so the post-processing masks vanish in the same pattern as the GUI. */
    public float[] getBurnBoxPx() {
        layout();
        return new float[]{GuiCanvas.pixelX(winX1), GuiCanvas.pixelY(winY1),
                GuiCanvas.pixelX(winX2), GuiCanvas.pixelY(winY2), 28f * GuiCanvas.scale()};
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
        Hover.reset();
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
    /** The bottom bar holding the footer buttons; the module list scrolls under its top edge. */
    private float trayX1, trayY1, trayX2, trayY2;

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
        GuiStyle.tickFrame();
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

        Hover.beginFrame(mouseX, mouseY);
        if (closing || shell > 0.02f)
            Hover.suppress(true);

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
        M3Components.glass(railX1, railY1, railX2, railY2, 22f, M3.alpha(M3.surfaceContainer(), 200), 0.25f);

        // as large as the rail's width allows, centred in the header band above the categories
        float logoX = railX1 + 16f;
        float logoH = arsenic.gui.design.LabLogo.heightFor(railX2 - railX1 - 32f, 32f);
        float logoY = railY1 + (60f - logoH) / 2f;
        boolean logoHover = Hover.test(LOGO_KEY, logoX, logoY, logoX + arsenic.gui.design.LabLogo.width(logoH), logoY + logoH);
        arsenic.gui.design.LabLogo.draw(logoX, logoY, logoH, logoHover);
        GlStateManager.color(1f, 1f, 1f, 1f);
        RenderUtils.resetColorText();


        GlStateManager.pushMatrix();
        float mascotTop = railY2 - 10f - MASCOT_H;
        if (addonMode) {
            // the addon list scrolls in the space above the mascot's slot
            float sideX1 = railX1 + 8f, sideX2 = railX2 - 8f, sideY1 = railY1 + 60f, sideY2 = mascotTop - 6f;
            boolean sideCaptured = beginListCapture();
            addonPage.drawSidebar(railX1 + 9f, sideY1, sideX1, sideX2, sideY2, ri);
            if (sideCaptured)
                endListCapture(sideX1, sideY1, sideX2, sideY2, addonPage.sideHiddenAbove(), addonPage.sideHiddenBelow());

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
        RenderUtils.resetColorText();

        ScissorUtils.subScissor((int) contentX1, (int) contentY1, (int) contentX2, (int) contentY2, 2);
        Hover.pushClip(contentX1, contentY1, contentX2, contentY2);
        boolean listCaptured = beginListCapture();
        float colGap = 10f;
        // the columns sit 2 units inside the clip so a card's outline (drawn on its edge) isn't cut off
        float inset = 2f;
        float colX = contentX1 + inset;
        float colW = (contentX2 - 8f - colX - colGap) / 2f;   // 8: the scrollbar's lane
        ModuleComponent.columnWidth = colW;
        ModuleCategoryComponent.contentWidth = contentX2 - contentX1;
        float top = contentY1 + 2f;
        if (addonMode) {
            float slide = slideProgress();
            float span = contentX2 - contentX1;
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
            float slide = slideProgress();
            float span = contentX2 - contentX1;
            if (slide < 1f && slideFromAddon) {
                float off = -slideDir * span * slide;
                boolean wasSuppressed = Hover.isSuppressed();
                Hover.suppress(true);
                addonPage.drawContent(colX + off, colX + colW + colGap + off, contentY1, contentX2 - colX - 4f, ri);
                Hover.suppress(wasSuppressed);
            } else if (slide < 1f && slideFrom != null && slideFrom != cmcc) {
                // the old category leaves; it can't take the cursor on its way out
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
        if (listCaptured) {
            float above = addonMode ? addonPage.hiddenAbove() : cmcc.hiddenAbove();
            float below = addonMode ? addonPage.hiddenBelow() : cmcc.hiddenBelow();
            endListCapture(above, below);
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
        ScissorUtils.resetScissor();

        GlStateManager.popMatrix();

        drawTray();
        drawFooter(mouseX, mouseY);
        if (addonMode)
            drawReloadButton(mouseX, mouseY);

        Backdrop.overlay();

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

    /** The distillery fills all the room the nav list leaves at the bottom of the rail. */
    private void drawMascot(float navBottom, int mouseX, int mouseY) {
        float y2 = railY2 - 10f;
        float y1 = navBottom + 6f;
        float x1 = railX1 + 10f, x2 = railX2 - 10f;
        boolean hover = Hover.test(MASCOT_KEY, x1, y1, x2, y2);
        sceneX1 = x1; sceneY1 = y1; sceneX2 = x2; sceneY2 = y2;
        arsenic.gui.design.DistilleryScene.draw(x1, y1, x2, y2, hover, mouseX, mouseY);
        GlStateManager.color(1f, 1f, 1f, 1f);
        RenderUtils.resetColorText();
    }

    private float sceneX1, sceneY1, sceneX2, sceneY2;
    private float reloadBtnX1, reloadBtnY1, reloadBtnX2, reloadBtnY2;
    private static final Object LOGO_KEY = new Object(), MASCOT_KEY = new Object();
    /** In the addon manager the scene keeps this much of the rail; the addon list scrolls above it. */
    private static final float MASCOT_H = 151f;
    private static final Object RELOAD_KEY = new Object(), BLATANT_KEY = new Object(),
            ADDON_KEY = new Object(), HUD_KEY = new Object();

    private void drawReloadButton(int mouseX, int mouseY) {
        reloadBtnX2 = searchX1 - 10f;
        reloadBtnX1 = reloadBtnX2 - 108f;
        reloadBtnY1 = searchY1;
        reloadBtnY2 = searchY2;

        boolean hovered = Hover.test(RELOAD_KEY, reloadBtnX1, reloadBtnY1, reloadBtnX2, reloadBtnY2);
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

        boolean blatantHover = Hover.test(BLATANT_KEY, tierRects[0][0], tierRects[0][1], tierRects[0][2], tierRects[0][3]);
        boolean addonHover = Hover.test(ADDON_KEY, addonBtnX1, addonBtnY1, addonBtnX2, addonBtnY2);
        boolean hudHover = Hover.test(HUD_KEY, hudBtnX1, hudBtnY1, hudBtnX2, hudBtnY2);
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

    /** The bottom bar behind the footer buttons: a rounded panel, inset like the rail. */
    private void drawTray() {
        int tray = M3.mix(M3.surfaceContainer(), M3.surfaceContainerLow(), 0.5f) | 0xFF000000;
        float r = 16f;
        DrawUtils.drawRoundedRect(trayX1, trayY1, trayX2, trayY2, r, tray);
        DrawUtils.drawRoundedOutline(trayX1, trayY1, trayX2, trayY2, r, 1f, M3.alpha(M3.outlineVariant(), 130));
    }

    // ---- list edge fade ----

    private net.minecraft.client.shader.Framebuffer listFbo;
    private int listPrevFbo;
    private boolean listPrevCoverage;
    /** How far the list fades to transparent at its top and bottom edges. */
    private static final float LIST_FADE = 12f;

    /**
     * Sends the module list to its own transparent texture so its edges can fade out instead of being
     * cut off. Returns false (draw straight to the screen) where framebuffers aren't available.
     */
    private boolean beginListCapture() {
        if (!net.minecraft.client.renderer.OpenGlHelper.isFramebufferEnabled())
            return false;
        listPrevFbo = GL11.glGetInteger(36006);                // GL_FRAMEBUFFER_BINDING; the burn may own it
        listPrevCoverage = RenderUtils.captureCoverage;
        try {
            listFbo = arsenic.utils.render.shader.ShaderUtil.createFrameBuffer(listFbo);
            listFbo.framebufferColor = new float[]{0f, 0f, 0f, 0f};
            listFbo.framebufferClear();
            listFbo.bindFramebuffer(false);
            RenderUtils.captureCoverage = true;
            return true;
        } catch (Exception e) {
            restoreListTarget();
            return false;
        }
    }

    private void restoreListTarget() {
        net.minecraft.client.renderer.OpenGlHelper.glBindFramebuffer(
                net.minecraft.client.renderer.OpenGlHelper.GL_FRAMEBUFFER, listPrevFbo);
        GlStateManager.viewport(0, 0, mc.displayWidth, mc.displayHeight);
        RenderUtils.captureCoverage = listPrevCoverage;
    }

    /**
     * Draws the captured list back. An edge only fades while there is list hidden past it: the top once
     * you scroll down, the bottom while more rows wait below; each ramps in over the first LIST_FADE
     * units of scrolling, so an unscrolled list starts crisp.
     */
    private void endListCapture(float hiddenAbove, float hiddenBelow) {
        endListCapture(contentX1, contentY1, contentX2, contentY2, hiddenAbove, hiddenBelow);
    }

    private void endListCapture(float x1, float y1, float x2, float y2, float hiddenAbove, float hiddenBelow) {
        restoreListTarget();

        float f = Math.min(LIST_FADE, (y2 - y1) / 4f);
        float u1 = GuiCanvas.pixelX(x1) / mc.displayWidth, u2 = GuiCanvas.pixelX(x2) / mc.displayWidth;

        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        // the capture is premultiplied, so fading every channel together is a correct fade
        GlStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.bindTexture(listFbo.framebufferTexture);
        net.minecraft.client.renderer.Tessellator tess = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_TEX_COLOR);
        float[] ys = {y1, y1 + f, y2 - f, y2};
        float topEdge = 1f - MathUtils.clamp01(hiddenAbove / LIST_FADE);
        float bottomEdge = 1f - MathUtils.clamp01(hiddenBelow / LIST_FADE);
        float[] as = {topEdge, 1f, 1f, bottomEdge};
        for (int i = 0; i < 3; i++) {
            float ya = ys[i], yb = ys[i + 1];
            float va = 1f - GuiCanvas.pixelY(ya) / mc.displayHeight, vb = 1f - GuiCanvas.pixelY(yb) / mc.displayHeight;
            float aa = as[i], ab = as[i + 1];
            wr.pos(x1, ya, 0).tex(u1, va).color(aa, aa, aa, aa).endVertex();
            wr.pos(x1, yb, 0).tex(u1, vb).color(ab, ab, ab, ab).endVertex();
            wr.pos(x2, yb, 0).tex(u2, vb).color(ab, ab, ab, ab).endVertex();
            wr.pos(x2, ya, 0).tex(u2, va).color(aa, aa, aa, aa).endVertex();
        }
        tess.draw();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.enableAlpha();
        GlStateManager.color(1f, 1f, 1f, 1f);
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
        float r = 10f;
        M3Components.glass(x1, y1, x2, y2, r, fill, 0f);
        if (active)
            DrawUtils.drawRoundedOutline(x1, y1, x2, y2, r, 1f, M3.alpha(M3.primary(), 140));
        if (hover > 0.01f)
            DrawUtils.drawRoundedRect(x1, y1, x2, y2, r, M3.alpha(M3.onSurface(), (int) (14 * hover)));
    }

    @Override
    public void mouseClick(int mouseX, int mouseY, int mouseButton) {
        if (closing || shellPhase() > 0.02f)
            return;
        // a bind being picked takes the middle and side mouse buttons, wherever they are pressed
        if (alwaysKeyboardInput != null && alwaysKeyboardInput.acceptsMouseButtons()
                && arsenic.utils.java.BindUtils.isBindableButton(mouseButton)) {
            arsenic.utils.java.SoundUtils.tick();
            alwaysKeyboardInput.recieveInput(arsenic.utils.java.BindUtils.mouseKey(mouseButton));
            return;
        }
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
        slideFrom = on ? cmcc : null;
        slideFromAddon = !on;
        slideDir = on ? 1f : -1f;
        slideStart = System.currentTimeMillis();
        Hover.reset();
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
            int from = navIndex(cmcc), to = navIndex(mcc);
            slideDir = to >= from ? 1f : -1f;
            slideFrom = cmcc;
            slideFromAddon = false;
            slideStart = System.currentTimeMillis();
            Hover.reset();
            cmcc.setCurrentCategory(false);
            mcc.setCurrentCategory(true);
            cmcc = mcc;
            contentSwitchTime = System.currentTimeMillis();
        }
    }
    private ModuleCategoryComponent slideFrom;
    private boolean slideFromAddon;
    private float slideDir = 1f;
    private long slideStart;
    private static final int SLIDE_MS = 280;

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
