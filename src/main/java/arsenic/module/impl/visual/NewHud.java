package arsenic.module.impl.visual;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.injection.accessor.IMixinMinecraft;
import arsenic.injection.accessor.IMixinTimer;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.render.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

@ModuleInfo(name = "NewHUD", category = ModuleCategory.CLIENT, keybind = Keyboard.KEY_U)
public class NewHud extends Module {

    public final EnumProperty<hMode> colorMode = new EnumProperty<>("Color Mode: ", hMode.RAINBOW);
    public final BooleanProperty watermark = new BooleanProperty("watermark", true);
    public final DoubleProperty opacity = new DoubleProperty("opacity", new DoubleValue(0, 255, 100, 1));
    public final BooleanProperty backbar = new BooleanProperty("backbar", true);
    public final BooleanProperty frontbar = new BooleanProperty("frontbar", true);
    public final BooleanProperty info = new BooleanProperty("info", true);

    @EventLink
    public final Listener<EventRender2D> onRender2D = event -> {
        if(mc.currentScreen != null && !(mc.currentScreen instanceof GuiChat))
            return;

        ScaledResolution sr = new ScaledResolution(mc);
        FontRendererExtension<?> fr = Arsenic.getArsenic().getClickGuiScreen(). getFontRenderer();

        if(fr == null)
            return;

        int noDelayColor = colorMode.getValue().getColor(4, 0);
        if(watermark.getValue())
            fr.drawStringWithShadow("A" + EnumChatFormatting.WHITE + "rsenic", 4, 4, noDelayColor);

        if (info.getValue()) {
            //this should actually be at the bottom also the tps is broken
            double y = sr.getScaledHeight();
            double tps = ((IMixinTimer) ((IMixinMinecraft) mc).getTimer()).getTicksPerSecond();
            fr.drawStringWithShadow("FPS " + EnumChatFormatting.WHITE + Minecraft.getDebugFPS(), 1, (float) y - fr.getHeight("FPS") * 3 , noDelayColor);
            fr.drawStringWithShadow("TPS " + EnumChatFormatting.WHITE + tps, 1, (float) y - fr.getHeight("TPS") * 2, noDelayColor);
            fr.drawStringWithShadow("XYZ " + EnumChatFormatting.WHITE + mc.thePlayer.getPosition().getX() + " " + EnumChatFormatting.WHITE + mc.thePlayer.getPosition().getY() + " " + EnumChatFormatting.WHITE + mc.thePlayer.getPosition().getZ(), 1,(float) y - fr.getHeight("XYZ") * 1, noDelayColor);
        }

        float x = sr.getScaledWidth();

        //sorts it in order of length
        List<ModuleRenderInfo> nameList =
                Arsenic.getArsenic().getModuleManager().getEnabledModules()
                        .stream().map(module -> new ModuleRenderInfo(fr.getWidth(module.getName()), module.getName()))
                        .sorted(Comparator.comparingDouble(ri -> -ri.length)).collect(Collectors.toList());

        int i = 0;
        for (ModuleRenderInfo m : nameList) {
            float mX = x - m.length;
            RenderUtils.resetColorText();
            int color = colorMode.getValue().getColor(4, i * 20);
            Gui.drawRect((int) x, i, (int) mX - 6, 10 + i, new Color(0, 0, 0, (int)opacity.getValue().getInput()).getRGB());
            fr.drawStringWithShadow(m.name, mX - 3, i + 1, color);

            if(backbar.getValue())
                Gui.drawRect((int)x, i, (int)x - 1, 10 + i, color);

            if(frontbar.getValue())
                Gui.drawRect((int) mX - 6, i, (int) mX - 7, 10 + i, color);

            i += 10;
        }
    };

    private static class ModuleRenderInfo {

        public final float length;
        public final String name;

        public ModuleRenderInfo(float length, String name) {
            this.length = length;
            this.name = name;
        }
    }

    public enum hMode {
        THEME(ColorUtils::getThemeRainbowColor),
        RAINBOW(ColorUtils::getRainbow);

        private final BinaryOperator<Integer> f;

        hMode(BinaryOperator<Integer> f) {
            this.f = f;
        }

        public int getColor(int speed, int delay) {
            return f.apply(speed, delay);
        }
    }
}