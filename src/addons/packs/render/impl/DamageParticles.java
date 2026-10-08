import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventAttack;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventTick;
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
 * Shows the damage of each hit you land as a number that rises over the target and fades out. The damage is the drop in
 * the target's health from just before your attack to the first tick it is hurt, so it can be off by armour or
 * absorption.
 */
@ModuleInfo(name = "DamageParticles", description = "Floats the damage of your hits over the target", category = ModuleCategory.RENDER)
public class DamageParticles extends Module {

    public final BooleanProperty render = new BooleanProperty("Render", true);

    private static final int LIFE_TICKS = 24;

    private static final class Particle {
        final double x, y, z;
        final String text;
        int age;

        Particle(double x, double y, double z, String text) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.text = text;
        }
    }

    private final List<Particle> particles = new ArrayList<>();
    private final MSTimer pendingTimer = new MSTimer();
    private EntityLivingBase pending;
    private float healthBefore;

    @RequiresPlayer
    @EventLink
    public final Listener<EventAttack> onAttack = event -> {
        Entity e = event.getTarget();
        if (!(e instanceof EntityLivingBase)) return;
        pending = (EntityLivingBase) e;
        healthBefore = pending.getHealth();
        pendingTimer.reset();
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            if (++p.age > LIFE_TICKS) it.remove();
        }
        if (pending == null) return;
        if (pending.hurtTime > 0) {
            float dealt = healthBefore - pending.getHealth();
            if (dealt > 0) {
                particles.add(new Particle(pending.posX, pending.posY + pending.height * 0.8, pending.posZ,
                        String.format("-%.1f", dealt)));
            }
            pending = null;
        } else if (pendingTimer.hasTimeElapsed(200)) {
            pending = null;
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRenderWorld = event -> {
        if (!render.getValue()) return;
        for (Particle p : particles) {
            float t = (p.age + event.partialTicks) / LIFE_TICKS;
            float alpha = 1f - t;
            double x = p.x - mc.getRenderManager().viewerPosX;
            double y = p.y + t * 1.2 - mc.getRenderManager().viewerPosY;
            double z = p.z - mc.getRenderManager().viewerPosZ;
            drawText(p.text, x, y, z, ((int) (alpha * 255) << 24) | 0xFFFF5555);
        }
    };

    static void drawText(String text, double x, double y, double z, int argb) {
        GlStateManager.pushMatrix();
        GL11.glTranslated(x, y, z);
        GL11.glNormal3f(0f, 1f, 0f);
        GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0f, 1f, 0f);
        GlStateManager.rotate(mc.getRenderManager().playerViewX, 1f, 0f, 0f);
        float s = 0.025f;
        GlStateManager.scale(-s, -s, s);
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        mc.fontRendererObj.drawString(text, -mc.fontRendererObj.getStringWidth(text) / 2, 0, argb);
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }
}
