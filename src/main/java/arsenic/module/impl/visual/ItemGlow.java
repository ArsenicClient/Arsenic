package arsenic.module.impl.visual;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.gui.design.M3;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.render.GlowRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;

import java.util.ArrayList;
import java.util.List;

@ModuleInfo(name = "ItemGlow", category = ModuleCategory.RENDER, hidden = true, tier = ModuleTier.VISUAL,
        description = "Glowing dropped items, using the same shader as the player glow ESP")
public class ItemGlow extends Module {

    public final DoubleProperty range = new DoubleProperty("Range", new DoubleValue(4, 64, 24, 1));
    public final BooleanProperty outline = new BooleanProperty("Outline", true);
    public final BooleanProperty softFill = new BooleanProperty("Soft Fill", false);

    private final GlowRenderer glowRenderer = new GlowRenderer();
    private final List<Entity> targets = new ArrayList<>();

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRenderWorldLast = event -> {
        targets.clear();
        double max = range.getValue().getInput();
        double maxSq = max * max;
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityItem) || entity.isDead)
                continue;
            if (mc.thePlayer.getDistanceSqToEntity(entity) > maxSq)
                continue;
            targets.add(entity);
        }
        if (targets.isEmpty())
            return;
        glowRenderer.render(targets, event.partialTicks, M3.primary(), 7, 3f,
                outline.getValue(), softFill.getValue() ? 0.10f : 0f);
    };

    @Override
    protected void onDisable() {
        glowRenderer.release();
        targets.clear();
    }
}
