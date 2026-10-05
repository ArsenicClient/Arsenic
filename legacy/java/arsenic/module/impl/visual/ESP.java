package arsenic.module.impl.visual;

import arsenic.utils.java.MathUtils;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.injection.accessor.IMixinRenderManager;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.AntiBot;
import arsenic.module.property.impl.EnumProperty;
import arsenic.utils.java.JavaUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.opengl.GL11;
import arsenic.gui.themes.ThemeManager;
import arsenic.utils.render.GlowRenderer;
import arsenic.utils.render.RenderUtils;

import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


@ModuleInfo(name = "Esp", category = ModuleCategory.RENDER, hidden = true)
public class ESP extends Module {

    public enum Mode {
        Box,
        Glow,
        Chams
    }

    public final EnumProperty<Mode> mode = new EnumProperty<>("Mode", Mode.Box);

    private static final int GLOW_RADIUS = 8;
    private static final float GLOW_STRENGTH = 3f;
    private static final boolean GLOW_OUTLINE_ONLY = true;
    private static final float GLOW_FILL = 0.15f;
    private static final float CHAMS_ALPHA = 0.6f;
    private static final boolean CHAMS_FLAT = true;

    private final GlowRenderer glowRenderer = new GlowRenderer();
    private final List<EntityPlayer> glowTargets = new ArrayList<>();

    @Override
    protected void onDisable() {
        glowRenderer.release();
        glowTargets.clear();
    }

    @EventLink
    public final Listener<EventRenderWorldLast> renderWorldLast = event -> {
        glowTargets.clear();
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
            Color color = new Color(resolveColour(entity));
            GlStateManager.pushMatrix();
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glDepthMask(false);
            GL11.glLineWidth(2.0F);
            if (mode.getValue() == Mode.Box) {
                RenderUtils.drawShadedBoundingBox(axisalignedbb1, color.getRed(), color.getGreen(), color.getBlue(), 63);
                RenderGlobal.drawOutlinedBoundingBox(axisalignedbb1, color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
            }
            GL11.glPushMatrix();
            drawHealthEsp(entity, x, y, z);
            GL11.glPopMatrix();
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDepthMask(true);
            GL11.glLineWidth(1.0F);
            GlStateManager.popMatrix();
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

    private void drawHealthEsp(EntityPlayer entity, double x, double y, double z) {
        if (!(entity instanceof EntityLivingBase)) return;
        EntityLivingBase en = (EntityLivingBase) entity;
        double r = MathUtils.clamp(en.getHealth() / en.getMaxHealth(), 0.0, 1.0);
        int b = (int) (74.0D * r);
        int hc = r < 0.3D ? Color.red.getRGB() : (r < 0.5D ? Color.orange.getRGB() : (r < 0.7D ? Color.yellow.getRGB() : Color.green.getRGB()));

        GlStateManager.pushMatrix();
        GL11.glTranslated(x, y - 0.2D, z);
        GL11.glRotated(-mc.getRenderManager().playerViewY, 0.0D, 1.0D, 0.0D);
        GlStateManager.disableDepth();
        GL11.glScalef(0.03F, 0.03F, 0.03F);
        int i = 21;
        net.minecraft.client.gui.Gui.drawRect(i, -1, i + 4, 75, Color.black.getRGB());
        net.minecraft.client.gui.Gui.drawRect(i + 1, b, i + 3, 74, Color.darkGray.getRGB());
        net.minecraft.client.gui.Gui.drawRect(i + 1, 0, i + 3, b, hc);
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
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
