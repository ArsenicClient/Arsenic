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
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Draws a ring under your feet when you press jump on the ground, which grows and fades over about half a second. Only
 * the jump key counts: knockback, jump reset and falling off an edge do not draw one. The ring is drawn where you left
 * the ground.
 */
@ModuleInfo(name = "JumpCircles", description = "Draws a ring under you when you jump", category = ModuleCategory.RENDER)
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
        if (!mc.gameSettings.keyBindJump.isKeyDown() || mc.currentScreen != null || !mc.thePlayer.onGround)
            return;
        rings.add(new Ring(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ));
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
        float r = ((main >> 16) & 0xFF) / 255f, g = ((main >> 8) & 0xFF) / 255f, b = (main & 0xFF) / 255f;

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glLineWidth(2f);
        for (Ring ring : rings) {
            float t = (ring.age + event.partialTicks) / LIFE_TICKS;
            double radius = 0.3 + t * 0.7;
            double x = ring.x - mc.getRenderManager().viewerPosX;
            double y = ring.y + 0.02 - mc.getRenderManager().viewerPosY;
            double z = ring.z - mc.getRenderManager().viewerPosZ;
            GlStateManager.color(r, g, b, 1f - t);
            GL11.glBegin(GL11.GL_LINE_LOOP);
            for (int i = 0; i < 32; i++) {
                double a = Math.PI * 2 * i / 32;
                GL11.glVertex3d(x + Math.cos(a) * radius, y, z + Math.sin(a) * radius);
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
