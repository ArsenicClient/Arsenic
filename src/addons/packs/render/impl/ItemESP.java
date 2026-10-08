import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.render.RenderUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.util.BlockPos;

/**
 * Highlights dropped items within a chosen range through walls. Each item is outlined on the block it lies in, in the
 * theme colour, so you can see drops from across the room.
 */
@ModuleInfo(name = "ItemESP", description = "Highlights dropped items through walls", category = ModuleCategory.RENDER)
public class ItemESP extends Module {

    public final BooleanProperty render = new BooleanProperty("Render", true);
    public final DoubleProperty range = new DoubleProperty("Range", new DoubleValue(8, 128, 48, 1));

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRenderWorld = event -> {
        if (!render.getValue()) return;
        int main = Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor();
        double max = range.getValue().getInput();
        for (Entity e : mc.theWorld.loadedEntityList) {
            if (!(e instanceof EntityItem) || mc.thePlayer.getDistanceToEntity(e) > max) continue;
            BlockPos pos = new BlockPos(e.posX, e.posY, e.posZ);
            RenderUtils.renderBlock(pos, ColorUtils.withAlpha(main, 70), false, true);
            RenderUtils.renderBlock(pos, ColorUtils.withAlpha(main, 220), true, false);
        }
    };
}
