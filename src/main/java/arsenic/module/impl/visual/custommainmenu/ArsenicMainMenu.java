package arsenic.module.impl.visual.custommainmenu;

import arsenic.gui.click.GuiStyle;
import arsenic.main.Arsenic;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.java.MathUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.QuadBatch;
import arsenic.utils.render.RenderContext;
import arsenic.utils.timer.FrameClock;
import arsenic.utils.timer.HoverAnimation;
import arsenic.utils.timer.MSTimer;
import arsenic.utils.timer.TickMode;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The client's title screen: the logo over the shared ocean backdrop, with a panel of pill buttons.
 * Clicking the backdrop away from the buttons pokes the scene (scatters fish, pops bubbles).
 */
public class ArsenicMainMenu extends Screen {

    private static final Identifier LOGO = Identifier.fromNamespaceAndPath("arsenic", "logos/modern.png");
    private static final float LOGO_ASPECT = 166f / 588f;
    private static final String MOD_MENU_SCREEN = "com.terraformersmc.modmenu.gui.ModsScreen";

    private final MSTimer opened = new MSTimer();
    private final FrameClock clock = new FrameClock();
    private final List<MenuButton> menuButtons = new ArrayList<>();

    private float smoothMX, smoothMY;
    private int panelX, panelY, panelW, panelH;

    public ArsenicMainMenu() {
        super(Component.literal("Arsenic"));
    }

    static boolean isLight() {
        return ColorUtils.luminance(MenuTheme.bg()) > 0.6f;
    }

    static int ink(int alpha) {
        return ColorUtils.withAlpha(isLight() ? 0x121217 : 0xFFFFFF, alpha);
    }

    static int accent(int alpha) {
        return ColorUtils.withAlpha(MenuTheme.main(), alpha);
    }

    @Override
    protected void init() {
        menuButtons.clear();
        List<Object[]> entries = new ArrayList<>();
        entries.add(new Object[]{Component.translatable("menu.singleplayer"), (Supplier<Screen>) () -> new SelectWorldScreen(this)});
        entries.add(new Object[]{Component.translatable("menu.multiplayer"), (Supplier<Screen>) () -> new JoinMultiplayerScreen(this)});
        // Fabric has no built-in mod list; offer Mod Menu's when it is installed
        if (FabricLoader.getInstance().isModLoaded("modmenu"))
            entries.add(new Object[]{Component.literal("Mods"), (Supplier<Screen>) this::modMenu});
        entries.add(new Object[]{Component.translatable("menu.options"), (Supplier<Screen>) () -> new OptionsScreen(this, minecraft.options)});
        entries.add(new Object[]{Component.translatable("menu.quit"), null});

        int bw = 200, bh = 22, gap = 6, pad = 14;
        int count = entries.size();
        panelW = bw + pad * 2;
        panelH = count * bh + (count - 1) * gap + pad * 2;
        panelX = this.width / 2 - panelW / 2;
        panelY = (int) (this.height * 0.47f);
        int x = panelX + pad, y = panelY + pad;
        for (int i = 0; i < count; i++) {
            Object[] e = entries.get(i);
            @SuppressWarnings("unchecked")
            Supplier<Screen> target = (Supplier<Screen>) e[1];
            Runnable action = target == null ? minecraft::stop : () -> minecraft.gui.setScreen(target.get());
            menuButtons.add(addRenderableWidget(new MenuButton(x, y + (bh + gap) * i, bw, bh, (Component) e[0], action, opened, i)));
        }
        addRenderableWidget(new MenuButton(this.width - 108, this.height - 28, 100, 20, Component.literal("Vanilla Menu"), () -> {
            CustomMenu.showVanillaNext();
            minecraft.gui.setScreen(new TitleScreen());
        }, opened, count));
    }

