import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.render.RenderUtils;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;

/**
 * Outlines the block your crosshair is on, in the theme colour. Reads mc.objectMouseOver, so it follows the silent
 * rotation: the outline shows what your clicks would actually hit.
 */
@ModuleInfo(name = "BlockOverlay", description = "Outlines the block your crosshair is on", category = ModuleCategory.RENDER)
public class BlockOverlay extends Module {

    public final BooleanProperty render = new BooleanProperty("Render", true);

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRenderWorld = event -> {
        if (!render.getValue()) return;
        MovingObjectPosition over = mc.objectMouseOver;
        if (over == null || over.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;
        BlockPos pos = over.getBlockPos();
        if (pos == null) return;
        int main = Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor();
        RenderUtils.renderBlock(pos, ColorUtils.withAlpha(main, 60), false, true);
        RenderUtils.renderBlock(pos, ColorUtils.withAlpha(main, 230), true, false);
    };
}
