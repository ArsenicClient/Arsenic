package arsenic.module.impl.visual;

import java.util.concurrent.atomic.AtomicInteger;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventShader;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.EnumProperty;
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

@ModuleInfo(name = "HUD", category = ModuleCategory.SETTINGS, hidden = true)
public class HUD extends Module {

    public final EnumProperty<hMode> colorMode = new EnumProperty<>("Color Mode: ", hMode.RAINBOW);
    public final double opacity = 75;

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

        float x = sr.getScaledWidth();

        //sorts it in order of length
        nameList = Arsenic.getArsenic().getModuleManager().getEnabledModules()
                        .stream().filter(module -> !module.isHidden())
                        .map(module -> new ModuleRenderInfo(fr.getWidth(module.getName()), module.getName()))
                        .sorted(Comparator.comparingDouble(ri -> -ri.length)).collect(Collectors.toList());

        AtomicInteger i = new AtomicInteger();
        nameList.forEach(m -> {
            float mX = x - m.length;
            RenderUtils.resetColorText();
            int color = colorMode.getValue().getColor(4, i.get() * 20);
            Gui.drawRect((int) x, i.get(), (int) mX - 6, 10 + i.get(), new Color(0, 0, 0, (int)opacity).getRGB());
            fr.drawStringWithShadow(m.name, mX - 3, i.get() + 2, color);

            Gui.drawRect((int)x, i.get(), (int)x - 1, 10 + i.get(), color);

            i.addAndGet(10);
        });
        fr.drawString("A" + EnumChatFormatting.WHITE + "rsenic", 4, 4, noDelayColor);

    };

    @EventLink
    public final Listener<EventShader.Bloom> bloomListener = event -> {
        if (mc.currentScreen != null && !(mc.currentScreen instanceof GuiChat)) {
            return;
        }
        ScaledResolution sr = new ScaledResolution(mc);
        FontRendererExtension<?> fr = Arsenic.getArsenic().getClickGuiScreen().getFontRenderer();

        if(fr == null || nameList == null)
            return;

        float x = sr.getScaledWidth();
        AtomicInteger i = new AtomicInteger();
        nameList.forEach(m -> {
            float mX = x - m.length;
            RenderUtils.resetColorText();
            int color = colorMode.getValue().getColor(4, i.get() * 20);

            Gui.drawRect((int) x, i.get(), (int) mX - 6, 10 + i.get(),RenderUtils.alpha(new Color(color),255));
            fr.drawStringWithShadow(m.name, mX - 3, i.get() + 2, RenderUtils.alpha(new Color(color),255));
            Gui.drawRect((int)x, i.get(), (int)x - 1, 10 + i.get(), RenderUtils.alpha(new Color(color),255));

            i.addAndGet(10);
        });
    };


    @EventLink
    public final Listener<EventShader.Blur> blurListener = event -> {
        if (mc.currentScreen != null && !(mc.currentScreen instanceof GuiChat)) {
            return;
        }
        ScaledResolution sr = new ScaledResolution(mc);
        FontRendererExtension<?> fr = Arsenic.getArsenic().getClickGuiScreen().getFontRenderer();

        if(fr == null || nameList == null) return;

        float x = sr.getScaledWidth();
        AtomicInteger i = new AtomicInteger();
        nameList.forEach(m -> {
            float mX = x - m.length;
            RenderUtils.resetColorText();
            Gui.drawRect((int) x, i.get(), (int) mX - 6, 10 + i.get(),-1);
            i.addAndGet(10);
        });
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