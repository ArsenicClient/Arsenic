import arsenic.module.ModuleTier;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.render.RenderUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows the light level on the ground around you, so you can see where hostile mobs can spawn. Spots at level 7 or below
 * (where hostile mobs can spawn in the dark) are red, the rest are white. Only open-air spots with a solid block under
 * them are shown. The numbers are refreshed every quarter second.
 */
@ModuleInfo(name = "LightLevel", description = "Shows the light level on the ground around you", category = ModuleCategory.RENDER, tier = ModuleTier.VISUAL)
public class LightLevel extends Module {

    public final BooleanProperty render = new BooleanProperty("Render", true);
    public final DoubleProperty radius = new DoubleProperty("Radius", new DoubleValue(2, 12, 6, 1));

    private static final class Spot {
        final BlockPos pos;
        final int level;

        Spot(BlockPos pos, int level) {
            this.pos = pos;
            this.level = level;
        }
    }

    private final List<Spot> spots = new ArrayList<>();
    private int tickCounter;

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (++tickCounter < 5) return;
        tickCounter = 0;
        spots.clear();
        int r = (int) radius.getValue().getInput();
        BlockPos origin = mc.player.blockPosition();
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
            // find the ground: the first solid block below the player's feet within a few blocks
            for (int dy = 0; dy >= -4; dy--) {
                BlockPos ground = origin.offset(x, dy, z);
                if (!mc.level.isLoaded(ground)) break;
                if (!mc.level.getBlockState(ground).isSolid()) continue;
                BlockPos spot = ground.above();
                if (!mc.level.getBlockState(spot).isAir()) break;
                spots.add(new Spot(spot, mc.level.getMaxLocalRawBrightness(spot)));
                break;
            }
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRenderWorld = event -> {
        if (!render.getValue() || spots.isEmpty()) return;
        for (Spot s : spots) {
            drawText(String.valueOf(s.level), s.pos.getX() + 0.5, s.pos.getY() + 0.1, s.pos.getZ() + 0.5,
                    s.level <= 7 ? 0xFFFF5555 : 0xFFFFFFFF);
        }
    };

    /** The number floating just above the ground, facing the camera. */
    private static void drawText(String text, double x, double y, double z, int argb) {
        RenderUtils.drawWorldText(new Vec3(x, y, z), text, argb, 0.75f);
    }
}
