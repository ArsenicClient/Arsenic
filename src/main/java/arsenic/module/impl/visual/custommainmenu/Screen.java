package arsenic.module.impl.visual.custommainmenu;

import arsenic.utils.render.RenderUtils;
import arsenic.gui.themes.ThemeManager;
import arsenic.main.Arsenic;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.font.VanillaFontRenderer;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.java.MathUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.timer.FrameClock;
import arsenic.utils.timer.HoverAnimation;
import arsenic.utils.timer.MSTimer;
import arsenic.utils.timer.TickMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;

import java.awt.Color;
import java.io.IOException;

public class Screen extends GuiScreen {

    private static final ResourceLocation LOGO = new ResourceLocation("arsenic", "logos/modern.png");
    private static final float LOGO_ASPECT = 166f / 588f;

    private final MSTimer opened = new MSTimer();
    private final FrameClock clock = new FrameClock();

    private float smoothMX, smoothMY;


    static boolean isLight() {
        return ColorUtils.luminance(MenuTheme.bg()) > 0.6f;
    }

    static int ink(int alpha) {
        int c = isLight() ? 0x121217 : 0xFFFFFF;
        return ColorUtils.withAlpha(c, alpha);
    }

    static int accent(int alpha) {
        return ColorUtils.withAlpha(MenuTheme.main(), alpha);
    }


    private int panelX, panelY, panelW, panelH;

    @Override
    public void initGui() {
        int bw = 200, bh = 22, gap = 6, pad = 14;
        panelW = bw + pad * 2;
        panelH = 5 * bh + 4 * gap + pad * 2;
        panelX = this.width / 2 - panelW / 2;
        panelY = (int) (this.height * 0.47f);
        int x = panelX + pad, y = panelY + pad;
        String[] labels = {I18n.format("menu.singleplayer"), I18n.format("menu.multiplayer"), "Mods",
                I18n.format("menu.options"), I18n.format("menu.quit")};
        int[] ids = {1, 2, 3, 0, 4};
        for (int i = 0; i < 5; i++)
            this.buttonList.add(new CustomGuiButton(ids[i], x, y + (bh + gap) * i, bw, bh, labels[i], opened, i));
        this.buttonList.add(new CustomGuiButton(5, this.width - 108, this.height - 28, 100, 20, "Vanilla Menu", opened, 5));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        Gui.drawRect(0, 0, 0, 0, new Color(255, 255, 255, 40).getRGB());

        float dt = clock.tick();
        long elapsed = opened.getTime();
        float time = elapsed / 1000f;
        float fade = TickMode.CUBIC.clamped(elapsed / 700f);

        smoothMX = FrameClock.approach(smoothMX, mouseX / (float) this.width - 0.5f, 4f, dt);
        smoothMY = FrameClock.approach(smoothMY, mouseY / (float) this.height - 0.5f, 4f, dt);

        int main = MenuTheme.main();
        int bg = MenuTheme.bg();
        boolean light = isLight();
        // the shared ocean backdrop, the same one every other screen uses
        MenuTheme.drawBackground(this.width, this.height, mouseX, mouseY, fade);

        // logo with a soft pulsing halo
        float lw = Math.min(this.width * 0.55f, 230f), lh = lw * LOGO_ASPECT;
        float lx = this.width / 2f - lw / 2f + smoothMX * 8f;
        float ly = this.height * 0.27f - lh / 2f + smoothMY * 8f - (1f - fade) * 10f;
        float pulse = 0.75f + 0.25f * (float) Math.sin(time * 1.6f);
        drawGlow(lx + lw / 2f, ly + lh / 2f, lw * 0.95f, accent((int) (70 * fade * pulse)));

        mc.getTextureManager().bindTexture(LOGO);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        RenderUtils.color2(ink(255), fade);
        Gui.drawModalRectWithCustomSizedTexture((int) lx, (int) ly, 0, 0, (int) lw, (int) lh, lw, lh);
        GlStateManager.color(1f, 1f, 1f, 1f);

        // Element 33: arsenic's own periodic-table tile beside the logo, pulsing softly
        if (arsenic.gui.click.GuiStyle.element()) {
            float tileSize = 50f, tx = lx + lw + 18f, ty = ly + lh / 2f - tileSize / 2f;
            if (tx + tileSize < this.width - 4)
                ElementScene.drawElementTile(tx, ty, tileSize, main, 0.6f + 0.4f * (float) Math.sin(time * 1.4f), fade);
        }

        int a = (int) (255 * fade);
        int ulHalf = (int) (50 * TickMode.CUBIC.clamped((elapsed - 150) / 700f));
        int ulY = (int) (ly + lh + 12);
        int end = (ColorUtils.mixRgb(main, light ? 0x000000 : 0xFFFFFF, 0.35f) & 0xFFFFFF) | a << 24;
        drawHorizontalGradient(this.width / 2 - ulHalf, ulY, this.width / 2 + ulHalf, ulY + 2, accent(a), end);

        FontRendererExtension<?> fr = VanillaFontRenderer.of(mc.fontRendererObj).getFontRendererExtension();
        String tag = "Arsenic Client  -  v" + modVersion();
        fr.drawStringWithShadow(tag, this.width / 2f, ulY + 12, ink((int) (140 * fade)), fr.CENTREX, fr.CENTREY);

        float slide = (1f - TickMode.CUBIC.clamped((elapsed - 250) / 700f)) * 14f;
        int pa = (int) (fade * 255);
        MenuTheme.drawPill(panelX, panelY + slide, panelX + panelW, panelY + panelH + slide, 12f, 1f,
                ((int) (120 * pa / 255f) << 24) | (ColorUtils.mixRgb(MenuTheme.bg(), 0x000000, 0.5f) & 0xFFFFFF), ink((int) (40 * pa / 255f)));
        DrawUtils.drawRoundedRect(panelX + 24, panelY + slide, panelX + panelW - 24, panelY + 1.5f + slide, 0f, accent((int) (120 * pa / 255f)));

        super.drawScreen(mouseX, mouseY, partialTicks);

        drawFooter(fade);
    }

