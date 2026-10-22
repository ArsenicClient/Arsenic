package arsenic.injection.mixin;

import arsenic.gui.click.GuiStyle;
import arsenic.main.Arsenic;
import arsenic.module.impl.visual.custommainmenu.MenuTheme;
import arsenic.utils.render.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every vanilla screen's backdrop becomes the menu theme. Screens build their backdrop from a few
 * shared pieces (the panorama, the blur, the menu texture and the dark in-game veil), and many
 * override the method that combines them, so each piece is replaced on its own.
 */
@Mixin(Screen.class)
public abstract class MixinScreen {

    @Shadow protected Minecraft minecraft;
    @Shadow public int width;
    @Shadow public int height;

    private static boolean themed() {
        return Arsenic.getArsenic() != null && GuiStyle.customMenus();
    }

    private void drawThemedBackground(GuiGraphicsExtractor graphics) {
        int mouseX = (int) minecraft.mouseHandler.getScaledXPos(minecraft.getWindow());
        int mouseY = (int) minecraft.mouseHandler.getScaledYPos(minecraft.getWindow());
        try (RenderContext ignored = RenderContext.begin(graphics)) {
            MenuTheme.drawBackground(width, height, mouseX, mouseY, 1f);
        }
    }

    /** The title-screen panorama: the theme's scene is drawn by the menu background instead. */
    @Inject(method = "extractPanorama", at = @At("HEAD"), cancellable = true)
    private void arsenic$panorama(GuiGraphicsExtractor graphics, float a, CallbackInfo ci) {
        if (themed())
            ci.cancel();
    }

    /** The blur of the world behind menus: the theme draws its own veil. */
    @Inject(method = "extractBlurredBackground", at = @At("HEAD"), cancellable = true)
    private void arsenic$blur(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (themed())
            ci.cancel();
    }

    /** The menu texture: the full scene over the whole screen, a tinted pane over part of it. */
    @Inject(method = "extractMenuBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIII)V", at = @At("HEAD"), cancellable = true)
    private void arsenic$menuBackground(GuiGraphicsExtractor graphics, int x, int y, int w, int h, CallbackInfo ci) {
        if (!themed())
            return;
        ci.cancel();
        if (x <= 0 && y <= 0 && w >= width && h >= height) {
            drawThemedBackground(graphics);
        } else {
            try (RenderContext ignored = RenderContext.begin(graphics)) {
                MenuTheme.drawListPane(x, y, x + w, y + h);
            }
        }
    }

    /** The dark veil behind in-game screens (inventories, chat-less overlays). */
    @Inject(method = "extractTransparentBackground", at = @At("HEAD"), cancellable = true)
    private void arsenic$transparentBackground(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (!themed())
            return;
        ci.cancel();
        drawThemedBackground(graphics);
    }
}
