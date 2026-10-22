import arsenic.module.ModuleTier;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.impl.client.AntiBot;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.render.RenderUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * A fading line behind each other player, through the last few positions they were at (chest height). The line is
 * brightest at the player and fades toward the oldest point. Filtered players (AntiBot) get no trail.
 */
@ModuleInfo(name = "PlayerTrail", description = "A fading trail behind other players", category = ModuleCategory.RENDER, tier = ModuleTier.VISUAL)
public class PlayerTrail extends Module {

    public final BooleanProperty render = new BooleanProperty("Render", true);
    public final DoubleProperty length = new DoubleProperty("Length (ticks)", new DoubleValue(5, 60, 20, 1));

    private final Map<Integer, Deque<double[]>> trails = new HashMap<>();

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        int max = (int) length.getValue().getInput();
        Map<Integer, Boolean> present = new HashMap<>();
        for (Player p : mc.level.players()) {
            if (p == mc.player || !p.isAlive() || AntiBot.isBot(p)) continue;
            present.put(p.getId(), true);
            Deque<double[]> trail = trails.computeIfAbsent(p.getId(), k -> new ArrayDeque<>());
            trail.addFirst(new double[]{p.getX(), p.getY() + p.getBbHeight() / 2, p.getZ()});
            while (trail.size() > max) trail.removeLast();
        }
        Iterator<Map.Entry<Integer, Deque<double[]>>> it = trails.entrySet().iterator();
        while (it.hasNext()) if (!present.containsKey(it.next().getKey())) it.remove();
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRenderWorld = event -> {
        if (!render.getValue() || trails.isEmpty()) return;
        int main = Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor();
        for (Deque<double[]> trail : trails.values()) {
            if (trail.size() < 2) continue;
            int n = trail.size(), i = 0;
            Vec3 prev = null;
            for (double[] pt : trail) {
                Vec3 point = new Vec3(pt[0], pt[1], pt[2]);
                if (prev != null)
                    RenderUtils.drawLine(prev, point, RenderUtils.withAlpha(main, (int) (255 * (1f - (float) i / n))), 2f);
                prev = point;
                i++;
            }
        }
    };
}
