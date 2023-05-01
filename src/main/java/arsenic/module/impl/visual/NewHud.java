package arsenic.module.impl.visual;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.render.RenderUtils;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@ModuleInfo(name = "NewHUD", category = ModuleCategory.CLIENT, keybind = Keyboard.KEY_U)
public class NewHud extends Module {
    public BooleanProperty watermark = new BooleanProperty("watermark", true);
    public DoubleProperty opacity = new DoubleProperty("opacity", new DoubleValue(0, 255, 100, 1));
    public BooleanProperty backbar = new BooleanProperty("backbar", true);
    public BooleanProperty frontbar = new BooleanProperty("frontbar", true);

    @EventLink
    public final Listener<EventRender2D> onRender2D = event -> {
        if(mc.currentScreen != null && !(mc.currentScreen instanceof GuiChat))
            return;

        ScaledResolution sr = new ScaledResolution(mc);
        FontRendererExtension<?> fr = Arsenic.getArsenic().getClickGuiScreen(). getFontRenderer();

        if(fr == null)
            return;

        if(watermark.getValue())
            fr.drawStringWithShadow("A" + EnumChatFormatting.WHITE + "rsenic", 4, 4, getRainbow(4, 20L));

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

            Gui.drawRect((int) x, i, (int) mX - 6, 10 + i, new Color(0, 0, 0, (int)opacity.getValue().getInput()).getRGB());
            fr.drawStringWithShadow(m.name, mX - 3, i + 1, getRainbow(4, i * 20L));

            if(backbar.getValue())
                Gui.drawRect((int)x, i, (int)x - 1, 10 + i, getRainbow(4, i * 20L));

            if(frontbar.getValue())
                Gui.drawRect((int) mX - 6, i, (int) mX - 7, 10 + i, getRainbow(4, i * 20L));

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

    public static int getRainbow(float seconds, long index) {
        return Color.HSBtoRGB(((System.currentTimeMillis() + index) % (int)(seconds * 1000)) / (seconds * 1000), 0.6f, 0.86f);
    }
}