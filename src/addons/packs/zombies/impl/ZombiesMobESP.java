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
import net.minecraft.entity.monster.IMob;
import net.minecraft.util.BlockPos;

/**
 * Outlines hostile mobs (zombies, skeletons and the rest of the monsters) through walls within a range, so you can see
 * what is coming.
 */
@ModuleInfo(name = "ZombiesMobESP", description = "Outlines hostile mobs through walls within a range", category = ModuleCategory.RENDER)
public class ZombiesMobESP extends Module {

    public final BooleanProperty render = new BooleanProperty("Render", true);
    public final DoubleProperty range = new DoubleProperty("Range", new DoubleValue(8, 64, 32, 1));

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRenderWorld = event -> {
        if (!render.getValue()) return;
        int main = Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor();
        double max = range.getValue().getInput();
        for (Entity e : mc.theWorld.loadedEntityList) {
            if (!(e instanceof IMob) || e.isDead || mc.thePlayer.getDistanceToEntity(e) > max) continue;
            BlockPos pos = new BlockPos(e.posX, e.posY + e.height / 2, e.posZ);
            RenderUtils.renderBlock(pos, ColorUtils.withAlpha(main, 60), false, true);
            RenderUtils.renderBlock(pos, ColorUtils.withAlpha(main, 230), true, false);
        }
    };
}
