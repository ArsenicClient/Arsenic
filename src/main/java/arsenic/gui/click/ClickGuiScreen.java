package arsenic.gui.click;

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
import arsenic.utils.interfaces.IFontRenderer;
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

// allow escape to bind to none

public class ClickGuiScreen extends CustomGuiScreen {
    private List<UICategoryComponent> components;

    private ModuleCategoryComponent searchComponent;
    private final List<Runnable> renderLastList = new ArrayList<>();
    private ModuleCategoryComponent cmcc,prevCmcc;
    private IAlwaysClickable alwaysClickedComponent;
    private IAlwaysKeyboardInput alwaysKeyboardInput;
    private int vLineX, hLineY, x1, y1;

    // ---------------------------------------------------------------
    //  HUD Editor shortcut. It lives in the screen's bottom-left corner,
    //  deliberately outside the GUI container: it opens a different screen
    //  rather than changing anything inside this one, so it should not read as
    //  part of the panel's own controls.
    // ---------------------------------------------------------------
    private float hudBtnX1, hudBtnY1, hudBtnX2, hudBtnY2;
    private boolean hudBtnHovered;
    private final arsenic.utils.timer.AnimationTimer hudBtnTimer =
            new arsenic.utils.timer.AnimationTimer(160, () -> hudBtnHovered,
                    arsenic.utils.timer.TickMode.SINE);

    private long openTime;
    private static final int OPEN_ANIMATION_DURATION = 400;

    // burn-away open/close transition
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

    // ---------------------------------------------------------------
    //  Transition state, exposed so PostProcessing can fade its blur/bloom
    //  masks in step with the open/close transition (they render outside the
    //  burn capture and would otherwise linger at full strength).
    // ---------------------------------------------------------------

    /** 1 = fully present, 0 = fully transitioned out. */
    public float currentBurnProgress() {
        if (!GuiStyle.transitionEnabled())
            return 1f;
        int dur = burnDurationMs();
        long nowMs = System.currentTimeMillis();
        if (closing)
            return Math.max(0f, 1f - (nowMs - closeStartTime) / (float) dur);
        return Math.min(1f, (nowMs - openTime) / (float) dur);
    }

    /** True while the open/close transition is mid-flight. */
    public boolean isBurnActive() {
        return GuiStyle.transitionEnabled() && currentBurnProgress() < 1f;
    }

    /** {@link GuiStyle.Transition} ordinal, passed to the shaders as the style uniform. */
    public int getTransitionStyleId() {
        return GuiStyle.transition().ordinal();
    }

    /** Main box rect + corner radius in top-down real pixels: {x1, y1, x2, y2, radius}. */
    public float[] getBurnBoxPx() {
        float s = this.scale;
        int bx = width / 8, by = height / 6;
        return new float[]{bx * s, by * s, (width - bx) * s, (height - by) * s, 30f * s};
    }

    /**
     * Builds the component tree. Called once, from client startup - it used to hang off the
     * ClickGui module's config callback, which no longer exists.
     */
    public void init() {
        components = Arrays.stream(UICategory.values()).map(UICategoryComponent::new).distinct()
                .collect(Collectors.toList());
        cmcc = (ModuleCategoryComponent) components.get(0).getContents().toArray()[0];
        cmcc.setCurrentCategory(true);
        searchComponent = new SearchComponent(ModuleCategory.SEARCH);
    }

