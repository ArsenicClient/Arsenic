package arsenic.module.impl.visual;

import arsenic.module.ModuleTier;
import arsenic.utils.java.MathUtils;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.injection.accessor.IMixinRenderManager;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.AntiBot;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.utils.java.JavaUtils;
import net.minecraft.client.Minecraft;
import arsenic.runtime.Access;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.opengl.GL11;
import arsenic.gui.design.M3;
import arsenic.gui.themes.ThemeManager;
import arsenic.utils.render.GlowRenderer;
import arsenic.utils.render.RenderUtils;

import java.awt.*;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


@ModuleInfo(name = "Esp", category = ModuleCategory.RENDER, hidden = true, tier = ModuleTier.VISUAL)
public class ESP extends Module {

    public enum Mode {
        Box,
        Glow,
        Chams
    }

    public final EnumProperty<Mode> mode = new EnumProperty<>("Mode", Mode.Box);
    public final BooleanProperty healthBar = new BooleanProperty("Health Bar", true);

    private static final int GLOW_RADIUS = 8;
    private static final float GLOW_STRENGTH = 3f;
    private static final boolean GLOW_OUTLINE_ONLY = true;
    private static final float GLOW_FILL = 0.15f;
    private static final float CHAMS_ALPHA = 0.6f;
    private static final boolean CHAMS_FLAT = true;

    private final GlowRenderer glowRenderer = new GlowRenderer();
    private final List<EntityPlayer> glowTargets = new ArrayList<>();
    // per player: screen x, top y, bottom y (window pixels), health ratio
    private final List<double[]> healthBars = new ArrayList<>();
    private static final Access.FieldRef ARI_MODELVIEW = Access.field(ActiveRenderInfo.class, "MODELVIEW");
    private static final Access.FieldRef ARI_PROJECTION = Access.field(ActiveRenderInfo.class, "PROJECTION");
    private final float[] modelview = new float[16];
    private final float[] projection = new float[16];

    @Override
    protected void onDisable() {
        glowRenderer.release();
        glowTargets.clear();
        healthBars.clear();
    }

