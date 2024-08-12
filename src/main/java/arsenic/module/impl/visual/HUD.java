package arsenic.module.impl.visual;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventShader;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.PropertyInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.render.RenderUtils;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.EnumChatFormatting;
import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

@ModuleInfo(name = "HUD", category = ModuleCategory.SETTINGS)
public class HUD extends Module {

    public final EnumProperty<hMode> colorMode = new EnumProperty<>("Color Mode: ", hMode.RAINBOW);
    @PropertyInfo(reliesOn = "Bloom",value = "true")
    public final EnumProperty<shaderMode> shadermodes = new EnumProperty<>("Shader Mode: ", shaderMode.Shadow);
    @PropertyInfo(reliesOn = "background",value = "true")
    public final DoubleProperty opacity = new DoubleProperty("opacity", new DoubleValue(0, 255, 100, 1));
    public final BooleanProperty bloom = new BooleanProperty("Bloom", true);
    public final BooleanProperty blur = new BooleanProperty("Blur", true);
    public final BooleanProperty watermark = new BooleanProperty("watermark", true);
    public final BooleanProperty backbar = new BooleanProperty("backbar", true);
    public final BooleanProperty frontbar = new BooleanProperty("frontbar", true);
    public final BooleanProperty background = new BooleanProperty("background", true);

    List<ModuleRenderInfo> nameList;

    @EventLink
    public final Listener<EventRender2D> onRender2D = event -> {
        if(mc.currentScreen != null && !(mc.currentScreen instanceof GuiChat))
            return;

        ScaledResolution sr = new ScaledResolution(mc);
        FontRendererExtension<?> fr = Arsenic.getArsenic().getClickGuiScreen(). getFontRenderer();

        if(fr == null)
            return;

        int noDelayColor = colorMode.getValue().getColor(4, 0);
        if(watermark.getValue()) fr.drawString("A" + EnumChatFormatting.WHITE + "rsenic Auth:" + Arsenic.getArsenic().getAuth().isAuthorised(), 4, 4, noDelayColor);

        float x = sr.getScaledWidth();

        //sorts it in order of length
        nameList = Arsenic.getArsenic().getModuleManager().getEnabledModules()
                        .stream().filter(module -> !isHidden())
                        .map(module -> new ModuleRenderInfo(fr.getWidth(module.getName()), module.getName()))
                        .sorted(Comparator.comparingDouble(ri -> -ri.length)).collect(Collectors.toList());

        int i = 0;
        for (ModuleRenderInfo m : nameList) {
            float mX = x - m.length;
            RenderUtils.resetColorText();
            int color = colorMode.getValue().getColor(4, i * 20);
            if (background.getValue()) Gui.drawRect((int) x, i, (int) mX - 6, 10 + i, new Color(0, 0, 0, (int)opacity.getValue().getInput()).getRGB());
            fr.drawStringWithShadow(m.name, mX - 3, i + 2, color);

            if(backbar.getValue()) Gui.drawRect((int)x, i, (int)x - 1, 10 + i, color);
            if(frontbar.getValue()) Gui.drawRect((int) mX - 6, i, (int) mX - 7, 10 + i, color);

            i += 10;
        }
    };

    @EventLink
    public final Listener<EventShader.Bloom> bloomListener = event -> {
        if (!bloom.getValue()) return;
        if(mc.currentScreen != null && !(mc.currentScreen instanceof GuiChat)) {
            return;
        }
        ScaledResolution sr = new ScaledResolution(mc);
        FontRendererExtension<?> fr = Arsenic.getArsenic().getClickGuiScreen(). getFontRenderer();

        if(fr == null)
            return;

        float x = sr.getScaledWidth();
        if (nameList == null) return;
        int i = 0;
        for (ModuleRenderInfo m : nameList) {
            float mX = x - m.length;
            RenderUtils.resetColorText();
            int color;
            if (shadermodes.getValue().equals(shaderMode.Bloom)) {
                color = colorMode.getValue().getColor(4, i * 20);
            } else {
                color = new Color(0,0,0,255).getRGB();
            }
            if (background.getValue()) Gui.drawRect((int) x, i, (int) mX - 6, 10 + i,RenderUtils.alpha(new Color(color),255));
            fr.drawStringWithShadow(m.name, mX - 3, i + 2, RenderUtils.alpha(new Color(color),255));
            if(backbar.getValue()) Gui.drawRect((int)x, i, (int)x - 1, 10 + i, RenderUtils.alpha(new Color(color),255));
            if(frontbar.getValue()) Gui.drawRect((int) mX - 6, i, (int) mX - 7, 10 + i, RenderUtils.alpha(new Color(color),255));

            i += 10;
        }
    };


    @EventLink
    public final Listener<EventShader.Blur> blurListener = event -> {
        if (!blur.getValue() || !background.getValue()) return;
        if(mc.currentScreen != null && !(mc.currentScreen instanceof GuiChat)) {
            return;
        }
        ScaledResolution sr = new ScaledResolution(mc);
        FontRendererExtension<?> fr = Arsenic.getArsenic().getClickGuiScreen(). getFontRenderer();

        if(fr == null) return;

        float x = sr.getScaledWidth();
        if (nameList == null) return;
        int i = 0;
        for (ModuleRenderInfo m : nameList) {
            float mX = x - m.length;
            RenderUtils.resetColorText();
            Gui.drawRect((int) x, i, (int) mX - 6, 10 + i,-1);
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

    public enum shaderMode {
        Bloom,
        Shadow
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