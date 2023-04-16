package arsenic.module.impl.visual;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.render.BlurUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderUtils;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@ModuleInfo(name = "HUD", category = ModuleCategory.CLIENT, keybind = Keyboard.KEY_Y)
public class HUD extends Module {

    protected final Color color = new Color(0xFF2ECC71);

    @EventLink
    public final Listener<EventRender2D> onRender2D = event -> {
        if(mc.currentScreen != null)
            return;
        ScaledResolution sr = new ScaledResolution(mc);
        FontRendererExtension<?> fr = Arsenic.getArsenic().getClickGuiScreen().getFontRenderer();
        float yOffSet = 0;
        float yOffSetAmount = fr.getHeight("T");
        float x = sr.getScaledWidth() - (yOffSetAmount/2f);
        //sorts it in order of length
        List<ModuleRenderInfo> nameList =
                Arsenic.getArsenic().getModuleManager().getEnabledModules()
                        .stream().map(module -> new ModuleRenderInfo(fr.getWidth(module.getName()), module.getName()))
                        .sorted(Comparator.comparingDouble(ri -> -ri.length)).collect(Collectors.toList());
        for (ModuleRenderInfo m : nameList) {
            float mX = x - m.length;
            float y2 = yOffSet + yOffSetAmount;
            RenderUtils.resetColorText();
            DrawUtils.drawRect(mX, yOffSet, x, y2, 0xAA303030);
            fr.drawString(m.name, mX, yOffSet, color.getRGB());
            yOffSet = y2;
        }
        DrawUtils.drawRect(x, 0, mc.displayWidth/3f, yOffSet, color.darker().getRGB());
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
