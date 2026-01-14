package arsenic.module.impl.visual;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.AntiBot;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.ColourProperty;
import arsenic.utils.render.RenderUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.Color;

@ModuleInfo(name = "Trajectories", category = ModuleCategory.WORLD, hidden = true)
public class Trajectories extends Module {
    public final BooleanProperty ghostBow = new BooleanProperty("Ghost Bow Charge", true);
    public final ColourProperty trajectoryColor = new ColourProperty("Color:", new Color(255, 255, 255).hashCode());
    public final ColourProperty targetColor = new ColourProperty("Target Color:", new Color(255, 0, 0).hashCode());

    @EventLink
    public final Listener<EventRenderWorldLast> renderWorldLast = event -> {
        if (mc.thePlayer.getHeldItem() == null || !(mc.thePlayer.getHeldItem().getItem() instanceof ItemBow)) {
            return;
        }

        ItemStack heldItem = mc.thePlayer.getHeldItem();
        if (!(heldItem.getItem() instanceof ItemBow) && !(heldItem.getItem() instanceof ItemSnowball) && !(heldItem.getItem() instanceof ItemEgg) && !(heldItem.getItem() instanceof ItemEnderPearl)) {
            return;
        }
        if (heldItem.getItem() instanceof ItemBow && !mc.thePlayer.isUsingItem() && ghostBow.getValue()) {
            return;
        }
        boolean bow = false;
        if (heldItem.getItem() instanceof ItemBow) {
            bow = true;
        }

        float playerYaw = mc.thePlayer.rotationYaw;
        float playerPitch = mc.thePlayer.rotationPitch;

        double posX = mc.getRenderManager().viewerPosX - (double) (MathHelper.cos(playerYaw / 180.0f * (float) Math.PI) * 0.16f);
        double posY = mc.getRenderManager().viewerPosY + (double) mc.thePlayer.getEyeHeight() - (double) 0.1f;
        double posZ = mc.getRenderManager().viewerPosZ - (double) (MathHelper.sin(playerYaw / 180.0f * (float) Math.PI) * 0.16f);

        double motionX = (double) (-MathHelper.sin(playerYaw / 180.0f * (float) Math.PI) * MathHelper.cos(playerPitch / 180.0f * (float) Math.PI)) * (bow ? 1.0 : 0.4);
        double motionY = (double) (-MathHelper.sin(playerPitch / 180.0f * (float) Math.PI)) * (bow ? 1.0 : 0.4);
        double motionZ = (double) (MathHelper.cos(playerYaw / 180.0f * (float) Math.PI) * MathHelper.cos(playerPitch / 180.0f * (float) Math.PI)) * (bow ? 1.0 : 0.4);
        int itemInUse = 40;
        if (mc.thePlayer.getItemInUseCount() > 0 && bow) {
            itemInUse = mc.thePlayer.getItemInUseCount();
        }
        int n10 = 72000 - itemInUse;
        float f10 = (float) n10 / 20.0f;
        if ((double) (f10 = (f10 * f10 + f10 * 2.0f) / 3.0f) < 0.1) {
            return;
        }
        if (f10 > 1.0f) {
            f10 = 1.0f;
        }
        RenderUtils.setColor(trajectoryColor.getValue());
        GL11.glPushMatrix();
        boolean bl3 = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean bl4 = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean bl5 = GL11.glIsEnabled(GL11.GL_BLEND);
        if (bl3) {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        }
        if (bl4) {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
        }
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glBlendFunc(770, 771);
        if (!bl5) {
            GL11.glEnable(GL11.GL_BLEND);
        }
        float f11 = MathHelper.sqrt_double(motionX * motionX + motionY * motionY + motionZ * motionZ);
        motionX /= f11;
        motionY /= f11;
        motionZ /= f11;
        motionX *= (double) (bow ? f10 * 2.0f : 1.0f) * 1.5;
        motionY *= (double) (bow ? f10 * 2.0f : 1.0f) * 1.5;
        motionZ *= (double) (bow ? f10 * 2.0f : 1.0f) * 1.5;
        GL11.glLineWidth(1.5f);
        GL11.glBegin(3);
        boolean ground = false;
        MovingObjectPosition target = null;
        boolean highlight = false;
        double[] transform = new double[]{posX, posY, posZ, motionX, motionY, motionZ};
        for (int k = 0; k <= 100 && !ground; ++k) {
            Vec3 start = new Vec3(transform[0], transform[1], transform[2]);
            Vec3 predicted = new Vec3(transform[0] + transform[3], transform[1] + transform[4], transform[2] + transform[5]);
            MovingObjectPosition rayTraced = mc.theWorld.rayTraceBlocks(start, predicted, false, true, false);
            if (rayTraced == null) {
                rayTraced = getEntityHit(start, predicted);
                if (rayTraced != null) {
                    highlight = true;
                    break;
                }
                float f14 = 0.99f;
                transform[4] *= f14;
                transform[0] += (transform[3] *= f14);
                transform[1] += (transform[4] -= bow ? 0.05 : 0.03);
                transform[2] += (transform[5] *= f14);
            }
        }

        for (int k = 0; k <= 100 && !ground; ++k) {
            Vec3 start = new Vec3(posX, posY, posZ);
            Vec3 predicted = new Vec3(posX + motionX, posY + motionY, posZ + motionZ);
            MovingObjectPosition rayTraced = mc.theWorld.rayTraceBlocks(start, predicted, false, true, false);
            if (rayTraced != null) {
                ground = true;
                target = rayTraced;
            } else {
                MovingObjectPosition entityHit = getEntityHit(start, predicted);
                if (entityHit != null) {
                    target = entityHit;
                    ground = true;
                }
            }
            if (highlight) {
                RenderUtils.setColor(targetColor.getValue());
            }

            float f14 = 0.99f;
            motionY *= f14;
            GL11.glVertex3d((posX += (motionX *= f14)) - mc.getRenderManager().viewerPosX, (posY += (motionY -= bow ? 0.05 : 0.03)) - mc.getRenderManager().viewerPosY, (posZ += (motionZ *= f14)) - mc.getRenderManager().viewerPosZ);
        }
        GL11.glEnd();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glTranslated(posX - mc.getRenderManager().viewerPosX, posY - mc.getRenderManager().viewerPosY, posZ - mc.getRenderManager().viewerPosZ);
        if (target != null && target.sideHit != null) {
            switch (target.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK ? target.sideHit.getIndex() : target.sideHit.getIndex()) {
                case 2:
                case 3: {
                    GL11.glRotatef(90.0f, 1.0f, 0.0f, 0.0f);
                    break;
                }
                case 4:
                case 5: {
                    GL11.glRotatef(90.0f, 0.0f, 0.0f, 1.0f);
                    break;
                }
            }
        }
        double distance = Math.max(mc.thePlayer.getDistance(posX + motionX, posY + motionY, posZ + motionZ) * 0.042830285, 1);
        GL11.glScaled(distance, distance, distance);
        this.drawX();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        if (bl3) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        }
        if (bl4) {
            GL11.glEnable(GL11.GL_TEXTURE_2D );
        }
        if (!bl5) {
            GL11.glDisable(GL11.GL_BLEND);
        }
        GL11.glPopMatrix();
    };

    public MovingObjectPosition getEntityHit(Vec3 origin, Vec3 destination) {
        for (Entity e : mc.theWorld.loadedEntityList) {
            if (!(e instanceof EntityLivingBase)) {
                continue;
            }
            if (e instanceof EntityPlayer && AntiBot.isBot(e)) {
                continue;
            }
            if (e != mc.thePlayer) {
                float expand = 0.3f;
                AxisAlignedBB boundingBox = e.getEntityBoundingBox().expand(expand, expand, expand);
                MovingObjectPosition possibleHit = boundingBox.calculateIntercept(origin, destination);
                if (possibleHit != null) {
                    return possibleHit;
                }
            }
        }
        return null;
    }

    public void drawX() {
        GL11.glBegin(1);
        GL11.glVertex3d(-0.25, 0.0, 0.0);
        GL11.glVertex3d(0.0, 0.0, 0.0);
        GL11.glVertex3d(0.0, 0.0, -0.25);
        GL11.glVertex3d(0.0, 0.0, 0.0);
        GL11.glVertex3d(0.25, 0.0, 0.0);
        GL11.glVertex3d(0.0, 0.0, 0.0);
        GL11.glVertex3d(0.0, 0.0, 0.25);
        GL11.glVertex3d(0.0, 0.0, 0.0);
        GL11.glEnd();
    }
}
