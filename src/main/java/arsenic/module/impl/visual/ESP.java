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
import arsenic.utils.render.RenderUtils;

import java.awt.*;


@ModuleInfo(name = "Esp", category = ModuleCategory.WORLD)
public class ESP extends Module {

    public ColourProperty color = new ColourProperty("Color:", 0xFF2ECC71);
    public BooleanProperty bedWars = new BooleanProperty("BedWars", false);
    public BooleanProperty shaderEsp = new BooleanProperty("Shader ESP", true);
    public BooleanProperty boxEsp = new BooleanProperty("Box ESP", true);
    public BooleanProperty healthEsp = new BooleanProperty("Health ESP", false);

    @EventLink
    public final Listener<EventRenderWorldLast> renderWorldLast = event -> {
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
    };

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
