import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.utils.render.DrawUtils;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

@ModuleInfo(name = "Breadcrumbs", description = "A fading line through your last few seconds of movement", category = ModuleCategory.RENDER)
public class Breadcrumbs extends Module {


    private final List<double[]> points = new ArrayList<>();
    private final List<Long> times = new ArrayList<>();
    private long lastPoint;

    @Override
    protected void onEnable() {
        points.clear();
        times.clear();
    }

    @Override
    protected void onDisable() {
        points.clear();
        times.clear();
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRender = event -> {
        long now = System.currentTimeMillis();

        if (now - lastPoint > 50) {
            points.add(new double[]{mc.thePlayer.posX, mc.thePlayer.posY + 0.1, mc.thePlayer.posZ});
            times.add(now);
            lastPoint = now;
        }

        while (points.size() > 500) {
            points.remove(0);
            times.remove(0);
        }

        long fadeMs = (long) (5 * 1000);
        while (!times.isEmpty() && now - times.get(0) > fadeMs) {
            points.remove(0);
            times.remove(0);
        }

        if (points.size() < 2) return;

        int themeColor = arsenic.main.Arsenic.getInstance().getThemeManager().getCurrentTheme().getMainColor();
        float r = ((themeColor >> 16) & 0xFF) / 255.0f;
        float g = ((themeColor >> 8) & 0xFF) / 255.0f;
        float b = (themeColor & 0xFF) / 255.0f;

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glLineWidth((float) 2f);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();

        int total = points.size();
        for (int i = 0; i < total - 1; i++) {
            double[] p1 = points.get(i);
            double[] p2 = points.get(i + 1);
            float alpha = (float) i / total;
            wr.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
            wr.pos(p1[0] - mc.getRenderManager().viewerPosX,
                    p1[1] - mc.getRenderManager().viewerPosY,
                    p1[2] - mc.getRenderManager().viewerPosZ).color(r, g, b, alpha * 0.8f).endVertex();
            wr.pos(p2[0] - mc.getRenderManager().viewerPosX,
                    p2[1] - mc.getRenderManager().viewerPosY,
                    p2[2] - mc.getRenderManager().viewerPosZ).color(r, g, b, alpha * 0.8f).endVertex();
            tessellator.draw();
        }

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_BLEND);
    };
}
