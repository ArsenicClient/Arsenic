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
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

/**
 * Highlights a ring of exposed block tops: for every column whose centre is within half a block of the radius from the
 * centre (world X and Z, default 0 0), the highest solid block with air above it, within 5 blocks above or below your
 * feet, is outlined on its top face.
 */
@ModuleInfo(name = "RingHighlight", description = "Highlights the exposed block tops in a ring around a point", category = ModuleCategory.RENDER)
public class RingHighlight extends Module {

    public final DoubleProperty radius = new DoubleProperty("Radius", new DoubleValue(1, 64, 5, 1));
    public final DoubleProperty centerX = new DoubleProperty("Center X", new DoubleValue(-30000000, 30000000, 0, 1));
    public final DoubleProperty centerZ = new DoubleProperty("Center Z", new DoubleValue(-30000000, 30000000, 0, 1));
    public final BooleanProperty render = new BooleanProperty("Render", true);

    /** How far above and below your feet a top is searched for. */
    private static final int BAND = 5;

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRenderWorld = event -> {
        if (!render.getValue()) return;
        double r = radius.getValue().getInput();
        double cx = centerX.getValue().getInput(), cz = centerZ.getValue().getInput();
        int main = Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor();
        int reach = (int) Math.ceil(r) + 1;
        int x0 = (int) Math.floor(cx), z0 = (int) Math.floor(cz);
        int footY = (int) Math.floor(mc.thePlayer.posY);

        for (int x = x0 - reach; x <= x0 + reach; x++) {
            for (int z = z0 - reach; z <= z0 + reach; z++) {
                // a column belongs to the ring when its centre is within half a block of the radius
                double dist = Math.hypot(x + 0.5 - cx, z + 0.5 - cz);
                if (Math.abs(dist - r) > 0.5) continue;
                BlockPos top = exposedTop(x, z, footY);
                if (top == null) continue;
                RenderUtils.renderBlockFace(top, EnumFacing.UP, ColorUtils.withAlpha(main, 60), false, true);
                RenderUtils.renderBlockFace(top, EnumFacing.UP, ColorUtils.withAlpha(main, 230), true, false);
            }
        }
    };

    /** The highest solid block with air above it in the column, from {@code BAND} above the feet to {@code BAND} below. */
    private BlockPos exposedTop(int x, int z, int footY) {
        for (int y = footY + BAND; y >= footY - BAND; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!mc.theWorld.isBlockLoaded(pos)) return null;
            if (solid(pos) && mc.theWorld.isAirBlock(pos.up()))
                return pos;
        }
        return null;
    }

    private boolean solid(BlockPos pos) {
        if (mc.theWorld.isAirBlock(pos)) return false;
        net.minecraft.block.material.Material material = mc.theWorld.getBlockState(pos).getBlock().getMaterial();
        return !material.isReplaceable() && material.isSolid();
    }
}
