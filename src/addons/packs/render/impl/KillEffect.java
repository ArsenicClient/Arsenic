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
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Plays a short burst where a kill you made happens: a ring that grows out from the target's chest, with spokes, fading
 * over about half a second. A kill is a landed hit after which the target is dead or has no health left.
 */
@ModuleInfo(name = "KillEffect", description = "Draws a burst where a kill you made happens", category = ModuleCategory.RENDER)
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
    private EntityLivingBase pending;

    @RequiresPlayer
    @EventLink
    public final Listener<EventAttack> onAttack = event -> {
        Entity e = event.getTarget();
        if (!(e instanceof EntityLivingBase)) return;
        pending = (EntityLivingBase) e;
        pendingTimer.reset();
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        Iterator<Burst> it = bursts.iterator();
        while (it.hasNext()) if (++it.next().age > LIFE_TICKS) it.remove();

        if (pending == null) return;
        if (pending.hurtTime > 0) {
            if (pending.isDead || pending.getHealth() <= 0) {
                bursts.add(new Burst(pending.posX, pending.posY + pending.height * 0.6, pending.posZ));
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
        float r = ((main >> 16) & 0xFF) / 255f, g = ((main >> 8) & 0xFF) / 255f, b = (main & 0xFF) / 255f;

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glLineWidth(2f);
        for (Burst burst : bursts) {
            float t = (burst.age + event.partialTicks) / LIFE_TICKS;
            double radius = 0.2 + t * 1.2;
            double x = burst.x - mc.getRenderManager().viewerPosX;
            double y = burst.y - mc.getRenderManager().viewerPosY;
            double z = burst.z - mc.getRenderManager().viewerPosZ;
            GlStateManager.color(r, g, b, 1f - t);
            GL11.glBegin(GL11.GL_LINE_LOOP);
            for (int i = 0; i < 24; i++) {
                double a = Math.PI * 2 * i / 24;
                GL11.glVertex3d(x + Math.cos(a) * radius, y, z + Math.sin(a) * radius);
            }
            GL11.glEnd();
            GL11.glBegin(GL11.GL_LINES);
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * 2 * i / 8;
                GL11.glVertex3d(x + Math.cos(a) * radius * 0.4, y, z + Math.sin(a) * radius * 0.4);
                GL11.glVertex3d(x + Math.cos(a) * radius, y + 0.3 * (1 - t), z + Math.sin(a) * radius);
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