    //called every time the ui is created
    @Override
    public void doInit() {
        super.doInit();
        openTime = System.currentTimeMillis();
        closing = false;
        RenderUtils.captureCoverage = false; // safety: never leak into normal rendering
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

        // Glow fades in after the scale animation is mostly done
        float openProgress = Math.min(1f, (System.currentTimeMillis() - openTime) / (float) OPEN_ANIMATION_DURATION);
        float glowFactor = Math.max(0, Math.min(1, (openProgress - 0.5f) / 0.5f));
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
        // render corner rounding as if always on GUI scale Normal
        DrawUtils.overrideScaleFactor = this.scale;

        // burn transition: 1 = fully present, <1 = mid transition (to transparent)
        boolean burn = GuiStyle.transitionEnabled();
        float burnProgress = burn ? currentBurnProgress() : 1f;
        if (burn && closing && burnProgress <= 0f) { // fully gone -> actually close
            DrawUtils.overrideScaleFactor = -1f;
            mc.displayGuiScreen(null);
            return;
        }

        // While mid-burn, render the whole GUI into an offscreen buffer so the
        // dissolve can reveal true transparency (the world), not the GUI beneath.
        boolean captured = false;
        if (burn && burnProgress < 1f) {
            try {
                burnFbo = arsenic.utils.render.shader.ShaderUtil.createFrameBuffer(burnFbo);
                burnFbo.framebufferColor = new float[]{0f, 0f, 0f, 0f};
                burnFbo.framebufferClear();
                burnFbo.bindFramebuffer(false);
                captured = true;
                // GUI alpha must accumulate as true coverage while captured so
                // the burn composite reproduces on-screen opacity exactly
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

        // main container - base layer, lifted off the shader backdrop
        RenderUtils.resetColor();
        DrawUtils.drawShadow(x, y, x1, y1, 30f, GuiStyle.shadowSpread(10f), GuiStyle.shadowAlpha(190), 7);
        DrawUtils.drawRoundedRect(x, y, x1, y1, 30f, GuiStyle.glassify(ThemeManager.getClickGuiBackground()));
        DrawUtils.drawEdgeHighlight(x, y, x1, y1, 30f, ThemeManager.getMainColor(), GuiStyle.edgeAlpha(28));
        if (GuiStyle.glassEnabled())
            DrawUtils.drawGlassRect(x, y, x1, y1, 30f,
                    ColorUtils.setColor(ThemeManager.getMainColor(), 0, 18), ThemeManager.getWhite(), GuiStyle.glassStrength());

        vLineX = 2 * x;
        hLineY = (int) (1.5 * y);

        // raised sidebar panel sized to wrap the category pills with even margins
        int catWidth = 10 * (width / 100);
        float leftColW = vLineX - x;
        float catMargin = leftColW * 0.06f;      // gap between panel edge and pill
        float catStartX = x + leftColW * 0.10f;  // pills inset from the container edge
        float expandMax = catWidth / 40f;        // matches the pill's slide (below)
        float sx1 = catStartX - catMargin, sy1 = hLineY + catMargin;
        float sx2 = catStartX + catWidth + expandMax + catMargin, sy2 = y1 - catMargin;
        DrawUtils.drawShadow(sx1, sy1, sx2, sy2, 12f, GuiStyle.shadowSpread(6f), GuiStyle.shadowAlpha(150), 6);
        DrawUtils.drawRoundedRect(sx1, sy1, sx2, sy2, 12f, GuiStyle.glassify(ThemeManager.getModuleBackground()));
        DrawUtils.drawEdgeHighlight(sx1, sy1, sx2, sy2, 12f, ThemeManager.getMainColor(), GuiStyle.edgeAlpha(22));
        if (GuiStyle.glassEnabled())
            DrawUtils.drawGlassRect(sx1, sy1, sx2, sy2, 12f,
                    ColorUtils.setColor(ThemeManager.getMainColor(), 0, 14), ThemeManager.getWhite(), GuiStyle.glassStrength());

        // vertical line
        DrawUtils.drawRect(vLineX, y, vLineX + 1.0f, y1, ThemeManager.getClickGuiSeparator());
        // horizontal line
        DrawUtils.drawRect(x, hLineY, x1, hLineY + 1.0f, ThemeManager.getClickGuiSeparator());

        //logo
        mc.getTextureManager().bindTexture(logoPath);
        int tempExpand = (int) (x * 0.1f);
        int logoCol = ThemeManager.getMainColor();
        GlStateManager.color(((logoCol >> 16) & 0xFF) / 255f, ((logoCol >> 8) & 0xFF) / 255f, (logoCol & 0xFF) / 255f, 1f);
        Gui.drawModalRectWithCustomSizedTexture(x + tempExpand, y + tempExpand, 0, 0, vLineX - x - (tempExpand * 2), hLineY - y - (tempExpand * 2), vLineX - x - (tempExpand * 2), hLineY - y - (tempExpand * 2) );
        GlStateManager.color(1f, 1f, 1f, 1f);

        // draws each module category component, aligned inside the sidebar panel
        PosInfo pi = new PosInfo(catStartX, sy1 + catMargin);
        components.forEach(component -> pi.moveY(component.updateComponent(pi, ri)));

        //search
        searchComponent.updateComponent(new PosInfo((vLineX + 5), (float) ((y + hLineY) / 2.05)), ri);

        // makes the currently selected category component draw its modules
        ScissorUtils.subScissor(vLineX + 1, hLineY, x1, y1, 2);

        PosInfo piL = new PosInfo(vLineX + 5, hLineY);
        cmcc.drawLeft(piL, ri);
        PosInfo piR = new PosInfo(vLineX + (x1 - vLineX) / 2f, hLineY);
        cmcc.drawRight(piR, ri);
        cmcc.subtractFromMaxScrollHeight(y1 - hLineY);

        renderLastList.forEach(Runnable::run);
        renderLastList.clear();

        ScissorUtils.endSubScissor();
        cmcc.drawScrollbar(x1, hLineY, y1 - hLineY, ri);
        ScissorUtils.resetScissor();

        GlStateManager.popMatrix();

        drawHudEditorButton(mouseX, mouseY);

        getFontRenderer().resetScale();

        drawShaderOverlay();

        RenderUtils.captureCoverage = false; // capture done - back to normal blending

        // Composite the captured GUI back to the screen through the burn shader:
        // burnt areas become transparent (world shows), the edge glows in the
        // theme colour, and everything outside the box fades. Guarded with a
        // plain blit fallback so a shader hiccup can never hide the GUI.
        if (captured) {
            mc.getFramebuffer().bindFramebuffer(true); // restore the main render target
            try {
                float s = this.scale;
                arsenic.utils.render.shader.ShaderUtil.renderBurnComposite(
                        burnFbo.framebufferTexture, burnProgress, ThemeManager.getMainColor(),
                        getTransitionStyleId(), x * s, y * s, x1 * s, y1 * s, 30f * s);
            } catch (Exception e) {
                try { burnFbo.framebufferRender(mc.displayWidth, mc.displayHeight); } catch (Exception ignored) {}
            }
        }

        DrawUtils.overrideScaleFactor = -1f; // restore for HUD rendering
    }

    /**
     * Small pill in the bottom-left corner of the screen that opens the HUD editor. Sized from the
     * label rather than a fixed width so it stays proportional at any resolution.
     */
    private void drawHudEditorButton(int mouseX, int mouseY) {
        String label = "HUD Editor";
        float pad = height / 100f * 1.6f;
        float h = height / 100f * 4.2f;
        float w = getFontRenderer().getWidth(label) + pad * 3f;
        float margin = height / 100f * 2.5f;

        hudBtnX1 = margin;
        hudBtnY1 = height - margin - h;
        hudBtnX2 = margin + w;
        hudBtnY2 = height - margin;

        // Sample hover before the timer so the animation does not trail the cursor by a frame.
        hudBtnHovered = mouseX >= hudBtnX1 && mouseX <= hudBtnX2
                && mouseY >= hudBtnY1 && mouseY <= hudBtnY2;
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

    // Fullscreen animated shader rendered behind the whole ClickGUI. Overdone on purpose.
    private void drawShaderBackdrop() {
        if (!GuiStyle.backgroundEnabled())
            return;

        String fsh = GuiStyle.backgroundShader();
        float alpha = GuiStyle.backgroundOpacity();
        float speed = GuiStyle.backgroundSpeed();
        if (alpha <= 0.001f)
            return;

        RenderUtils.resetColor();
        // tint the backdrop toward the GUI's theme colour (managed via ThemeManager)
        arsenic.utils.render.shader.ShaderUtil.renderFullscreen(
                fsh, alpha, speed,
                arsenic.utils.render.shader.ShaderUtil.BlendMode.NORMAL,
                ThemeManager.getMainColor(), 0.45f);
        RenderUtils.resetColor();
    }

    // Subtle VHS/scanline pass layered over everything for extra flair.
    private void drawShaderOverlay() {
        if (!GuiStyle.scanlinesEnabled())
            return;
        RenderUtils.resetColor();
        // hand the theme colour to the scanline shader so it matches the GUI
        arsenic.utils.render.shader.ShaderUtil.renderFullscreen(
                "vhsGlitch", 0.10f, 1.0f,
                arsenic.utils.render.shader.ShaderUtil.BlendMode.NORMAL,
                ThemeManager.getMainColor(), 0f);
        RenderUtils.resetColor();
    }

    @Override
    public void mouseClick(int mouseX, int mouseY, int mouseButton) {
        // Checked before everything else: the button sits outside every component tree, and an
        // open dropdown claiming all clicks must not be able to eat it.
        if (mouseButton == 0 && mouseX >= hudBtnX1 && mouseX <= hudBtnX2
                && mouseY >= hudBtnY1 && mouseY <= hudBtnY2) {
            arsenic.utils.java.SoundUtils.chordOpen();
            mc.displayGuiScreen(new arsenic.gui.hud.HudEditorScreen());
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
                    ((IFontRenderer) mc.fontRendererObj).getFontRendererExtension();
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
        cmcc.scroll(i * 30);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        arsenic.utils.java.SoundUtils.tick();
        if(alwaysKeyboardInput != null) {
            // Whichever component is claiming all keyboard input is the ONLY thing that gets to
            // see this key, full stop - regardless of what it returns. A component that finishes
            // and clears its own registration while handling the key (e.g. a module keybind that
            // just got successfully set) used to make the check below read as "nobody's listening
            // anymore" and let that same keystroke fall through into the search bar and vanilla key
            // handling too, so e.g. binding "H" would also type "H" into the search box and refilter
            // the module list out from under you.
            alwaysKeyboardInput.recieveInput(keyCode);
            return;
        }
        // ESC plays the burn-away close; a second ESC while burning closes instantly
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
        // The module list (current category) and search results are handled outside `components`,
        // so their release must be dispatched explicitly — otherwise draggable property components
        // like the colour picker never see the mouse-up and stay stuck in the "clicked" state.
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
