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
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.opengl.GL11;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * A fading line behind each other player, through the last few positions they were at (chest height). The line is
 * brightest at the player and fades toward the oldest point. Filtered players (AntiBot) get no trail.
 */
@ModuleInfo(name = "PlayerTrail", description = "A fading trail behind other players", category = ModuleCategory.RENDER)
public class PlayerTrail extends Module {

    public final BooleanProperty render = new BooleanProperty("Render", true);
    public final DoubleProperty length = new DoubleProperty("Length (ticks)", new DoubleValue(5, 60, 20, 1));

    private final Map<Integer, Deque<double[]>> trails = new HashMap<>();

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        int max = (int) length.getValue().getInput();
        Map<Integer, Boolean> present = new HashMap<>();
        for (EntityPlayer p : mc.theWorld.playerEntities) {
            if (p == mc.thePlayer || p.isDead || AntiBot.isBot(p)) continue;
            present.put(p.getEntityId(), true);
            Deque<double[]> trail = trails.computeIfAbsent(p.getEntityId(), k -> new ArrayDeque<>());
            trail.addFirst(new double[]{p.posX, p.posY + p.height / 2, p.posZ});
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
        float r = ((main >> 16) & 0xFF) / 255f, g = ((main >> 8) & 0xFF) / 255f, b = (main & 0xFF) / 255f;

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glLineWidth(2f);
        double vx = mc.getRenderManager().viewerPosX, vy = mc.getRenderManager().viewerPosY, vz = mc.getRenderManager().viewerPosZ;
        for (Deque<double[]> trail : trails.values()) {
            if (trail.size() < 2) continue;
            int n = trail.size(), i = 0;
            GL11.glBegin(GL11.GL_LINE_STRIP);
            for (double[] pt : trail) {
                GlStateManager.color(r, g, b, 1f - (float) i / n);
                GL11.glVertex3d(pt[0] - vx, pt[1] - vy, pt[2] - vz);
                i++;
            }
            GL11.glEnd();
        }
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    };
}
