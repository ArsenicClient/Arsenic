package arsenic.module.impl.visual.custommainmenu;

import arsenic.gui.themes.ThemeManager;
import arsenic.main.Arsenic;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

import java.awt.Color;

/**
 * The client's title screen.
 * <p>
 * On 1.8 the background cycled through a set of fullscreen GLSL shaders. Those don't run on the
 * 26.x renderer, so the background is vanilla's panorama under a slowly shifting theme gradient.
 */
public class ArsenicMainMenu extends Screen {

    public ArsenicMainMenu() {
        super(Component.literal("Arsenic"));
    }

    @Override
    protected void init() {
        int top = this.height / 2;
        int step = 24;
        int x = this.width / 2 - 100;
        addRenderableWidget(new MenuButton(x, top, Component.translatable("menu.singleplayer"),
                () -> minecraft.gui.setScreen(new SelectWorldScreen(this))));
        addRenderableWidget(new MenuButton(x, top + step, Component.translatable("menu.multiplayer"),
                () -> minecraft.gui.setScreen(new JoinMultiplayerScreen(this))));
        addRenderableWidget(new MenuButton(x, top + step * 2, Component.translatable("menu.options"),
                () -> minecraft.gui.setScreen(new OptionsScreen(this, minecraft.options))));
        addRenderableWidget(new MenuButton(x, top + step * 3, Component.translatable("menu.quit"),
                () -> minecraft.stop()));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        extractPanorama(graphics, partialTicks);
        try (RenderContext ignored = RenderContext.begin(graphics)) {
            // the theme gradient drifts between the two theme colours over a few seconds
            float t = (float) (Math.sin(System.currentTimeMillis() / 2500.0) * 0.5 + 0.5);
            int main = ThemeManager.getMainColor(), second = ThemeManager.getGradientColor();
            int top = ColorUtils.setColor(t > 0.5f ? main : second, 0, 90);
            int bottom = ColorUtils.setColor(0x000000, 0, 170);
            DrawUtils.drawGradientRect(0, 0, width, height, top, bottom);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
        try (RenderContext ignored = RenderContext.begin(graphics)) {
            FontRendererExtension<?> fontRenderer = Arsenic.getArsenic().getFonts().Minecraft.getFontRendererExtension();
            fontRenderer.setScale(7f);
            fontRenderer.drawStringWithShadow("Arsenic", this.width / 2f, this.height / 3f, -1, fontRenderer.CENTREY, fontRenderer.CENTREX);
            fontRenderer.resetScale();
        }

        String moduleCount = "Modules: " + Arsenic.getArsenic().getModuleManager().getModules().size();
        long settingCount = Arsenic.getArsenic().getModuleManager().getModules().stream().mapToLong(module -> module.getProperties().size()).sum();
        String settingCountStr = "Settings: " + settingCount;
        String commandCount = "Commands: " + Arsenic.getArsenic().getCommandManager().getCommandCount();

        graphics.text(font, moduleCount, this.width - font.width(moduleCount) - 2, 2, -1, true);
        graphics.text(font, settingCountStr, this.width - font.width(settingCountStr) - 2, 2 + font.lineHeight, -1, true);
        graphics.text(font, commandCount, this.width - font.width(commandCount) - 2, 2 + font.lineHeight * 2, -1, true);

        String modCount = "Mods loaded: " + FabricLoader.getInstance().getAllMods().size();
        graphics.text(font, modCount, 2, this.height - font.lineHeight - 2, -1, true);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    private static final class MenuButton extends AbstractButton {

        private final Runnable action;

        MenuButton(int x, int y, Component text, Runnable action) {
            super(x, y, 200, 20, text);
            this.action = action;
        }

        @Override
        public void onPress(InputWithModifiers input) {
            action.run();
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
            try (RenderContext ignored = RenderContext.begin(graphics)) {
                int fill = new Color(255, 255, 255, isHoveredOrFocused() ? 70 : 40).getRGB();
                DrawUtils.drawRoundedRect(getX(), getY(), getX() + width, getY() + height, height / 4.0f * 2f, fill);
                FontRendererExtension<?> fontRenderer = Arsenic.getArsenic().getFonts().Minecraft.getFontRendererExtension();
                fontRenderer.drawStringWithShadow(getMessage().getString(), getX() + width / 2f, getY() + height / 2f, 0xFFE0E0E0,
                        fontRenderer.CENTREX, fontRenderer.CENTREY);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
