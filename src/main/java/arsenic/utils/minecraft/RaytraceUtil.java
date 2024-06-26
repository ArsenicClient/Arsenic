package arsenic.utils.minecraft;

import arsenic.injection.accessor.IMixinMinecraft;
import arsenic.utils.java.UtilityClass;
import net.minecraft.entity.Entity;
import net.minecraft.util.*;

public class RaytraceUtil extends UtilityClass {

    public static boolean isMouseOver(final float yaw, final float pitch, final Entity target, final float range) {
        final float partialTicks = ((IMixinMinecraft) mc).getTimer().renderPartialTicks;
        MovingObjectPosition rayTrace = customRayTrace(range, partialTicks, yaw, pitch);
        if (rayTrace.entityHit != null && rayTrace.entityHit == target) {
            return true;
        }
        return false;
    }


    public static MovingObjectPosition customRayTrace(final double blockReachDistance, final float partialTicks, final float yaw, final float pitch) {
        final Vec3 vec3 = mc.thePlayer.getPositionEyes(partialTicks);
        final Vec3 vec4 = getCustomLook(partialTicks, yaw, pitch);
        final Vec3 vec5 = vec3.addVector(vec4.xCoord * blockReachDistance, vec4.yCoord * blockReachDistance, vec4.zCoord * blockReachDistance);
        return mc.theWorld.rayTraceBlocks(vec3, vec5, false, false, true);
    }

    public static final Vec3 getVectorForRotation(float pitch, float yaw) {
        float f = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float f1 = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float f2 = -MathHelper.cos(-pitch * 0.017453292F);
        float f3 = MathHelper.sin(-pitch * 0.017453292F);
        return new Vec3((double) (f1 * f2), (double) f3, (double) (f * f2));
    }

    public static Vec3 getCustomLook(final float partialTicks, final float yaw, final float pitch) {
        if (partialTicks == 1.0f || partialTicks == 2.0f) {
            return getVectorForRotation(pitch, yaw);
        }

        final float f = mc.thePlayer.prevRotationPitch + (mc.thePlayer.rotationPitch - mc.thePlayer.prevRotationPitch) * partialTicks;
        final float f2 = mc.thePlayer.prevRotationYaw + (mc.thePlayer.rotationYaw - mc.thePlayer.prevRotationYaw) * partialTicks;
        return getVectorForRotation(f, f2);
    }
}
