import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventTick;
import arsenic.gui.hud.HudElement;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.AntiBot;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.render.DrawUtils;
import net.minecraft.entity.player.EntityPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lists players who have stood still for longer than the chosen time, in BedWars lobbies and similar places. Position is
 * compared each tick, so a player who only turns their head still counts as still. Bots are skipped.
 */
@ModuleInfo(name = "AfkDetector", description = "Lists players who have stood still for a long time", category = ModuleCategory.RENDER)
public class AfkDetector extends Module {

    public final BooleanProperty hud = new BooleanProperty("HUD", true);
    public final DoubleProperty after = new DoubleProperty("After (s)", new DoubleValue(10, 300, 60, 5));

    private final HudElement panel = hudElement("AfkDetector", 4, 560, 170, 16);
    private final Map<Integer, double[]> still = new HashMap<>();   // id -> {x, z, ticks still}
    private final List<String> lines = new ArrayList<>();

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        Map<Integer, double[]> next = new HashMap<>();
        lines.clear();
        long need = (long) (after.getValue().getInput() * 20);
        for (EntityPlayer p : mc.theWorld.playerEntities) {
            if (p == mc.thePlayer || p.isDead || AntiBot.isBot(p)) continue;
            double[] s = still.get(p.getEntityId());
            if (s == null || Math.abs(s[0] - p.posX) > 0.001 || Math.abs(s[1] - p.posZ) > 0.001) {
                s = new double[]{p.posX, p.posZ, 0};
            } else {
                s[2]++;
            }
            next.put(p.getEntityId(), s);
            if (s[2] >= need) lines.add(p.getName() + "  " + (int) (s[2] / 20) + "s");
        }
        still.clear();
        still.putAll(next);
        if (lines.size() > 4) lines.subList(4, lines.size()).clear();
    };

    @EventLink
    public final Listener<EventRender2D> onRender = event -> {
        if (!hud.getValue() || lines.isEmpty()) return;
        int width = 170;
        for (String l : lines) width = Math.max(width, mc.fontRendererObj.getStringWidth(l) + 12);
        panel.setSize(width, 6 + lines.size() * 10);
        DrawUtils.drawRoundedRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 5, 0x96121212);
        int y = panel.y + 4;
        for (String l : lines) {
            mc.fontRendererObj.drawStringWithShadow(l, panel.x + 6, y, 0xFFFFFFFF);
            y += 10;
        }
    };
}