    @EventLink
    public final Listener<EventRenderWorldLast> renderWorldLast = event -> {
        glowTargets.clear();
        healthBars.clear();
        captureMatrices();
        ICamera camera = new Frustum();
        for (EntityPlayer entity : Minecraft.getMinecraft().theWorld.playerEntities) {
            if (entity == mc.thePlayer)
                continue;
            if (AntiBot.isBot(entity))
                continue;
            IMixinRenderManager renderManager = (IMixinRenderManager) mc.getRenderManager();
            double x = (entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * event.partialTicks) - renderManager.getRenderPosX();
            double y = (entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * event.partialTicks) - renderManager.getRenderPosY();
            double z = (entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * event.partialTicks) - renderManager.getRenderPosZ();
            AxisAlignedBB axisalignedbb = entity.getEntityBoundingBox();
            AxisAlignedBB axisalignedbb1 = new AxisAlignedBB(axisalignedbb.minX - entity.posX + x, axisalignedbb.minY - entity.posY + y, axisalignedbb.minZ - entity.posZ + z, axisalignedbb.maxX - entity.posX + x, axisalignedbb.maxY - entity.posY + y, axisalignedbb.maxZ - entity.posZ + z);
            if (!camera.isBoundingBoxInFrustum(axisalignedbb1))
                continue;
            if (mode.getValue() == Mode.Glow)
                glowTargets.add(entity);
            if (mode.getValue() == Mode.Box) {
                Color color = new Color(resolveColour(entity));
                RenderUtils.beginWorldOverlay();
                GL11.glLineWidth(2.0F);
                RenderUtils.drawShadedBoundingBox(axisalignedbb1, color.getRed(), color.getGreen(), color.getBlue(), 63);
                RenderGlobal.drawOutlinedBoundingBox(axisalignedbb1, color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
                GL11.glLineWidth(1.0F);
                RenderUtils.endWorldOverlay();
            }
            if (healthBar.getValue())
                queueHealthBar(entity, x, y, z);
        }

        if (mode.getValue() == Mode.Glow && !glowTargets.isEmpty()) {
            Map<Integer, List<EntityPlayer>> byColour = new LinkedHashMap<>();
            for (EntityPlayer target : glowTargets)
                byColour.computeIfAbsent(getGlowColour(target), c -> new ArrayList<>()).add(target);

            for (Map.Entry<Integer, List<EntityPlayer>> group : byColour.entrySet())
                glowRenderer.render(group.getValue(), event.partialTicks, group.getKey(),
                        GLOW_RADIUS, GLOW_STRENGTH, GLOW_OUTLINE_ONLY, GLOW_FILL);
        }
        glowTargets.clear();
    };

    private int resolveColour(EntityPlayer entity) {
        if (entity != null && Arsenic.getArsenic().getFriendManager().isFriend(entity))
            return getFriendColour();
        if (entity != null)
            return getBedWarsColor(entity);
        return ThemeManager.getMainColor();
    }

    private static int getFriendColour() {
        int theme = ThemeManager.getMainColor();
        float[] hsb = Color.RGBtoHSB((theme >> 16) & 0xFF, (theme >> 8) & 0xFF, theme & 0xFF, null);
        return 0xFF000000 | Color.HSBtoRGB((hsb[0] + 0.5f) % 1f, Math.max(0.7f, hsb[1]), Math.max(0.85f, hsb[2]));
    }

    private int getGlowColour(EntityPlayer entity) {
        return resolveColour(entity);
    }

    public boolean isChamsEnabled() {
        return mode.getValue() == Mode.Chams;
    }

    public int getChamsColour(net.minecraft.entity.EntityLivingBase entity) {
        return resolveColour(entity instanceof EntityPlayer ? (EntityPlayer) entity : null);
    }

    public boolean isChamsFlat() {
        return CHAMS_FLAT;
    }

    public float getChamsAlpha() {
        return CHAMS_ALPHA;
    }

    /*
     * Shader packs take over the world pipeline, so anything drawn in world space at RenderWorldLast gets lit, fogged
     * and composited like terrain (the bar came out dark or vanished). The bar is projected to the screen here and
     * drawn in the 2D pass, which shaders leave alone.
     */
    private void queueHealthBar(EntityPlayer entity, double x, double y, double z) {
        double r = MathUtils.clamp(entity.getHealth() / entity.getMaxHealth(), 0.0, 1.0);
        double yaw = Math.toRadians(mc.getRenderManager().playerViewY);
        double side = 0.69D;
        double bx = x + Math.cos(yaw) * side, bz = z + Math.sin(yaw) * side;
        double[] bottom = project(bx, y - 0.2D, bz);
        double[] top = project(bx, y + 2.05D, bz);
        if (bottom == null || top == null)
            return;
        healthBars.add(new double[] { bottom[0], top[1], bottom[1], r });
    }

    @EventLink
    public final Listener<EventRender2D> render2D = event -> {
        if (healthBars.isEmpty())
            return;
        double scale = event.getSr().getScaleFactor();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        for (double[] bar : healthBars) {
            double cx = bar[0] / scale, top = bar[1] / scale, bottom = bar[2] / scale, r = bar[3];
            double height = bottom - top;
            if (height < 2)
                continue;
            double w = Math.max(1.5D, height * 4.0D / 76.0D), in = w / 4.0D;
            float rf = (float) r;
            int hc = rf >= 0.5f ? M3.primary() : (rf >= 0.25f ? M3.mix(M3.error(), M3.primary(), (rf - 0.25f) / 0.25f) : M3.error());
            double fillTop = bottom - in - (height - in * 2) * r;
            rect(cx - w / 2, top, cx + w / 2, bottom, M3.alpha(M3.surfaceContainerLowest(), 220));
            rect(cx - w / 2 + in, top + in, cx + w / 2 - in, fillTop, M3.alpha(M3.surfaceContainerHighest(), 210));
            rect(cx - w / 2 + in, fillTop, cx + w / 2 - in, bottom - in, hc);
        }
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
        healthBars.clear();
    };

    private static void rect(double x1, double y1, double x2, double y2, int colour) {
        if (y2 <= y1 || x2 <= x1)
            return;
        Tessellator ts = Tessellator.getInstance();
        WorldRenderer wr = ts.getWorldRenderer();
        int a = colour >>> 24, r = (colour >> 16) & 0xFF, g = (colour >> 8) & 0xFF, b = colour & 0xFF;
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(x1, y2, 0).color(r, g, b, a).endVertex();
        wr.pos(x2, y2, 0).color(r, g, b, a).endVertex();
        wr.pos(x2, y1, 0).color(r, g, b, a).endVertex();
        wr.pos(x1, y1, 0).color(r, g, b, a).endVertex();
        ts.draw();
    }

    /*
     * The camera matrices as ActiveRenderInfo saved them right after setupCameraTransform. The live GL matrices at
     * RenderWorldLast can't be trusted: shader packs swap them (shadow pass, composites) and render at their own
     * resolution, which left the bars floating away from the players.
     */
    private void captureMatrices() {
        FloatBuffer mv = ARI_MODELVIEW.get(null), pr = ARI_PROJECTION.get(null);
        for (int i = 0; i < 16; i++) {
            modelview[i] = mv.get(i);
            projection[i] = pr.get(i);
        }
    }

    /** Camera-relative position to window pixels (top-left origin), or null when behind the camera. */
    private double[] project(double x, double y, double z) {
        double[] eye = mul(modelview, x, y, z, 1);
        double[] clip = mul(projection, eye[0], eye[1], eye[2], eye[3]);
        if (clip[3] <= 0.01D)
            return null;
        double sx = (clip[0] / clip[3] + 1) / 2 * mc.displayWidth;
        double sy = (1 - clip[1] / clip[3]) / 2 * mc.displayHeight;
        return new double[] { sx, sy };
    }

    // column-major, as GL stores it
    private static double[] mul(float[] m, double x, double y, double z, double w) {
        return new double[] {
                m[0] * x + m[4] * y + m[8] * z + m[12] * w,
                m[1] * x + m[5] * y + m[9] * z + m[13] * w,
                m[2] * x + m[6] * y + m[10] * z + m[14] * w,
                m[3] * x + m[7] * y + m[11] * z + m[15] * w };
    }


    public int getBedWarsColor(EntityPlayer entityPlayer) {
        ItemStack stack = entityPlayer.getCurrentArmor(2);
        if (stack == null)
            return ThemeManager.getMainColor();
        NBTTagCompound nbttagcompound = stack.getTagCompound();
        if (nbttagcompound != null) {
            NBTTagCompound nbttagcompound1 = nbttagcompound.getCompoundTag("display");
            if (nbttagcompound1 != null && nbttagcompound1.hasKey("color", 3)) {
                return nbttagcompound1.getInteger("color");
            }
        }

        return ThemeManager.getMainColor();
    }


}
