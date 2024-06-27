package arsenic.utils.rotations;

import arsenic.main.Arsenic;
import arsenic.utils.java.JavaUtils;
import arsenic.utils.java.UtilityClass;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.*;

public class RotationUtils extends UtilityClass {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public static float[] getRotationsToEntity(EntityLivingBase e, int smooth) {
        if (e == null) return null;

        double x = e.posX - mc.thePlayer.posX;
        double y = e.getPositionVector().yCoord - mc.thePlayer.getPositionVector().yCoord;
        double z = e.posZ - mc.thePlayer.posZ;
        double distance = MathHelper.sqrt_double((x * x) + (z * z));

        float targetYaw = (float) ((Math.toDegrees(Math.atan2(z, x))) - 90);
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(y, distance)));

        float random1 = (float) JavaUtils.getRandom(-1, 1);
        float random2 = (float) JavaUtils.getRandom(-1, 1);

        if (random1 == random2) {
            while (random1 == random2) {
                random1 = (float) JavaUtils.getRandom(-1, 1);
            }
        }

        targetYaw += random1;
        targetPitch += random2;

        // Get current player rotations
        float currentYaw = Arsenic.getArsenic().getSilentRotationManager().yaw;
        float currentPitch = Arsenic.getArsenic().getSilentRotationManager().pitch;

        // Smoothing based on FOV
        float smoothingSpeed = smooth; // Base smoothing speed
        if (smoothingSpeed == 1) { // 1 means no smoothing
            float[] rots = new float[]{targetYaw, targetPitch};
            float[] lastRots = new float[]{currentYaw, currentPitch};
            float[] fixedRots = patchGCD(lastRots, rots);

            return fixedRots;
        }

        // Calculate the FOV diffrence
        float yawDifference = MathHelper.wrapAngleTo180_float(targetYaw - currentYaw);
        float pitchDifference = targetPitch - currentPitch;

        double fovToEntity = fovFromEntity(e);

        float fovFactor = (float) (1.0 + Math.abs(fovToEntity) / 180.0); // More FOV difference means more speed

        float finalYaw = currentYaw + yawDifference * fovFactor / smoothingSpeed;
        float finalPitch = currentPitch + pitchDifference * fovFactor / smoothingSpeed;

        // Create the rotations array
        float[] rots = new float[]{finalYaw, finalPitch};

        // Smooth out the rotations using patchGCD method
        float[] lastRots = new float[]{currentYaw, currentPitch};
        float[] fixedRots = patchGCD(lastRots, rots);

        return fixedRots;
    }

    public static float[] getRotations(BlockPos position, EnumFacing facing) {
        float currentYaw = Arsenic.getArsenic().getSilentRotationManager().yaw;
        float currentPitch = Arsenic.getArsenic().getSilentRotationManager().pitch;
        float[] rots = new float[]{mc.thePlayer.rotationYaw + 180, 77};

        // Smooth out the rotations using patchGCD method
        float[] lastRots = new float[]{currentYaw, currentPitch};
        float[] fixedRots = patchGCD(lastRots, rots);
        return fixedRots;
    }

    public static float[] patchGCD(float[] prevRotation, float[] currentRotation) {
        float f = mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
        float gcd = f * f * f * 8.0F * 0.15F;
        final float deltaYaw = currentRotation[0] - prevRotation[0],
                deltaPitch = currentRotation[1] - prevRotation[1];
        final float yaw = prevRotation[0] + Math.round(deltaYaw / gcd) * gcd,
                pitch = prevRotation[1] + Math.round(deltaPitch / gcd) * gcd;

        return new float[]{yaw, pitch};
    }

    public static Vec3 getBestHitVec(final Entity entity) {
        final Vec3 positionEyes = mc.thePlayer.getPositionEyes(1f);
        final float size = entity.getCollisionBorderSize();
        final AxisAlignedBB entityBoundingBox = entity.getEntityBoundingBox().expand(size, size, size);
        final double ex = MathHelper.clamp_double(positionEyes.xCoord, entityBoundingBox.minX, entityBoundingBox.maxX);
        final double ey = MathHelper.clamp_double(positionEyes.yCoord, entityBoundingBox.minY, entityBoundingBox.maxY);
        final double ez = MathHelper.clamp_double(positionEyes.zCoord, entityBoundingBox.minZ, entityBoundingBox.maxZ);
        return new Vec3(ex, ey - 0.4, ez);
    }

    public static double getDistanceToEntityBox(Entity entity) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1f);
        Vec3 pos = RotationUtils.getBestHitVec(entity);
        double xDist = Math.abs(pos.xCoord - eyes.xCoord);
        double yDist = Math.abs(pos.yCoord - eyes.yCoord);
        double zDist = Math.abs(pos.zCoord - eyes.zCoord);
        return Math.sqrt(Math.pow(xDist, 2) + Math.pow(yDist, 2) + Math.pow(zDist, 2));
    }

    public static double fovFromEntity(Entity en) {
        return ((((double) (mc.thePlayer.rotationYaw - fovToEntity(en)) % 360.0D) + 540.0D) % 360.0D) - 180.0D;
    }

    public static float fovToEntity(Entity ent) {
        double x = ent.posX - mc.thePlayer.posX;
        double z = ent.posZ - mc.thePlayer.posZ;
        double yaw = Math.atan2(x, z) * 57.2957795D;
        return (float) (yaw * -1.0D);
    }

    // old arsenic
    public static float[] getRotations(Vec3 from, Vec3 to) {
        final float diffY = (float) (from.yCoord - to.yCoord);
        final float diffX = (float) (from.xCoord - to.xCoord);
        final float diffZ = (float) (from.zCoord - to.zCoord);
        final float dist = MathHelper.sqrt_double((diffX * diffX) + (diffZ * diffZ));
        float pitch = (float) Math.toDegrees(Math.atan2(diffY, dist));
        pitch += JavaUtils.getRandom(-1, 1);
        final float yaw = (float) MathHelper.wrapAngleTo180_double(Math.toDegrees(Math.atan2(diffZ, diffX)) + 90f);
        return new float[]{yaw, pitch};
    }

    public static float[] getPlayerRotationsToVec(Vec3 to) {
        return getRotations(mc.thePlayer.getPositionVector().addVector(0, 1.5, 0), to);
    }

    public static Vec3 getVec3FromBlockPosAndEnumFacing(BlockPos blockPos, EnumFacing face) {
        final Vec3 blockVec = new Vec3(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
        return blockVec.addVector(face.getFrontOffsetX() / 2d, face.getFrontOffsetY() / 2d, face.getFrontOffsetZ() / 2d);
    }

    public static double getDistanceToBlockPos(BlockPos blockPos) {
        return mc.thePlayer.getPositionVector().distanceTo(new Vec3(blockPos));
    }

    //haven't tested if this works
    public static float[] getPlayerRotationsToBlock(BlockPos pos, EnumFacing face) {
        return getPlayerRotationsToVec(getVec3FromBlockPosAndEnumFacing(pos, face));
    }

    public static float getYawDifference(float yaw1, float yaw2) {
        float yawDiff = MathHelper.wrapAngleTo180_float(yaw1) - MathHelper.wrapAngleTo180_float(yaw2);
        if (Math.abs(yawDiff) > 180)
            yawDiff = yawDiff + 360;
        return MathHelper.wrapAngleTo180_float(yawDiff);
    }

    public static float getPitchDifference(float pitch1, float pitch2) {
        return (pitch1 - pitch2);
    }

    public static float[] getRotations(final BlockPos blockPos) {
        final double x = blockPos.getX() + 0.45 - mc.thePlayer.posX;
        final double y = blockPos.getY() + 0.45 - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        final double z = blockPos.getZ() + 0.45 - mc.thePlayer.posZ;
        float[] targetRots = new float[]{mc.thePlayer.rotationYaw + MathHelper.wrapAngleTo180_float((float) (Math.atan2(z, x) * 57.295780181884766) - 90.0f - mc.thePlayer.rotationYaw), clamp(mc.thePlayer.rotationPitch + MathHelper.wrapAngleTo180_float((float) (-(Math.atan2(y, MathHelper.sqrt_double(x * x + z * z)) * 57.295780181884766)) - mc.thePlayer.rotationPitch))};

        float currentYaw = Arsenic.getArsenic().getSilentRotationManager().yaw;
        float currentPitch = Arsenic.getArsenic().getSilentRotationManager().pitch;

        float[] lastRots = new float[]{currentYaw, currentPitch};
        float[] fixedRots = patchGCD(lastRots, targetRots);
        return fixedRots;
    }

    public static float clamp(final float n) {
        return MathHelper.clamp_float(n, -90.0f, 90.0f);
    }
}