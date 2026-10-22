import arsenic.module.ModuleTier;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventAttack;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.timer.MSTimer;
import arsenic.utils.render.RenderUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Plays a short burst where a kill you made happens: a ring that grows out from the target's chest, with spokes, fading
 * over about half a second. A kill is a landed hit after which the target is dead or has no health left.
 */
@ModuleInfo(name = "KillEffect", description = "Draws a burst where a kill you made happens", category = ModuleCategory.RENDER, tier = ModuleTier.QOL)
public class KillEffect extends Module {

    public final BooleanProperty render = new BooleanProperty("Render", true);

    private static final int LIFE_TICKS = 10;

    private static final class Burst {
        final double x, y, z;
        int age;

        Burst(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private final List<Burst> bursts = new ArrayList<>();
    private final MSTimer pendingTimer = new MSTimer();
    private LivingEntity pending;

    @RequiresPlayer
    @EventLink
    public final Listener<EventAttack> onAttack = event -> {
        Entity e = event.getTarget();
        if (!(e instanceof LivingEntity)) return;
        pending = (LivingEntity) e;
        pendingTimer.reset();
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        Iterator<Burst> it = bursts.iterator();
        while (it.hasNext()) if (++it.next().age > LIFE_TICKS) it.remove();

        if (pending == null) return;
        if (pending.hurtTime > 0) {
            if (!pending.isAlive() || pending.getHealth() <= 0) {
                bursts.add(new Burst(pending.getX(), pending.getY() + pending.getBbHeight() * 0.6, pending.getZ()));
            }
            pending = null;
        } else if (pendingTimer.hasTimeElapsed(200)) {
            pending = null;
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRenderWorld = event -> {
        if (!render.getValue() || bursts.isEmpty()) return;
        int main = Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor();
        for (Burst burst : bursts) {
            float t = (burst.age + event.partialTicks) / LIFE_TICKS;
            double radius = 0.2 + t * 1.2;
            int colour = RenderUtils.withAlpha(main, (int) ((1f - t) * 255));
            Vec3 centre = new Vec3(burst.x, burst.y, burst.z);
            RenderUtils.drawFlatCircle(centre, radius, colour, 2f);
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * 2 * i / 8;
                RenderUtils.drawLine(centre.add(Math.cos(a) * radius * 0.4, 0, Math.sin(a) * radius * 0.4),
                        centre.add(Math.cos(a) * radius, 0.3 * (1 - t), Math.sin(a) * radius), colour, 2f);
            }
        }
    };
}
