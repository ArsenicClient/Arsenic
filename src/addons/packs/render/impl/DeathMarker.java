import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.ButtonProperty;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/**
 * Marks where you last died with a beam and the distance to it. The marker is set the tick your health first reaches
 * zero, and stays until you press Clear or disable the addon.
 */
@ModuleInfo(name = "DeathMarker", description = "Marks where you last died", category = ModuleCategory.RENDER)
public class DeathMarker extends Module {

    public final BooleanProperty render = new BooleanProperty("Render", true);
    public final ButtonProperty clear = new ButtonProperty("Clear", () -> marked = false);

    private static final double BEAM_HEIGHT = 32;

    private boolean marked, wasDead;
    private double deathX, deathY, deathZ;

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        boolean dead = mc.thePlayer.getHealth() <= 0 || mc.thePlayer.isDead;
        if (dead && !wasDead) {
            marked = true;
            deathX = mc.thePlayer.posX;
            deathY = mc.thePlayer.posY;
            deathZ = mc.thePlayer.posZ;
        }
        wasDead = dead;
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRenderWorld = event -> {
        if (!render.getValue() || !marked) return;
        int main = Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor();
        float r = ((main >> 16) & 0xFF) / 255f, g = ((main >> 8) & 0xFF) / 255f, b = (main & 0xFF) / 255f;
        double vx = mc.getRenderManager().viewerPosX, vy = mc.getRenderManager().viewerPosY, vz = mc.getRenderManager().viewerPosZ;
        double x = deathX - vx, y = deathY - vy, z = deathZ - vz;

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glLineWidth(3f);
        GlStateManager.color(r, g, b, 0.9f);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(x, y, z);
        GL11.glVertex3d(x, y + BEAM_HEIGHT, z);
        GL11.glEnd();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();

        String text = "Death  " + (int) Math.sqrt(mc.thePlayer.getDistanceSq(deathX, deathY, deathZ)) + "m";
        drawText(text, x, y + BEAM_HEIGHT + 1, z, 0xFFFFFFFF);
    };

    private static void drawText(String text, double x, double y, double z, int argb) {
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
