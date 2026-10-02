package arsenic.module.impl.world;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.utils.render.RenderUtils;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

@ModuleInfo(name = "Breadcrumbs", category = ModuleCategory.RENDER, hidden = true)
public class Breadcrumbs extends Module {


    private final List<double[]> points = new ArrayList<>();
    private final List<Long> times = new ArrayList<>();
    private long lastPoint;

    @Override
    protected void onEnable() {
        points.clear();
        times.clear();
    }

    @Override
    protected void onDisable() {
        points.clear();
        times.clear();
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRender = event -> {
        long now = System.currentTimeMillis();

        if (now - lastPoint > 50) {
            points.add(new double[]{mc.player.getX(), mc.player.getY() + 0.1, mc.player.getZ()});
            times.add(now);
            lastPoint = now;
        }

        while (points.size() > 500) {
            points.remove(0);
            times.remove(0);
        }

        long fadeMs = (long) (5 * 1000);
        while (!times.isEmpty() && now - times.get(0) > fadeMs) {
            points.remove(0);
            times.remove(0);
        }

        if (points.size() < 2) return;

        int themeColor = arsenic.main.Arsenic.getInstance().getThemeManager().getCurrentTheme().getMainColor();

        // older segments fade out towards the tail
        int total = points.size();
        for (int i = 0; i < total - 1; i++) {
            double[] p1 = points.get(i);
            double[] p2 = points.get(i + 1);
            float alpha = (float) i / total;
            int color = RenderUtils.withAlpha(themeColor, (int) (alpha * 0.8f * 255));
            RenderUtils.drawLine(new Vec3(p1[0], p1[1], p1[2]), new Vec3(p2[0], p2[1], p2[2]), color, 2f);
        }
    };
}