    private static String modVersion() {
        net.minecraftforge.fml.common.ModContainer c = net.minecraftforge.fml.common.Loader.instance().getIndexedModList().get("arsenic");
        return c != null ? c.getVersion() : "?";
    }

    private void drawFooter(float fade) {
        int a = (int) (fade * 255);
        int h = mc.fontRendererObj.FONT_HEIGHT;
        String user = "Logged in as " + mc.getSession().getUsername();
        mc.fontRendererObj.drawStringWithShadow(user, 6, this.height - h * 2 - 8, ink(a * 160 / 255));
        String mods = "Mods loaded: " + net.minecraftforge.fml.common.Loader.instance().getModList().size();
        mc.fontRendererObj.drawStringWithShadow(mods, 6, this.height - h - 6, ink(a * 110 / 255));

        int modules = Arsenic.getArsenic().getModuleManager().getModules().size();
        long settings = Arsenic.getArsenic().getModuleManager().getModules().stream().mapToLong(m -> m.getProperties().size()).sum();
        int commands = Arsenic.getArsenic().getCommandManager().getCommandCount();
        String[] stats = {modules + " modules", settings + " settings", commands + " commands"};
        for (int i = 0; i < stats.length; i++)
            mc.fontRendererObj.drawStringWithShadow(stats[i], this.width - mc.fontRendererObj.getStringWidth(stats[i]) - 6,
                    6 + h * i, ink(a * 120 / 255));
    }


