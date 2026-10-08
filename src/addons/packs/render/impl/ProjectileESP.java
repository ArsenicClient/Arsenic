import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.render.RenderUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.util.BlockPos;

/**
 * Highlights thrown and fired projectiles through walls: arrows, fireballs, ender pearls and other thrown items each
 * get their own colour. Outlines the block the projectile is in.
 */
@ModuleInfo(name = "ProjectileESP", description = "Highlights arrows, fireballs and thrown items through walls", category = ModuleCategory.RENDER)
public class ProjectileESP extends Module {

    public final BooleanProperty render = new BooleanProperty("Render", true);

    private static final int ARROW = 0xFFFFFFFF;
    private static final int FIREBALL = 0xFFFF8C1A;
    private static final int PEARL = 0xFFB35CFF;
    private static final int OTHER = 0xFF4DD2FF;

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRenderWorld = event -> {
        if (!render.getValue()) return;
        for (Entity e : mc.theWorld.loadedEntityList) {
            int color = colorOf(e);
            if (color == 0) continue;
            BlockPos pos = new BlockPos(e.posX, e.posY, e.posZ);
            RenderUtils.renderBlock(pos, ColorUtils.withAlpha(color, 90), false, true);
            RenderUtils.renderBlock(pos, ColorUtils.withAlpha(color, 230), true, false);
        }
    };

    /** 0 means "not a projectile we highlight". */
    private static int colorOf(Entity e) {
        if (e instanceof EntityArrow) return ARROW;
        if (e instanceof EntityFireball) return FIREBALL;
        if (e.getClass().getSimpleName().contains("EnderPearl")) return PEARL;
        if (e instanceof EntityThrowable) return OTHER;
        return 0;
    }
}
