package arsenic.module.impl.visual;

import arsenic.gui.themes.ThemeManager;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.AntiBot;
import arsenic.utils.render.RenderUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.item.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;

@ModuleInfo(name = "Trajectories", category = ModuleCategory.RENDER, hidden = true)
public class Trajectories extends Module {

    @EventLink
    public final Listener<EventRenderWorldLast> renderWorldLast = event -> {
        if (mc.player.getMainHandItem() == null || !(mc.player.getMainHandItem().getItem() instanceof ItemBow)) {
            return;
        }

        ItemStack heldItem = mc.player.getMainHandItem();
        if (!(heldItem.getItem() instanceof ItemBow) && !(heldItem.getItem() instanceof ItemSnowball) && !(heldItem.getItem() instanceof ItemEgg) && !(heldItem.getItem() instanceof ItemEnderPearl)) {
            return;
        }
        if (heldItem.getItem() instanceof ItemBow && !mc.player.isUsingItem()) {
            return;
        }
        boolean bow = false;
        if (heldItem.getItem() instanceof ItemBow) {
            bow = true;
        }

        float playerYaw = mc.player.getYRot();
        float playerPitch = mc.player.getXRot();

        double posX = mc.getRenderManager().viewerPosX - (double) (Mth.cos(playerYaw / 180.0f * (float) Math.PI) * 0.16f);
        double posY = mc.getRenderManager().viewerPosY + (double) mc.player.getEyeHeight() - (double) 0.1f;
        double posZ = mc.getRenderManager().viewerPosZ - (double) (Mth.sin(playerYaw / 180.0f * (float) Math.PI) * 0.16f);

        double motionX = (double) (-Mth.sin(playerYaw / 180.0f * (float) Math.PI) * Mth.cos(playerPitch / 180.0f * (float) Math.PI)) * (bow ? 1.0 : 0.4);
        double motionY = (double) (-Mth.sin(playerPitch / 180.0f * (float) Math.PI)) * (bow ? 1.0 : 0.4);
        double motionZ = (double) (Mth.cos(playerYaw / 180.0f * (float) Math.PI) * Mth.cos(playerPitch / 180.0f * (float) Math.PI)) * (bow ? 1.0 : 0.4);
        int itemInUse = 40;
        if (mc.player.getItemInUseCount() > 0 && bow) {
            itemInUse = mc.player.getItemInUseCount();
        }
        int n10 = 72000 - itemInUse;
        float f10 = (float) n10 / 20.0f;
        if ((double) (f10 = (f10 * f10 + f10 * 2.0f) / 3.0f) < 0.1) {
            return;
        }
        if (f10 > 1.0f) {
            f10 = 1.0f;
        }
        RenderUtils.setColor(ThemeManager.getMainColor());
        boolean bl3 = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean bl4 = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean bl5 = GL11.glIsEnabled(GL11.GL_BLEND);
        if (bl3) {
        }
        if (bl4) {
        }
        if (!bl5) {
        }
        float f11 = (float) Math.sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ);
        motionX /= f11;
        motionY /= f11;
        motionZ /= f11;
        motionX *= (double) (bow ? f10 * 2.0f : 1.0f) * 1.5;
        motionY *= (double) (bow ? f10 * 2.0f : 1.0f) * 1.5;
        motionZ *= (double) (bow ? f10 * 2.0f : 1.0f) * 1.5;
        GL11.glBegin(3);
        boolean ground = false;
        MovingObjectPosition target = null;
        boolean highlight = false;
        double[] transform = new double[]{posX, posY, posZ, motionX, motionY, motionZ};
        for (int k = 0; k <= 100 && !ground; ++k) {
            Vec3 start = new Vec3(transform[0], transform[1], transform[2]);
            Vec3 predicted = new Vec3(transform[0] + transform[3], transform[1] + transform[4], transform[2] + transform[5]);
            MovingObjectPosition rayTraced = mc.level.rayTraceBlocks(start, predicted, false, true, false);
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
            MovingObjectPosition rayTraced = mc.level.rayTraceBlocks(start, predicted, false, true, false);
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
                RenderUtils.setColor(ThemeManager.getError());
            }

            float f14 = 0.99f;
            motionY *= f14;
            GL11.glVertex3d((posX += (motionX *= f14)) - mc.getRenderManager().viewerPosX, (posY += (motionY -= bow ? 0.05 : 0.03)) - mc.getRenderManager().viewerPosY, (posZ += (motionZ *= f14)) - mc.getRenderManager().viewerPosZ);
        }
        GL11.glEnd();
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
        double distance = Math.max(mc.player.getDistance(posX + motionX, posY + motionY, posZ + motionZ) * 0.042830285, 1);
        GL11.glScaled(distance, distance, distance);
        this.drawX();
        if (bl3) {
        }
        if (bl4) {
        }
        if (!bl5) {
        }
    };

    public MovingObjectPosition getEntityHit(Vec3 origin, Vec3 destination) {
        for (Entity e : mc.level.loadedEntityList) {
            if (!(e instanceof LivingEntity)) {
                continue;
            }
            if (e instanceof Player && AntiBot.isBot(e)) {
                continue;
            }
            if (e != mc.player) {
                float expand = 0.3f;
                AABB boundingBox = e.getBoundingBox().expand(expand, expand, expand);
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
