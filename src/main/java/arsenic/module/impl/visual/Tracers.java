package arsenic.module.impl.visual;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.AntiBot;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.ColourProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

@ModuleInfo(name = "Tracers", category = ModuleCategory.WORLD, hidden = true)
public class Tracers extends Module {

    public final ColourProperty color = new ColourProperty("Color:", 0xFF2ECC71);
    public final BooleanProperty bedWars = new BooleanProperty("BedWars", false);
    public final BooleanProperty offScreenOnly = new BooleanProperty("Off Screen Only", false);

    private final FloatBuffer modelView = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer projection = BufferUtils.createFloatBuffer(16);
    private final IntBuffer viewport = BufferUtils.createIntBuffer(16);
    private final FloatBuffer screenCoords = BufferUtils.createFloatBuffer(3);

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> renderListener = event -> {
        if (offScreenOnly.getValue()) {
            modelView.clear();
            projection.clear();
            viewport.clear();
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelView);
            GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
            GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        }

        for (EntityPlayer player : Minecraft.getMinecraft().theWorld.playerEntities) {
            if (player == mc.thePlayer) continue;
            if (AntiBot.isBot(player)) continue;

            double x = (player.lastTickPosX + (player.posX - player.lastTickPosX) * event.partialTicks)
                    - mc.getRenderManager().viewerPosX;
            double y = (player.lastTickPosY + (player.posY - player.lastTickPosY) * event.partialTicks)
                    - mc.getRenderManager().viewerPosY;
            double z = (player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * event.partialTicks)
                    - mc.getRenderManager().viewerPosZ;

            if (offScreenOnly.getValue() && isOnScreen(x, y + player.height / 2, z)) continue;

            Color c = new Color(bedWars.getValue() ? getBedWarsColor(player) : color.getValue());

            GlStateManager.pushMatrix();
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glDepthMask(false);
            GL11.glLineWidth(2.0F);

            GL11.glBegin(GL11.GL_LINES);
            GL11.glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, c.getAlpha() / 255f);
            GL11.glVertex3d(0, mc.thePlayer.getEyeHeight(), 0);
            GL11.glVertex3d(x, y + player.height / 2, z);
            GL11.glEnd();

            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDepthMask(true);
            GL11.glLineWidth(1.0F);
            GlStateManager.popMatrix();
        }
    };

    /** Projects a viewer-relative point to the screen and checks whether it lands inside the viewport. */
    private boolean isOnScreen(double x, double y, double z) {
        screenCoords.clear();
        if (!project((float) x, (float) y, (float) z, modelView, projection, viewport, screenCoords))
            return false;
        float winX = screenCoords.get(0);
        float winY = screenCoords.get(1);
        float winZ = screenCoords.get(2);
        if (winZ < 0f || winZ > 1f) return false; // behind the camera
        int vx = viewport.get(0);
        int vy = viewport.get(1);
        int vw = viewport.get(2);
        int vh = viewport.get(3);
        return winX >= vx && winX <= vx + vw && winY >= vy && winY <= vy + vh;
    }

    private int getBedWarsColor(EntityPlayer player) {
        if (player.getCurrentArmor(2) != null) {
            net.minecraft.nbt.NBTTagCompound tag = player.getCurrentArmor(2).getTagCompound();
            if (tag != null) {
                net.minecraft.nbt.NBTTagCompound display = tag.getCompoundTag("display");
                if (display != null && display.hasKey("color", 3)) {
                    return display.getInteger("color");
                }
            }
        }
        return color.getValue();
    }

    /**
     * Projects an object-space point to window coordinates. Drop-in replacement for
     * GLU.gluProject (from the lwjgl_util library, which LabyMod does not ship on the
     * classpath) using identical math, so behaviour is unchanged.
     */
    private static boolean project(float objX, float objY, float objZ,
                                   FloatBuffer model, FloatBuffer proj, IntBuffer view,
                                   FloatBuffer winPos) {
        float[] in = {objX, objY, objZ, 1.0f};
        float[] out = new float[4];
        multMatrixVec(model, in, out); // -> eye space
        multMatrixVec(proj, out, in);  // -> clip space
        if (in[3] == 0.0f) return false;
        in[3] = (1.0f / in[3]) * 0.5f;
        in[0] = in[0] * in[3] + 0.5f;  // -> normalized device coords in [0,1]
        in[1] = in[1] * in[3] + 0.5f;
        in[2] = in[2] * in[3] + 0.5f;
        winPos.put(0, in[0] * view.get(2) + view.get(0));
        winPos.put(1, in[1] * view.get(3) + view.get(1));
        winPos.put(2, in[2]);
        return true;
    }

    /** Column-major 4x4 matrix times a 4-vector: out = m * in. */
    private static void multMatrixVec(FloatBuffer m, float[] in, float[] out) {
        for (int i = 0; i < 4; i++) {
            out[i] = in[0] * m.get(i)
                    + in[1] * m.get(4 + i)
                    + in[2] * m.get(8 + i)
                    + in[3] * m.get(12 + i);
        }
    }
}
