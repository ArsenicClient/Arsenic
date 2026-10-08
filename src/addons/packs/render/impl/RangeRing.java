import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/**
 * Draws a ring on the ground around you at the chosen radius, so you can see what is in reach. Set the radius to your
 * reach (3 is the vanilla reach). The ring follows your interpolated position, so it stays put while you move.
 */
@ModuleInfo(name = "RangeRing", description = "Draws a ring on the ground at your reach", category = ModuleCategory.RENDER)
public class RangeRing extends Module {

    public final BooleanProperty render = new BooleanProperty("Render", true);
    public final DoubleProperty radius = new DoubleProperty("Radius", new DoubleValue(1, 6, 3, 0.1));

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRenderWorld = event -> {
        if (!render.getValue()) return;
        int main = Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor();
        float r = ((main >> 16) & 0xFF) / 255f, g = ((main >> 8) & 0xFF) / 255f, b = (main & 0xFF) / 255f;
        double pt = event.partialTicks;
        double x = mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * pt - mc.getRenderManager().viewerPosX;
        double y = mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * pt - mc.getRenderManager().viewerPosY + 0.02;
        double z = mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * pt - mc.getRenderManager().viewerPosZ;
        double rad = radius.getValue().getInput();

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glLineWidth(2f);
        GlStateManager.color(r, g, b, 0.8f);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i < 64; i++) {
            double a = Math.PI * 2 * i / 64;
            GL11.glVertex3d(x + Math.cos(a) * rad, y, z + Math.sin(a) * rad);
        }
        GL11.glEnd();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    };
}
