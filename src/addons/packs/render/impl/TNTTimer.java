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
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.util.BlockPos;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Shows the fuse left on primed TNT within a chosen range: a red outline through walls on the block, and a HUD list of
 * the closest ones with seconds left (sorted by time, soonest first).
 */
@ModuleInfo(name = "TNTTimer", description = "Shows the fuse left on primed TNT and highlights it", category = ModuleCategory.RENDER)
public class TNTTimer extends Module {

    public final BooleanProperty render = new BooleanProperty("Render", true);
    public final BooleanProperty hud = new BooleanProperty("HUD", true);
    public final DoubleProperty range = new DoubleProperty("Range", new DoubleValue(8, 64, 32, 1));

    private final HudElement panel = hudElement("TNTTimer", 4, 120, 100, 16);
    private final List<EntityTNTPrimed> tnt = new ArrayList<>();

    private List<EntityTNTPrimed> nearby() {
        tnt.clear();
        double max = range.getValue().getInput();
        for (Entity e : mc.theWorld.loadedEntityList) {
            if (e instanceof EntityTNTPrimed && mc.thePlayer.getDistanceToEntity(e) <= max) tnt.add((EntityTNTPrimed) e);
        }
        tnt.sort((a, b) -> Integer.compare(a.fuse, b.fuse));
        return tnt;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRenderWorld = event -> {
        if (!render.getValue()) return;
        for (EntityTNTPrimed t : nearby()) {
            BlockPos pos = new BlockPos(t.posX, t.posY, t.posZ);
            RenderUtils.renderBlock(pos, new Color(255, 60, 60, 90).getRGB(), false, true);
            RenderUtils.renderBlock(pos, new Color(255, 60, 60, 230).getRGB(), true, false);
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRender2D> onRender = event -> {
        if (!hud.getValue()) return;
        List<EntityTNTPrimed> list = nearby();
        if (list.isEmpty()) return;
        int shown = Math.min(4, list.size());
        int width = 110;
        panel.setSize(width, 6 + shown * 10);
        DrawUtils.drawRoundedRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 5, 0x96121212);
        int y = panel.y + 4;
        for (int i = 0; i < shown; i++) {
            EntityTNTPrimed t = list.get(i);
            String text = String.format("TNT %.1fs", Math.max(0, t.fuse) / 20.0);
            mc.fontRendererObj.drawStringWithShadow(text, panel.x + 6, y, 0xFFFF5555);
            y += 10;
        }
    };
}
