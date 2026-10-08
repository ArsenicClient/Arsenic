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
import net.minecraft.potion.Potion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Lists nearby players whose movement looks like a cheat, with the reason. Information only: nothing is sent and nothing
 * is targeted. Two checks, each a score that rises while the pattern lasts and falls back when it stops:
 * - speed: horizontal movement faster than a sprinting player can manage (a speed potion raises the limit);
 * - hover: left the ground but stayed at the same height for a long time, outside water and ladders.
 * False positives happen (knockback, teleports, boats, mounts), so the list shows the score and the bots are skipped.
 */
@ModuleInfo(name = "HackerDetector", description = "Lists nearby players whose movement looks like a cheat", category = ModuleCategory.RENDER)
public class HackerDetector extends Module {

    public final BooleanProperty hud = new BooleanProperty("HUD", true);
    public final DoubleProperty range = new DoubleProperty("Range", new DoubleValue(8, 64, 32, 1));
    public final DoubleProperty flagAt = new DoubleProperty("Flag At", new DoubleValue(5, 40, 12, 1));

    private static final double SPRINT_LIMIT = 0.6;     // blocks per tick
    private static final double SPEED_POTION_LIMIT = 0.9;

    private static final class Score {
        double lastX, lastY, lastZ;
        double speed, hover;
        int seen;
        boolean first = true;
    }

    private final HudElement panel = hudElement("HackerDetector", 4, 300, 170, 16);
    private final Map<Integer, Score> scores = new HashMap<>();
    private final List<String> lines = new ArrayList<>();

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        double max = range.getValue().getInput();
        Map<Integer, Score> next = new HashMap<>();
        lines.clear();
        for (EntityPlayer p : mc.theWorld.playerEntities) {
            if (p == mc.thePlayer || p.isDead || mc.thePlayer.getDistanceToEntity(p) > max || AntiBot.isBot(p)) continue;
            Score s = scores.get(p.getEntityId());
            if (s == null) s = new Score();
            next.put(p.getEntityId(), s);
            if (s.first) {
                s.first = false;
            } else {
                double limit = p.isPotionActive(Potion.moveSpeed) ? SPEED_POTION_LIMIT : SPRINT_LIMIT;
                double horizontal = Math.hypot(p.posX - s.lastX, p.posZ - s.lastZ);
                s.speed = horizontal > limit ? s.speed + 1 : Math.max(0, s.speed - 0.1);

                double dy = Math.abs(p.posY - s.lastY);
                boolean floating = !p.onGround && dy < 0.005 && !p.isInWater() && !p.isOnLadder();
                s.hover = floating ? s.hover + 1 : Math.max(0, s.hover - 0.5);
            }
            s.lastX = p.posX;
            s.lastY = p.posY;
            s.lastZ = p.posZ;
            s.seen++;

            double flag = flagAt.getValue().getInput();
            if (s.speed >= flag) lines.add(p.getName() + "  speed " + (int) s.speed);
            if (s.hover >= flag * 1.5) lines.add(p.getName() + "  hover " + (int) s.hover);
        }
        scores.clear();
        scores.putAll(next);
        Iterator<Integer> it = scores.keySet().iterator();
        while (it.hasNext()) if (!next.containsKey(it.next())) it.remove();
        if (lines.size() > 5) lines.subList(5, lines.size()).clear();
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRender2D> onRender = event -> {
        if (!hud.getValue() || lines.isEmpty()) return;
        int width = 170;
        for (String l : lines) width = Math.max(width, mc.fontRendererObj.getStringWidth(l) + 12);
        panel.setSize(width, 6 + lines.size() * 10);
        DrawUtils.drawRoundedRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 5, 0x96121212);
        int y = panel.y + 4;
        for (String l : lines) {
            mc.fontRendererObj.drawStringWithShadow(l, panel.x + 6, y, 0xFFFF8C1A);
            y += 10;
        }
    };
}
