package arsenic.module.impl.visual;

import arsenic.gui.themes.ThemeManager;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.AntiBot;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.BufferUtils;

import java.awt.*;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

@ModuleInfo(name = "Tracers", category = ModuleCategory.RENDER, hidden = true)
public class Tracers extends Module {

    private final FloatBuffer modelView = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer projection = BufferUtils.createFloatBuffer(16);
    private final IntBuffer viewport = BufferUtils.createIntBuffer(16);
    private final FloatBuffer screenCoords = BufferUtils.createFloatBuffer(3);

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> renderListener = event -> {

        for (Player player : Minecraft.getInstance().level.playerEntities) {
            if (player == mc.player) continue;
            if (AntiBot.isBot(player)) continue;

            double x = (player.xo + (player.getX() - player.xo) * event.partialTicks)
                    - mc.getRenderManager().viewerPosX;
            double y = (player.yo + (player.getY() - player.yo) * event.partialTicks)
                    - mc.getRenderManager().viewerPosY;
            double z = (player.zo + (player.getZ() - player.zo) * event.partialTicks)
                    - mc.getRenderManager().viewerPosZ;

            if (false && isOnScreen(x, y + player.height / 2, z)) continue;

            Color c = new Color(getBedWarsColor(player), true);


            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex3d(0, mc.player.getEyeHeight(), 0);
            GL11.glVertex3d(x, y + player.height / 2, z);
            GL11.glEnd();

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

    private int getBedWarsColor(Player player) {
        if (player.getCurrentArmor(2) != null) {
            net.minecraft.nbt.NBTTagCompound tag = player.getCurrentArmor(2).getTagCompound();
            if (tag != null) {
                net.minecraft.nbt.NBTTagCompound display = tag.getCompoundTag("display");
                if (display != null && display.hasKey("color", 3)) {
                    return display.getInteger("color");
                }
            }
        }
        return ThemeManager.getMainColor();
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
