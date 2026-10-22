import arsenic.module.ModuleTier;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.gui.hud.HudElement;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.core.BlockPos;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Shows the fuse left on primed TNT within a chosen range: a red outline through walls on the block, and a HUD list of
 * the closest ones with seconds left (sorted by time, soonest first).
 */
@ModuleInfo(name = "TNTTimer", description = "Shows the fuse left on primed TNT and highlights it", category = ModuleCategory.RENDER, tier = ModuleTier.VISUAL)
public class TNTTimer extends Module {

    public final BooleanProperty render = new BooleanProperty("Render", true);
    public final BooleanProperty hud = new BooleanProperty("HUD", true);
    public final DoubleProperty range = new DoubleProperty("Range", new DoubleValue(8, 64, 32, 1));

    private final HudElement panel = hudElement("TNTTimer", 4, 120, 100, 16);
    private final List<PrimedTnt> tnt = new ArrayList<>();

    private List<PrimedTnt> nearby() {
        tnt.clear();
        double max = range.getValue().getInput();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof PrimedTnt && mc.player.distanceTo(e) <= max) tnt.add((PrimedTnt) e);
        }
        tnt.sort((a, b) -> Integer.compare(a.getFuse(), b.getFuse()));
        return tnt;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRenderWorld = event -> {
        if (!render.getValue()) return;
        for (PrimedTnt t : nearby()) {
            BlockPos pos = t.blockPosition();
            RenderUtils.renderBlock(pos, new Color(255, 60, 60, 90).getRGB(), false, true);
            RenderUtils.renderBlock(pos, new Color(255, 60, 60, 230).getRGB(), true, false);
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRender2D> onRender = event -> {
        if (!hud.getValue()) return;
        List<PrimedTnt> list = nearby();
        if (list.isEmpty()) return;
        int shown = Math.min(4, list.size());
        int width = 110;
        panel.setSize(width, 6 + shown * 10);
        DrawUtils.drawRoundedRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 5, 0x96121212);
        int y = panel.y + 4;
        for (int i = 0; i < shown; i++) {
            PrimedTnt t = list.get(i);
            String text = String.format("TNT %.1fs", Math.max(0, t.getFuse()) / 20.0);
            DrawUtils.drawStringWithShadow(text, panel.x + 6, y, 0xFFFF5555);
            y += 10;
        }
    };
}
