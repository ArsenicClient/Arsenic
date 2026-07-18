package arsenic.gui.click;

import arsenic.gui.click.impl.ModuleCategoryComponent;
import arsenic.gui.click.impl.ModuleComponent;
import arsenic.gui.click.impl.SearchComponent;
import arsenic.gui.click.impl.UICategoryComponent;
import arsenic.gui.themes.ThemeManager;
import arsenic.main.Arsenic;
import arsenic.module.ModuleCategory;
import arsenic.module.impl.visual.ClickGui;
import arsenic.module.impl.visual.ClickGui.LogoMode;
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
    private ClickGui module;
    private List<UICategoryComponent> components;

    private ModuleCategoryComponent searchComponent;
    private final List<Runnable> renderLastList = new ArrayList<>();
    private ModuleCategoryComponent cmcc,prevCmcc;
    private IAlwaysClickable alwaysClickedComponent;
    private IAlwaysKeyboardInput alwaysKeyboardInput;
    private int vLineX, hLineY, x1, y1;

    private long openTime;
    private static final int OPEN_ANIMATION_DURATION = 400;

    // burn-away open/close transition
    private boolean closing = false;
    private long closeStartTime;
    private static final int BURN_DURATION = 700;
    private net.minecraft.client.shader.Framebuffer burnFbo;

    private int burnDurationMs() {
        try {
            return (int) (module.burnTime.getValue().getInput() * 1000);
        } catch (Exception e) {
            return BURN_DURATION;
        }
    }

    //called once
    public void init(ClickGui clickGui) {
        components = Arrays.stream(UICategory.values()).map(UICategoryComponent::new).distinct()
                .collect(Collectors.toList());
        cmcc = (ModuleCategoryComponent) components.get(0).getContents().toArray()[0];
        cmcc.setCurrentCategory(true);
        this.module = clickGui;
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
        // render corner rounding as if always on GUI scale Normal, and point the
        // drop shadows away from the configured "sun"
        DrawUtils.overrideScaleFactor = this.scale;
        if (module != null) {
            double ang = Math.toRadians(module.sunAngle.getValue().getInput());
            DrawUtils.shadowDirX = (float) -Math.cos(ang);
            DrawUtils.shadowDirY = (float) Math.sin(ang);
        }

        // burn transition: 1 = fully present, <1 = mid burn (dissolving to transparent)
        long nowMs = System.currentTimeMillis();
        int dur = burnDurationMs();
        boolean burn = module != null && module.burnTransition.getValue();
        float burnProgress = 1f;
        if (burn) {
            if (closing) {
                float cp = Math.min(1f, (nowMs - closeStartTime) / (float) dur);
                burnProgress = 1f - cp;             // GUI burns away
                if (cp >= 1f) {                     // fully gone -> actually close
                    DrawUtils.overrideScaleFactor = -1f;
                    mc.displayGuiScreen(null);
                    return;
                }
            } else {
                burnProgress = Math.min(1f, (nowMs - openTime) / (float) dur);
            }
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
        ClickGui clickGui = Arsenic.getArsenic().getModuleManager().getModuleByClass(ClickGui.class);
        ResourceLocation logoPath = clickGui.logoMode.getValue() == ClickGui.LogoMode.MODERN
                ? Arsenic.getArsenic().getThemeManager().getCurrentTheme().getAltLogoPath()
                : Arsenic.getArsenic().getThemeManager().getCurrentTheme().getLogoPath();

        GlStateManager.pushMatrix();

        // main container - base layer, lifted off the shader backdrop
        RenderUtils.resetColor();
        DrawUtils.drawShadow(x, y, x1, y1, 30f, ClickGui.shadowSpread(10f), ClickGui.shadowAlpha(190), 7);
        DrawUtils.drawRoundedRect(x, y, x1, y1, 30f, ThemeManager.getClickGuiBackground());
        DrawUtils.drawEdgeHighlight(x, y, x1, y1, 30f, ThemeManager.getMainColor(), ClickGui.edgeAlpha(28));

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
        DrawUtils.drawShadow(sx1, sy1, sx2, sy2, 12f, ClickGui.shadowSpread(6f), ClickGui.shadowAlpha(150), 6);
        DrawUtils.drawRoundedRect(sx1, sy1, sx2, sy2, 12f, ThemeManager.getModuleBackground());
        DrawUtils.drawEdgeHighlight(sx1, sy1, sx2, sy2, 12f, ThemeManager.getMainColor(), ClickGui.edgeAlpha(22));

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
                        x * s, y * s, x1 * s, y1 * s, 30f * s);
            } catch (Exception e) {
                try { burnFbo.framebufferRender(mc.displayWidth, mc.displayHeight); } catch (Exception ignored) {}
            }
        }

        DrawUtils.overrideScaleFactor = -1f; // restore for HUD rendering
    }

    // Fullscreen animated shader rendered behind the whole ClickGUI. Overdone on purpose.
    private void drawShaderBackdrop() {
        if (module == null || !module.shaderBackground.getValue())
            return;

        String fsh = module.backgroundShader.getValue().fsh;
        float alpha = (float) (module.backgroundOpacity.getValue().getInput() / 100.0);
        float speed = (float) module.backgroundSpeed.getValue().getInput();
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
        if (module == null || !module.scanlineOverlay.getValue())
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
            return module.customFont.getValue() ?
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
            if(alwaysKeyboardInput.recieveInput(keyCode)) return;
            if (alwaysKeyboardInput != null) return;
        }
        // ESC plays the burn-away close; a second ESC while burning closes instantly
        if (keyCode == org.lwjgl.input.Keyboard.KEY_ESCAPE
                && module != null && module.burnTransition.getValue()) {
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
