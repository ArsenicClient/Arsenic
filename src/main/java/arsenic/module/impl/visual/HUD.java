package arsenic.module.impl.visual;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.render.RenderUtils;
import net.minecraft.client.gui.ScaledResolution;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@ModuleInfo(name = "HUD", category = ModuleCategory.CLIENT)
public class HUD extends Module {

    public DoubleProperty test = new DoubleProperty("strength", new DoubleValue(1, 100, 5, 1));

    @EventLink
    public final Listener<EventRender2D> onRender2D = event -> {
        if(mc.currentScreen != null)
            return;
        ScaledResolution sr = new ScaledResolution(mc);
        FontRendererExtension<?> fr = Arsenic.getArsenic().getClickGuiScreen(). getFontRenderer();
        if(fr == null)
            return;
        float yOffSet = 0;
        float yOffSetAmount = fr.getHeight("T");
        float x = sr.getScaledWidth();
        //sorts it in order of length
        List<ModuleRenderInfo> nameList =
                Arsenic.getArsenic().getModuleManager().getEnabledModules()
                        .stream().map(module -> new ModuleRenderInfo(fr.getWidth(module.getName()), module.getName()))
                        .sorted(Comparator.comparingDouble(ri -> -ri.length)).collect(Collectors.toList());
        for (ModuleRenderInfo m : nameList) {
            float mX = x - m.length;
            float y2 = yOffSet + yOffSetAmount;
            RenderUtils.resetColorText();
            fr.drawStringWithShadow(m.name, mX, yOffSet, ColorUtils.getThemeRainbowColor((long) (test.getValue().getInput() * yOffSet), 3000L));
            yOffSet = y2;
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
}
