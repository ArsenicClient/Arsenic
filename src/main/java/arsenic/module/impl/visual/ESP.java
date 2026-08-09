package arsenic.module.impl.visual;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.injection.accessor.IMixinRenderManager;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.AntiBot;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.ColourProperty;
import arsenic.module.property.impl.FolderProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
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


@ModuleInfo(name = "Esp", category = ModuleCategory.WORLD, hidden = true)
public class ESP extends Module {

    public ColourProperty color = new ColourProperty("Color:", 0xFF2ECC71);
    public BooleanProperty bedWars = new BooleanProperty("BedWars", false);
    public BooleanProperty shaderEsp = new BooleanProperty("Shader ESP", true);
    public BooleanProperty boxEsp = new BooleanProperty("Box ESP", true);
    public BooleanProperty healthEsp = new BooleanProperty("Health ESP", false);

    // glow: a soft coloured halo around the player model that shows through walls
    private final BooleanProperty glow = new BooleanProperty("Glow", false);
    private final DoubleProperty glowRadius = new DoubleProperty("Glow Radius", new DoubleValue(1, 30, 8, 1));
    private final DoubleProperty glowStrength = new DoubleProperty("Glow Strength", new DoubleValue(0.5, 8, 3, 0.5));
    private final BooleanProperty glowOutlineOnly = new BooleanProperty("Glow Outline Only", true);
    private final DoubleProperty glowFill = new DoubleProperty("Glow Fill", new DoubleValue(0, 100, 15, 5));
    private final BooleanProperty glowTheme = new BooleanProperty("Glow Theme Colour", true);
    public final FolderProperty glowFolder =
            new FolderProperty("Glow ESP", glow, glowRadius, glowStrength, glowOutlineOnly, glowFill, glowTheme);

    // chams: flat coloured models. No framebuffers or shaders involved, so it is unaffected by
    // whatever a shaderpack is doing with render targets.
    private final BooleanProperty chams = new BooleanProperty("Chams", false);
    private final DoubleProperty chamsAlpha = new DoubleProperty("Chams Opacity", new DoubleValue(5, 100, 60, 5));
    private final BooleanProperty chamsFlat = new BooleanProperty("Chams Flat Colour", true);
    private final BooleanProperty chamsTheme = new BooleanProperty("Chams Theme Colour", true);
    public final FolderProperty chamsFolder =
            new FolderProperty("Chams", chams, chamsFlat, chamsAlpha, chamsTheme);

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
            if (glow.getValue())
                glowTargets.add(entity);
            Color color = new Color(bedWars.getValue() ? getBedWarsColor(entity) : this.color.getValue());
            GlStateManager.pushMatrix();
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glDepthMask(false);
            GL11.glLineWidth(2.0F);
            if (shaderEsp.getValue()) {
                RenderUtils.drawShadedBoundingBox(axisalignedbb1, color.getRed(), color.getGreen(), color.getBlue(), 63);
            }
            if (boxEsp.getValue()) {
                RenderGlobal.drawOutlinedBoundingBox(axisalignedbb1, color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
            }
            if (healthEsp.getValue()) {
                GL11.glPushMatrix();
                drawHealthEsp(entity, x, y, z);
                GL11.glPopMatrix();
            }
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDepthMask(true);
            GL11.glLineWidth(1.0F);
            GlStateManager.popMatrix();
        }

        // The glow composites the whole screen, so it runs once per distinct colour rather than once
        // per player - normally that is a single pass, but in BedWars each team's colour needs its
        // own mask or every player would glow whichever colour happened to be first in the list.
        if (glow.getValue() && !glowTargets.isEmpty()) {
            Map<Integer, List<EntityPlayer>> byColour = new LinkedHashMap<>();
            for (EntityPlayer target : glowTargets)
                byColour.computeIfAbsent(getGlowColour(target), c -> new ArrayList<>()).add(target);

            for (Map.Entry<Integer, List<EntityPlayer>> group : byColour.entrySet())
                glowRenderer.render(group.getValue(), event.partialTicks, group.getKey(),
                        (int) glowRadius.getValue().getInput(),
                        (float) glowStrength.getValue().getInput(),
                        glowOutlineOnly.getValue(),
                        (float) glowFill.getValue().getInput() / 100f);
        }
        glowTargets.clear();
    };

    /**
     * BedWars team colour wins when it is on, otherwise the client theme unless the user has opted
     * out, in which case the module's own colour is used.
     */
    private int resolveColour(EntityPlayer entity, boolean useTheme) {
        if (bedWars.getValue() && entity != null)
            return getBedWarsColor(entity);
        return useTheme ? ThemeManager.getMainColor() : color.getValue();
    }

    private int getGlowColour(EntityPlayer entity) {
        return resolveColour(entity, glowTheme.getValue());
    }

    // read by ChamsRenderer from inside RendererLivingEntity#renderModel
    public boolean isChamsEnabled() {
        return chams.getValue();
    }

    public int getChamsColour(net.minecraft.entity.EntityLivingBase entity) {
        return resolveColour(entity instanceof EntityPlayer ? (EntityPlayer) entity : null,
                chamsTheme.getValue());
    }

    public boolean isChamsFlat() {
        return chamsFlat.getValue();
    }

    public float getChamsAlpha() {
        return (float) chamsAlpha.getValue().getInput() / 100f;
    }

    private void drawHealthEsp(EntityPlayer entity, double x, double y, double z) {
        if (!(entity instanceof EntityLivingBase)) return;
        EntityLivingBase en = (EntityLivingBase) entity;
        double r = JavaUtils.limit(en.getHealth() / en.getMaxHealth(), 0, 1);
        int b = (int) (74.0D * r);
        int hc = r < 0.3D ? Color.red.getRGB() : (r < 0.5D ? Color.orange.getRGB() : (r < 0.7D ? Color.yellow.getRGB() : Color.green.getRGB()));

        GlStateManager.pushMatrix();
        GL11.glTranslated(x, y - 0.2D, z);
        GL11.glRotated(-mc.getRenderManager().playerViewY, 0.0D, 1.0D, 0.0D);
        GlStateManager.disableDepth();
        GL11.glScalef(0.03F, 0.03F, 0.03F); // Removed 'd' from scale, assuming 'd' was a variable from original context not available here.
        int i = 21; // Assuming 'shift' was also a context variable, using a fixed value for 'i'
        net.minecraft.client.gui.Gui.drawRect(i, -1, i + 4, 75, Color.black.getRGB());
        net.minecraft.client.gui.Gui.drawRect(i + 1, b, i + 3, 74, Color.darkGray.getRGB());
        net.minecraft.client.gui.Gui.drawRect(i + 1, 0, i + 3, b, hc);
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }

    public int getBedWarsColor(EntityPlayer entityPlayer) {
        ItemStack stack = entityPlayer.getCurrentArmor(2);
        if (stack == null)
            return color.getValue(); // not wearing a chest plate
        NBTTagCompound nbttagcompound = stack.getTagCompound();
        if (nbttagcompound != null) {
            NBTTagCompound nbttagcompound1 = nbttagcompound.getCompoundTag("display");
            if (nbttagcompound1 != null && nbttagcompound1.hasKey("color", 3)) {
                return nbttagcompound1.getInteger("color");
            }
        }

        return color.getValue();
    }


}
