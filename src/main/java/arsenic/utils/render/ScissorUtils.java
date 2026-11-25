package arsenic.utils.render;

import arsenic.utils.java.UtilityClass;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

public class ScissorUtils extends UtilityClass {

    private static final List<int[]> rahhh = new ArrayList<>();

    static {
        resetScissor();
    }
    public static void subScissor(int x, int y, int width, int height) {
        subScissor(x, y, width, height, new ScaledResolution(mc).getScaleFactor());
    }

    /** While a {@link GuiCanvas} screen is drawing, the coordinates are canvas units and {@code scale} is ignored. */
    public static void subScissor(int x1, int y1, int x2, int y2, int scale) {
        GL11.glPushAttrib(GL11.GL_SCISSOR_BIT);
        int[] parent = rahhh.get(rahhh.size() - 1);
        x1 = Math.max(x1, parent[0]);
        y1 = Math.max(y1, parent[1]);
        x2 = Math.min(x2, parent[2]);
        y2 = Math.min(y2, parent[3]);
        if (x2 <= x1 || y2 <= y1) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(0, 0, 0, 0);
            rahhh.add(new int[] {x1, y1, x1, y1});
            return;
        }
        float s = GuiCanvas.isActive() ? GuiCanvas.scale() : scale;
        float ox = GuiCanvas.isActive() ? GuiCanvas.offsetX() : 0f;
        float oy = GuiCanvas.isActive() ? GuiCanvas.offsetY() : 0f;
        int px1 = (int) (x1 * s + ox), px2 = (int) Math.ceil(x2 * s + ox);
        int py1 = (int) (mc.displayHeight - (y2 * s + oy)), py2 = (int) Math.ceil(mc.displayHeight - (y1 * s + oy));
        // clip to the window first, so a box partly off-screen keeps its real visible size
        px1 = Math.max(px1, 0);
        py1 = Math.max(py1, 0);
 int[] rah = new int[] {px1, py1, Math.max(0, px2 - px1), Math.max(0, py2 - py1)};
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(rah[0], rah[1], rah[2], rah[3]);
        rahhh.add(new int[] {x1,y1,x2,y2});
    }

    public static void endSubScissor() {
        if (rahhh.size() <= 1)
            return;
        rahhh.remove(rahhh.size() - 1);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glPopAttrib();
    }

    public static void resetScissor() {
        rahhh.clear();
        // negative coordinates are valid on a GuiCanvas screen (the bars left/above the canvas)
        rahhh.add(new int[] {-1000000, -1000000, Integer.MAX_VALUE, Integer.MAX_VALUE});
    }


}
