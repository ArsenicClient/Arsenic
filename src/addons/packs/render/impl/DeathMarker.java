import arsenic.utils.render.RenderUtils;
import net.minecraft.world.phys.Vec3;
import arsenic.module.ModuleTier;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.ButtonProperty;

/**
 * Marks where you last died with a beam and the distance to it. The marker is set the tick your health first reaches
 * zero, and stays until you press Clear or disable the addon.
 */
@ModuleInfo(name = "DeathMarker", description = "Marks where you last died", category = ModuleCategory.RENDER, tier = ModuleTier.QOL)
public class DeathMarker extends Module {

    public final BooleanProperty render = new BooleanProperty("Render", true);
    public final ButtonProperty clear = new ButtonProperty("Clear", () -> marked = false);

    private static final double BEAM_HEIGHT = 32;

    private boolean marked, wasDead;
    private double deathX, deathY, deathZ;

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        boolean dead = mc.player.getHealth() <= 0 || !mc.player.isAlive();
        if (dead && !wasDead) {
            marked = true;
            deathX = mc.player.getX();
            deathY = mc.player.getY();
            deathZ = mc.player.getZ();
        }
        wasDead = dead;
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRenderWorld = event -> {
        if (!render.getValue() || !marked) return;
        int main = Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor();
        Vec3 base = new Vec3(deathX, deathY, deathZ);
        RenderUtils.drawLine(base, base.add(0, BEAM_HEIGHT, 0), RenderUtils.withAlpha(main, 230), 3f);

        String text = "Death  " + (int) Math.sqrt(mc.player.distanceToSqr(deathX, deathY, deathZ)) + "m";
        RenderUtils.drawWorldText(base.add(0, BEAM_HEIGHT + 1, 0), text, 0xFFFFFFFF, 1f);
    };
}
