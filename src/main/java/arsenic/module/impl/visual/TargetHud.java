package arsenic.module.impl.visual;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.blatant.KillAura;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.render.DrawUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;

import java.awt.*;

@ModuleInfo(name = "TargetHud", category = ModuleCategory.SETTINGS)
public class TargetHud extends Module {

    public final DoubleProperty opacity = new DoubleProperty("opacity", new DoubleValue(0, 255, 100, 1));
    public final DoubleProperty x = new DoubleProperty("X", new DoubleValue(0, 1000, 1, 1));
    public final DoubleProperty y = new DoubleProperty("Y", new DoubleValue(0, 1000, 30, 1));

    @EventLink
    public final Listener<EventRender2D> eventRender2DListener = event -> {
        FontRendererExtension<?> fr = Arsenic.getArsenic().getClickGuiScreen().getFontRenderer();

        if (fr == null)
            return;
        EntityPlayer target = Arsenic.getArsenic().getModuleManager().getModuleByClass(KillAura.class).target;

        if (target == null) {
            return;
        }
        // This code is absolutely beautiful and so easy to read
        // my balls
        DrawUtils.drawRect((float) x.getValue().getInput(), (float) y.getValue().getInput(), fr.getWidth("Name:" + target.getName()) + 6 + (float) x.getValue().getInput(), (float) y.getValue().getInput() + 21, new Color(0, 0, 0, (int) opacity.getValue().getInput()).getRGB());
        DrawUtils.drawRect((float) x.getValue().getInput(), (float) y.getValue().getInput(), fr.getWidth("Name:" + target.getName()) + 6 + (float) x.getValue().getInput(), (float) y.getValue().getInput() + 1, ColorUtils.getThemeRainbowColor(4, 0));
        fr.drawStringWithShadow("Name: " + EnumChatFormatting.WHITE + target.getName(), (float) x.getValue().getInput() + 1, (float) y.getValue().getInput() + 2, ColorUtils.getThemeRainbowColor(4, 0));
        fr.drawStringWithShadow("HP: " + EnumChatFormatting.WHITE + Math.floor(target.getHealth()), (float) x.getValue().getInput() + 1, (float) y.getValue().getInput() + 2 + fr.getHeight("N"), ColorUtils.getThemeRainbowColor(4, 0));
    };
}