    private Screen modMenu() {
        try {
            return (Screen) Class.forName(MOD_MENU_SCREEN).getConstructor(Screen.class).newInstance(this);
        } catch (ReflectiveOperationException | LinkageError e) {
            return this;
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        long elapsed = opened.getTime();
        float fade = TickMode.CUBIC.clamped(elapsed / 700f);
        try (RenderContext ignored = RenderContext.begin(graphics)) {
            // the shared ocean backdrop
            MenuTheme.drawBackground(this.width, this.height, mouseX, mouseY, fade);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        float dt = clock.tick();
        long elapsed = opened.getTime();
        float time = elapsed / 1000f;
        float fade = TickMode.CUBIC.clamped(elapsed / 700f);
        smoothMX = FrameClock.approach(smoothMX, mouseX / (float) this.width - 0.5f, 4f, dt);
        smoothMY = FrameClock.approach(smoothMY, mouseY / (float) this.height - 0.5f, 4f, dt);

        try (RenderContext ignored = RenderContext.begin(graphics)) {
            int main = MenuTheme.main();
            boolean light = isLight();

            // logo with a soft pulsing halo
            float lw = Math.min(this.width * 0.55f, 230f), lh = lw * LOGO_ASPECT;
            float lx = this.width / 2f - lw / 2f + smoothMX * 8f;
            float ly = this.height * 0.27f - lh / 2f + smoothMY * 8f - (1f - fade) * 10f;
            float pulse = 0.75f + 0.25f * (float) Math.sin(time * 1.6f);
            drawGlow(lx + lw / 2f, ly + lh / 2f, lw * 0.95f, accent((int) (70 * fade * pulse)));
            DrawUtils.drawTexture(LOGO, lx, ly, lw, lh, ColorUtils.withAlpha(ink(255), fade));

            // Element 33: arsenic's own periodic-table tile beside the logo, pulsing softly
            if (GuiStyle.element()) {
                float tileSize = 50f, tx = lx + lw + 18f, ty = ly + lh / 2f - tileSize / 2f;
                if (tx + tileSize < this.width - 4)
                    ElementScene.drawElementTile(tx, ty, tileSize, main, 0.6f + 0.4f * (float) Math.sin(time * 1.4f), fade);
            }

            int a = (int) (255 * fade);
            int ulHalf = (int) (50 * TickMode.CUBIC.clamped((elapsed - 150) / 700f));
            int ulY = (int) (ly + lh + 12);
            int end = (ColorUtils.mixRgb(main, light ? 0x000000 : 0xFFFFFF, 0.35f) & 0xFFFFFF) | a << 24;
            DrawUtils.drawHorizontalGradientRect(this.width / 2f - ulHalf, ulY, this.width / 2f + ulHalf, ulY + 2, accent(a), end);

            FontRendererExtension<?> fr = Arsenic.getArsenic().getFonts().Minecraft.getFontRendererExtension();
            String tag = "Arsenic Client  -  v" + modVersion();
            fr.drawStringWithShadow(tag, this.width / 2f, ulY + 12, ink((int) (140 * fade)), fr.CENTREX, fr.CENTREY);

            float slide = (1f - TickMode.CUBIC.clamped((elapsed - 250) / 700f)) * 14f;
            int pa = (int) (fade * 255);
            MenuTheme.drawPill(panelX, panelY + slide, panelX + panelW, panelY + panelH + slide, 12f, 1f,
                    ((int) (120 * pa / 255f) << 24) | (ColorUtils.mixRgb(MenuTheme.bg(), 0x000000, 0.5f) & 0xFFFFFF), ink((int) (40 * pa / 255f)));
            DrawUtils.drawRect(panelX + 24, panelY + slide, panelX + panelW - 24, panelY + 1.5f + slide, accent((int) (120 * pa / 255f)));
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);

        drawFooter(graphics, fade);
    }

    private static String modVersion() {
        return FabricLoader.getInstance().getModContainer("arsenic")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
    }

    private void drawFooter(GuiGraphicsExtractor graphics, float fade) {
        int a = (int) (fade * 255);
        int h = font.lineHeight;
        graphics.text(font, "Logged in as " + minecraft.getUser().getName(), 6, this.height - h * 2 - 8, ink(a * 160 / 255), true);
        graphics.text(font, "Mods loaded: " + FabricLoader.getInstance().getAllMods().size(), 6, this.height - h - 6, ink(a * 110 / 255), true);

        var modules = Arsenic.getArsenic().getModuleManager().getModules();
        long settings = modules.stream().mapToLong(m -> m.getProperties().size()).sum();
        int commands = Arsenic.getArsenic().getCommandManager().getCommandCount();
        String[] stats = {modules.size() + " modules", settings + " settings", commands + " commands"};
        for (int i = 0; i < stats.length; i++)
            graphics.text(font, stats[i], this.width - font.width(stats[i]) - 6, 6 + h * i, ink(a * 120 / 255), true);
    }

    private static void drawGlow(float cx, float cy, float radius, int color) {
        if (color >>> 24 == 0) return;
        int edge = color & 0xFFFFFF;
        QuadBatch batch = new QuadBatch();
        int seg = 40;
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
            batch.triangle(cx, cy, color, cx + (float) Math.cos(a0) * radius, cy + (float) Math.sin(a0) * radius, edge,
                    cx + (float) Math.cos(a1) * radius, cy + (float) Math.sin(a1) * radius, edge);
        }
        batch.submit();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick))
            return true;
        boolean overPanel = MathUtils.inside((int) event.x(), (int) event.y(), panelX, panelY, panelX + panelW, panelY + panelH);
        if (event.button() == 0 && !overPanel)
            MenuTheme.click((float) event.x(), (float) event.y());
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    private static final class MenuButton extends AbstractButton {

        private final Runnable action;
        private final MSTimer opened;
        private final int index;
        private final HoverAnimation hoverAnim = new HoverAnimation();

        MenuButton(int x, int y, int width, int height, Component text, Runnable action, MSTimer opened, int index) {
            super(x, y, width, height, text);
            this.action = action;
            this.opened = opened;
            this.index = index;
        }

        @Override
        public void onPress(InputWithModifiers input) {
            action.run();
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
            float hover = hoverAnim.update(isHovered());
            float in = TickMode.CUBIC.clamped((opened.getTime() - 350 - index * 70L) / 450f);
            if (in <= 0f) return;
            float y0 = getY() + (1f - in) * 8f;
            try (RenderContext ignored = RenderContext.begin(graphics)) {
                int fill = ColorUtils.mixArgb(ink((int) (20 * in)), accent((int) (80 * in)), hover);
                MenuTheme.drawPill(getX(), y0, getX() + width, y0 + height, height / 2f, 1f, fill,
                        ColorUtils.mixArgb(ink((int) (55 * in)), accent((int) (235 * in)), hover));
                FontRendererExtension<?> fr = Arsenic.getArsenic().getFonts().Minecraft.getFontRendererExtension();
                int tc = ColorUtils.mixArgb(ink((int) (205 * in)), ink((int) (255 * in)), hover);
                fr.drawStringWithShadow(getMessage().getString(), getX() + width / 2f + 2 * hover, y0 + height / 2f, tc, fr.CENTREX, fr.CENTREY);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
