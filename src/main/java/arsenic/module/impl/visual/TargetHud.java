package arsenic.module.impl.visual;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.combat.Aura;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.render.DrawUtils;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumChatFormatting;

@ModuleInfo(name = "TargetHud", category = ModuleCategory.CLIENT)
public class TargetHud extends Module {

    @EventLink
    public final Listener<EventRender2D> onRender2D = event -> {
        FontRendererExtension<?> fr = Arsenic.getArsenic().getClickGuiScreen(). getFontRenderer();

        if(fr == null)
            return;
        Entity target = PlayerUtils.getClosestPlayerWithin(Arsenic.getArsenic().getModuleManager().getModuleByClass(Aura.class).range.getValue().getInput());
        if (target == null) {
            return;
        }

        DrawUtils.drawRect(12.5f, 25, 25, 1 , Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor());
        fr.drawStringWithShadow("HP: " + EnumChatFormatting.WHITE + target.getName(), 1, 30 , ColorUtils.getThemeRainbowColor(4,0));
    };
}
