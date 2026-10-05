package arsenic.gui.click;

import arsenic.utils.render.RenderUtils;
import arsenic.utils.java.MathUtils;
import arsenic.gui.click.impl.ModuleCategoryComponent;
import arsenic.gui.click.impl.SearchComponent;
import arsenic.gui.click.impl.UICategoryComponent;
import arsenic.gui.themes.ThemeManager;
import arsenic.main.Arsenic;
import arsenic.module.ModuleCategory;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.interfaces.IAlwaysClickable;
import arsenic.utils.interfaces.IAlwaysKeyboardInput;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.render.*;
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
    private boolean hudBtnHovered;
    private final arsenic.utils.timer.AnimationTimer hudBtnTimer =
            new arsenic.utils.timer.AnimationTimer(160, () -> hudBtnHovered,
                    arsenic.utils.timer.TickMode.SINE);

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
            drawMoreModulesToggle(mouseX, mouseY);
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

        PosInfo pi = new PosInfo(catStartX, sy1 + catMargin);
        components.forEach(component -> pi.moveY(component.updateComponent(pi, ri)));

        searchComponent.updateComponent(new PosInfo((vLineX + 5), (float) ((y + hLineY) / 2.05)), ri);

        ScissorUtils.subScissor(vLineX + 1, hLineY, x1, y1);

        PosInfo piL = new PosInfo(vLineX + 5, hLineY);
        cmcc.drawLeft(piL, ri);
        PosInfo piR = new PosInfo(vLineX + (x1 - vLineX) / 2f, hLineY);
        cmcc.drawRight(piR, ri);
        cmcc.subtractFromMaxScrollHeight(y1 - hLineY);

        renderLastList.forEach(Runnable::run);
        renderLastList.clear();

        ScissorUtils.endSubScissor();
        cmcc.drawScrollbar(x1, hLineY, y1 - hLineY, ri);
    }

    private void drawHudEditorButton(int mouseX, int mouseY) {
        String label = "HUD Editor";
        float pad = height / 100f * 1.6f;
        float h = height / 100f * 4.2f;
        float w = getFontRenderer().getWidth(label) + pad * 3f;
        float margin = height / 100f * 2.5f;

        hudBtnX1 = margin;
        // sits one pill-height above the bottom edge: the "More modules" pill takes the corner below it
        hudBtnY1 = height - margin - h - h * 1.3f;
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
    }

    private float moreX1, moreY1, moreX2, moreY2;
    private boolean moreHovered;
    private final arsenic.utils.timer.AnimationTimer moreHoverTimer =
            new arsenic.utils.timer.AnimationTimer(160, () -> moreHovered,
                    arsenic.utils.timer.TickMode.SINE);
    private final arsenic.utils.timer.AnimationTimer moreTickTimer =
            new arsenic.utils.timer.AnimationTimer(180, () -> GuiStyle.get().isShowMoreModules(),
                    arsenic.utils.timer.TickMode.SINE);

    private void drawMoreModulesToggle(int mouseX, int mouseY) {
        String label = "More modules";
        float pad = height / 100f * 1.6f;
        float h = height / 100f * 4.2f;
        float box = h * 0.5f;
        float w = pad * 1.4f + box + pad + getFontRenderer().getWidth(label) + pad * 1.4f;

        moreX1 = hudBtnX1;
        moreX2 = moreX1 + w;
        moreY1 = hudBtnY2 + h * 0.3f;
        moreY2 = moreY1 + h;

        moreHovered = MathUtils.inside(mouseX, mouseY, moreX1, moreY1, moreX2, moreY2);
        float hover = moreHoverTimer.getPercent();
        float tick = moreTickTimer.getPercent();
        float radius = h / 2f;

        DrawUtils.drawShadow(moreX1, moreY1, moreX2, moreY2, radius,
                GuiStyle.shadowSpread(h * 0.35f), GuiStyle.shadowAlpha((int) (110 + 60 * hover)), 5);
        DrawUtils.drawRoundedRect(moreX1, moreY1, moreX2, moreY2, radius,
                GuiStyle.glassify(ThemeManager.getClickGuiBackground()));
        DrawUtils.drawRoundedOutline(moreX1, moreY1, moreX2, moreY2, radius, 1f,
                ColorUtils.setColor(ThemeManager.getMainColor(), 0, (int) (70 + 150 * hover)));

        float midY = (moreY1 + moreY2) / 2f;
        float bx1 = moreX1 + pad * 1.4f, bx2 = bx1 + box;
        float by1 = midY - box / 2f, by2 = midY + box / 2f;
        float boxRadius = Math.max(2f, box * 0.28f);
        DrawUtils.drawRoundedRect(bx1, by1, bx2, by2, boxRadius,
                UITheme.alpha(UITheme.accent(), (int) (235 * tick)));
        DrawUtils.drawRoundedOutline(bx1, by1, bx2, by2, boxRadius, 1f,
                UITheme.mix(UITheme.alpha(ThemeManager.getTextMuted(), 200), UITheme.accent(), Math.max(tick, hover * 0.6f)));
        if (tick > 0.02f)
            UITheme.check((bx1 + bx2) / 2f, midY, box * 0.7f, Math.max(1f, box * 0.14f),
                    UITheme.alpha(UITheme.readableOn(UITheme.accent()), (int) (255 * tick)));

        getFontRenderer().drawString(label, bx2 + pad, midY,
                RenderUtils.interpolateColoursInt(ThemeManager.getTextSecondary(), ThemeManager.getWhite(),
                        Math.max(hover, tick)),
                getFontRenderer().CENTREY);
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
        DrawUtils.drawGradientRect(0, 0, width, height, top, bottom);
    }

    /** Faint scanlines layered over everything, standing in for the old VHS shader. */
    private void drawScanlines() {
        if (!GuiStyle.scanlinesEnabled())
            return;
        int line = ColorUtils.setColor(0x000000, 0, 26);
        QuadBatch batch = new QuadBatch();
        float offset = (System.currentTimeMillis() % 4000L) / 4000f * 4f;
        for (float ly = offset; ly < height; ly += 4f)
            batch.rect(0, ly, width, ly + 1f, line);
        batch.submit();
    }

    @Override
    public void mouseClick(int mouseX, int mouseY, int mouseButton) {
        if (components == null)
            return;
        if (mouseButton == 0 && MathUtils.inside(mouseX, mouseY, hudBtnX1, hudBtnY1, hudBtnX2, hudBtnY2)) {
            arsenic.utils.java.SoundUtils.chordOpen();
            minecraft.gui.setScreen(new arsenic.gui.hud.HudEditorScreen());
            return;
        }
        if (mouseButton == 0 && MathUtils.inside(mouseX, mouseY, moreX1, moreY1, moreX2, moreY2)) {
            GuiStyle.get().setShowMoreModules(!GuiStyle.get().isShowMoreModules());
            Arsenic.getArsenic().getConfigManager().saveConfig();
            arsenic.utils.java.SoundUtils.chordEnum();
            return;
        }
        if(alwaysClickedComponent != null) {
            if(alwaysClickedComponent.clickAlwaysClickable(mouseX, mouseY, mouseButton)) return;
        }
        searchComponent.handleClick(mouseX, mouseY, mouseButton);
        components.forEach(panel -> panel.handleClick(mouseX, mouseY, mouseButton));
        if(mouseX > vLineX && mouseX < x1 && mouseY > hLineY && mouseY < y1)
            cmcc.clickChildren(mouseX, mouseY, mouseButton);
    }

    public void setCmcc(ModuleCategoryComponent mcc) {
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
        if (cmcc != null)
            cmcc.scroll((int) Math.signum(amount) * 30);
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

    @Override
    public void removed() {
        RenderContext.setAlpha(1f);
        super.removed();
    }
}
