package arsenic.module.impl.visual;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.gui.design.M3;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.render.RenderUtils;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

@ModuleInfo(name = "Ambient", category = ModuleCategory.RENDER, hidden = true, tier = ModuleTier.VISUAL,
        description = "Soft themed motes floating around the player")
public class AmbientParticles extends Module {

    public final DoubleProperty density = new DoubleProperty("Density", new DoubleValue(6, 80, 32, 2));
    public final DoubleProperty radius = new DoubleProperty("Radius", new DoubleValue(3, 18, 9, 0.5));
    public final DoubleProperty speed = new DoubleProperty("Speed", new DoubleValue(0.1, 2, 0.6, 0.1));
    public final DoubleProperty height = new DoubleProperty("Height", new DoubleValue(0.5, 8, 3.5, 0.5));
    public final BooleanProperty twoTone = new BooleanProperty("Two Tone", true);
    public final BooleanProperty additive = new BooleanProperty("Additive", true);

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRenderWorldLast = event -> {
        int count = (int) density.getValue().getInput();
        if (count <= 0 || mc.theWorld == null || mc.thePlayer == null)
            return;

        double px = (mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * event.partialTicks)
                - mc.getRenderManager().viewerPosX;
        double py = (mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * event.partialTicks)
                - mc.getRenderManager().viewerPosY;
        double pz = (mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * event.partialTicks)
                - mc.getRenderManager().viewerPosZ;

        float time = (mc.theWorld.getTotalWorldTime() + event.partialTicks) / 20f;
        float radiusMax = (float) radius.getValue().getInput();
        float heightMax = (float) height.getValue().getInput();
        float speedValue = (float) speed.getValue().getInput();
        float yaw = (float) Math.toRadians(mc.getRenderManager().playerViewY);
        float rightX = (float) Math.cos(yaw), rightZ = (float) Math.sin(yaw);
        int primary = M3.primary();
        int secondary = twoTone.getValue() ? M3.tertiary() : primary;

        RenderUtils.beginWorldOverlay();
        if (additive.getValue())
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, 1, 0);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);

        for (int i = 0; i < count; i++) {
            float seed = fract((float) Math.sin(i * 12.9898f + 4.1414f) * 43758.5453f);
            float seed2 = fract((float) Math.sin(i * 78.233f + 1.337f) * 24634.6345f);
            float angle = i * 2.399963f + time * speedValue * (0.45f + seed * 0.55f);
            float r = radiusMax * (0.35f + 0.65f * seed2);
            float bob = (float) Math.sin(time * (0.7f + seed * 0.5f) * speedValue + seed * 6.283f);
            float y = (float) py + 0.15f + (bob * 0.5f + 0.5f) * heightMax;
            float x = (float) px + (float) Math.cos(angle) * r;
            float z = (float) pz + (float) Math.sin(angle) * r;

            float pulse = 0.55f + 0.45f * (float) Math.sin(time * 1.3f * speedValue + seed2 * 6.283f);
            int rgb = (seed2 > 0.72f ? secondary : primary) & 0x00FFFFFF;
            int a = (int) (18 + 54 * pulse);
            float size = 0.035f + seed * 0.055f;

            vertex(wr, x, y, z, rightX, rightZ, size, rgb, a);
        }
        tessellator.draw();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        RenderUtils.endWorldOverlay();
    };

    private static void vertex(WorldRenderer wr, float x, float y, float z,
                               float rightX, float rightZ, float size, int rgb, int alpha) {
        float r = (rgb >> 16 & 255) / 255f;
        float g = (rgb >> 8 & 255) / 255f;
        float b = (rgb & 255) / 255f;
        float rx = rightX * size, rz = rightZ * size;
        wr.pos(x - rx, y - size, z - rz).color(r, g, b, alpha / 255f).endVertex();
        wr.pos(x - rx, y + size, z - rz).color(r, g, b, alpha / 255f).endVertex();
        wr.pos(x + rx, y + size, z + rz).color(r, g, b, alpha / 255f).endVertex();
        wr.pos(x + rx, y - size, z + rz).color(r, g, b, alpha / 255f).endVertex();
    }

    private static float fract(float value) {
        return value - (float) Math.floor(value);
    }
}
