package arsenic.module.impl.visual;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.ColourProperty;
import arsenic.utils.render.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.Sys;
import org.lwjgl.opengl.GL11;

@ModuleInfo(name = "Esp", category = ModuleCategory.WORLD)
public class ESP extends Module {

    public ColourProperty color = new ColourProperty("Color:", 0xFF2ECC71);

    @EventLink
    public final Listener<EventRenderWorldLast> renderWorldLast = event -> {
        for(Entity entity : Minecraft.getMinecraft().theWorld.loadedEntityList) {
            double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * event.partialTicks;
            double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * event.partialTicks;
            double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * event.partialTicks;
            GlStateManager.pushMatrix();
            AxisAlignedBB axisalignedbb = entity.getEntityBoundingBox();
            AxisAlignedBB axisalignedbb1 = new AxisAlignedBB(axisalignedbb.minX - entity.posX + x, axisalignedbb.minY - entity.posY + y, axisalignedbb.minZ - entity.posZ + z, axisalignedbb.maxX - entity.posX + x, axisalignedbb.maxY - entity.posY + y, axisalignedbb.maxZ - entity.posZ + z);
            GL11.glBlendFunc(770, 771);
            GL11.glEnable(3042);
            GL11.glDisable(3553);
            GL11.glDisable(2929);
            GL11.glDepthMask(false);
            GL11.glLineWidth(2.0F);
            //RenderGlobal.drawSelectionBoundingBox(axisalignedbb1);
            RenderGlobal.drawOutlinedBoundingBox(axisalignedbb1, color.getColor(1), color.getColor(2), color.getColor(3), color.getColor(0));
            GL11.glEnable(3553);
            GL11.glEnable(2929);
            GL11.glDepthMask(true);
            GL11.glDisable(3042);
            GlStateManager.popMatrix();
        }
    };
}
