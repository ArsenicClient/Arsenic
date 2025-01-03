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
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.culling.ICamera;
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

    @EventLink
    public final Listener<EventRenderWorldLast> renderWorldLast = event -> {
        ICamera camera = new Frustum();
        for (EntityPlayer entity : Minecraft.getMinecraft().theWorld.playerEntities) {
            if (entity == mc.thePlayer)
                continue;
            if (Arsenic.getArsenic().getModuleManager().getModuleByClass(AntiBot.class).isBot(entity))
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
            RenderUtils.drawShadedBoundingBox(axisalignedbb1, color.getRed(), color.getGreen(), color.getBlue(), 63);
            RenderGlobal.drawOutlinedBoundingBox(axisalignedbb1, color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDepthMask(true);
            GL11.glLineWidth(1.0F);
            GlStateManager.popMatrix();
        }
    };

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
