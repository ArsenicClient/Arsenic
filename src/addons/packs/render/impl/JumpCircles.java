import arsenic.module.ModuleTier;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventMovementInput;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.render.RenderUtils;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Draws a ring under your feet when you press jump on the ground, which grows and fades over about half a second. Only
 * the jump key counts: knockback, jump reset and falling off an edge do not draw one. The ring is drawn where you left
 * the ground.
 */
@ModuleInfo(name = "JumpCircles", description = "Draws a ring under you when you jump", category = ModuleCategory.RENDER, tier = ModuleTier.QOL)
public class JumpCircles extends Module {

    public final BooleanProperty render = new BooleanProperty("Render", true);

    private static final int LIFE_TICKS = 10;

    private static final class Ring {
        final double x, y, z;
        int age;

        Ring(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private final List<Ring> rings = new ArrayList<>();

    // Runs in the player's update just before the jump is decided, so the key and onGround are the ones the jump uses
    @RequiresPlayer
    @EventLink
    public final Listener<EventMovementInput> onMovementInput = event -> {
        if (!mc.options.keyJump.isDown() || mc.gui.screen() != null || !mc.player.onGround())
            return;
        rings.add(new Ring(mc.player.getX(), mc.player.getY(), mc.player.getZ()));
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        Iterator<Ring> it = rings.iterator();
        while (it.hasNext()) {
            if (++it.next().age > LIFE_TICKS) it.remove();
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRenderWorld = event -> {
        if (!render.getValue() || rings.isEmpty()) return;
        int main = Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor();
        for (Ring ring : rings) {
            float t = (ring.age + event.partialTicks) / LIFE_TICKS;
            double radius = 0.3 + t * 0.7;
            RenderUtils.drawFlatCircle(new Vec3(ring.x, ring.y + 0.02, ring.z), radius,
                    RenderUtils.withAlpha(main, (int) ((1f - t) * 255)), 2f);
        }
    };
}