    private void drawGlow(float cx, float cy, float radius, int color) {
        int r = color >> 16 & 0xFF, g = color >> 8 & 0xFF, b = color & 0xFF, a = color >>> 24;
        if (a <= 0) return;
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(7425);
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(6, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(cx, cy, 0).color(r, g, b, a).endVertex();
        int seg = 40;
        for (int i = 0; i <= seg; i++) {
            double ang = i * Math.PI * 2 / seg;
            wr.pos(cx + Math.cos(ang) * radius, cy + Math.sin(ang) * radius, 0).color(r, g, b, 0).endVertex();
        }
        tess.draw();
        GlStateManager.shadeModel(7424);
        GlStateManager.enableTexture2D();
    }

    private void drawHorizontalGradient(int l, int t, int r, int b, int left, int right) {
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.shadeModel(7425);
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(7, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(l, t, 0).color(left >> 16 & 0xFF, left >> 8 & 0xFF, left & 0xFF, left >>> 24).endVertex();
        wr.pos(l, b, 0).color(left >> 16 & 0xFF, left >> 8 & 0xFF, left & 0xFF, left >>> 24).endVertex();
        wr.pos(r, b, 0).color(right >> 16 & 0xFF, right >> 8 & 0xFF, right & 0xFF, right >>> 24).endVertex();
        wr.pos(r, t, 0).color(right >> 16 & 0xFF, right >> 8 & 0xFF, right & 0xFF, right >>> 24).endVertex();
        tess.draw();
        GlStateManager.shadeModel(7424);
        GlStateManager.enableTexture2D();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        boolean overPanel = MathUtils.inside(mouseX, mouseY, panelX, panelY, panelX + panelW, panelY + panelH);
        boolean overButton = false;
        for (GuiButton b : this.buttonList)
            if (MathUtils.insideSized(mouseX, mouseY, b.xPosition, b.yPosition, b.width, b.height))
                overButton = true;
        if (mouseButton == 0 && !overPanel && !overButton)
            MenuTheme.click(mouseX, mouseY);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case 0:
                this.mc.displayGuiScreen(new GuiOptions(this, this.mc.gameSettings));
                break;
            case 1:
                this.mc.displayGuiScreen(new GuiSelectWorld(this));
                break;
            case 2:
                this.mc.displayGuiScreen(new GuiMultiplayer(this));
                break;
            case 3:
                this.mc.displayGuiScreen(new net.minecraftforge.fml.client.GuiModList(this));
                break;
            case 4:
                this.mc.shutdown();
                break;
            case 5:
                CustomMenu.showVanillaNext();
                this.mc.displayGuiScreen(new GuiMainMenu());
                break;
        }
    }

    public static class CustomGuiButton extends GuiButton {

        private final MSTimer opened;
        private final int index;
        private final HoverAnimation hoverAnim = new HoverAnimation();

        public CustomGuiButton(int buttonId, int x, int y, int width, int height, String buttonText, MSTimer opened, int index) {
            super(buttonId, x, y, width, height, buttonText);
            this.opened = opened;
            this.index = index;
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY) {
            this.hovered = MathUtils.insideSized(mouseX, mouseY, this.xPosition, this.yPosition, this.width, this.height);
            float hover = hoverAnim.update(hovered);

            float in = TickMode.CUBIC.clamped((opened.getTime() - 350 - index * 70L) / 450f);
            if (in <= 0f) return;
            float dy = (1f - in) * 8f;
            float y0 = yPosition + dy;

            int fill = ColorUtils.mixArgb(ink((int) (20 * in)), accent((int) (80 * in)), hover);
            MenuTheme.drawPill(xPosition, y0, xPosition + width, y0 + height, height / 2f, 1f, fill,
                    ColorUtils.mixArgb(ink((int) (55 * in)), accent((int) (235 * in)), hover));
            FontRendererExtension<?> fr = VanillaFontRenderer.of(mc.fontRendererObj).getFontRendererExtension();
            int tc = ColorUtils.mixArgb(ink((int) (205 * in)), ink((int) (255 * in)), hover);
            fr.drawStringWithShadow(displayString, xPosition + width / 2f + 2 * hover, y0 + height / 2f, tc, fr.CENTREX, fr.CENTREY);
        }
    }
}
