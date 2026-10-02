package arsenic.utils.rotations;

import arsenic.main.Arsenic;
import arsenic.utils.java.JavaUtils;
import arsenic.utils.java.UtilityClass;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class RotationUtils extends UtilityClass {

    //dont bloat this method again. Let it be the way it was when i first made it
    public static float[] getRotationsToEntity(LivingEntity e) {
        if (e == null) return null;
        final Vec3 targetVec = getBestHitVec(e);
        final Vec3 eyePos = mc.player.getEyePosition(1f);
        double x = targetVec.x - eyePos.x;
        double y = targetVec.y - eyePos.y;
        double z = targetVec.z - eyePos.z;
        double distance = Math.sqrt((x * x) + (z * z));
        float targetYaw = (float) ((Math.toDegrees(Math.atan2(z, x))) - 90);
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(y, distance)));
        return new float[]{targetYaw, targetPitch};
    }

    public static float[] getCappedRotations(float[] prev, float[] current, float speed) {
        float yawDiff = getYawDifference(current[0], prev[0]);
        if (Math.abs(yawDiff) > speed)
            yawDiff = (speed * (yawDiff > 0 ? 1 : -1)) / 2f;
        float cappedPYaw = prev[0] + yawDiff;
        float pitchDiff = getPitchDifference(current[1], prev[1]);
        if (Math.abs(pitchDiff) > speed / 2f)
            pitchDiff = (speed / 2f * (pitchDiff > 0 ? 1 : -1)) / 2f;
        float cappedPitch = prev[1] + pitchDiff;
        return new float[]{cappedPYaw, cappedPitch};
    }

    public static float[] getPatchedAndCappedRots(float[] prev, float[] current, float speed) {
        return patchGCD(prev, getCappedRotations(prev, current, speed));
    }

    /** Snaps a rotation change to a whole number of mouse steps, like a real mouse would produce. */
    public static float[] patchGCD(float[] prevRotation, float[] currentRotation) {
        float gcd = getGCD();
        final float deltaYaw = currentRotation[0] - prevRotation[0],
                deltaPitch = currentRotation[1] - prevRotation[1];
        final float yaw = prevRotation[0] + Math.round(deltaYaw / gcd) * gcd,
                pitch = prevRotation[1] + Math.round(deltaPitch / gcd) * gcd;
        return new float[]{yaw, pitch};
    }

    /** One mouse count, in degrees, at the current sensitivity (MouseHandler#turnPlayer). */
    public static float getGCD() {
        float f = (float) (mc.options.sensitivity().get() * 0.6F + 0.2F);
        return f * f * f * 8.0F * 0.15F;
    }

    public static Vec3 getBestHitVec(final Entity entity) {
        final Vec3 positionEyes = mc.player.getEyePosition(1f);
        final AABB box = entity.getBoundingBox();
        final double ex = Mth.clamp(positionEyes.x, box.minX, box.maxX);
        final double ey = Mth.clamp(positionEyes.y, box.minY, box.maxY);
        final double ez = Mth.clamp(positionEyes.z, box.minZ, box.maxZ);
        return new Vec3(ex, ey, ez);
    }

    public static double getDistanceToEntityBox(Entity entity) {
        return getDistanceToEntityBox(entity, mc.player);
    }

    public static double getDistanceToEntityBox(Entity target, Player from) {
        Vec3 eyes = from.getEyePosition(1f);
        return eyes.distanceTo(getBestHitVec(target));
    }

    public static float fovFromEntity(Entity en) {
        return getYawDifference(mc.player.getYRot(), fovToEntity(en));
    }

    public static float fovToEntity(Entity ent) {
        double x = ent.getX() - mc.player.getX();
        double z = ent.getZ() - mc.player.getZ();
        double yaw = Math.atan2(x, z) * 57.2957795D;
        return (float) (yaw * -1.0D);
    }

    // old arsenic
    public static float[] getRotations(Vec3 from, Vec3 to) {
        final float diffY = (float) (from.y - to.y);
        final float diffX = (float) (from.x - to.x);
        final float diffZ = (float) (from.z - to.z);
        final float dist = (float) Math.sqrt((diffX * diffX) + (diffZ * diffZ));
        float pitch = (float) Math.toDegrees(Math.atan2(diffY, dist));
        pitch += JavaUtils.getRandom(-1, 1);
        final float yaw = (float) (Math.toDegrees(Math.atan2(diffZ, diffX)) + 90f);
        return new float[]{yaw, pitch};
    }

    public static float[] getPlayerRotationsToVec(Vec3 to) {
        return getRotations(mc.player.position().add(0, 1.5, 0), to);
    }

    public static Vec3 getVec3FromBlockPosAndEnumFacing(BlockPos blockPos, Direction face) {
        final Vec3 blockVec = Vec3.atCenterOf(blockPos);
        return blockVec.add(face.getStepX() / 2d, face.getStepY() / 2d, face.getStepZ() / 2d);
    }

    public static double getDistanceToBlockPos(BlockPos blockPos) {
        return mc.player.position().distanceTo(Vec3.atLowerCornerOf(blockPos));
    }

    //haven't tested if this works
    public static float[] getPlayerRotationsToBlock(BlockPos pos, Direction face) {
        return getPlayerRotationsToVec(getVec3FromBlockPosAndEnumFacing(pos, face));
    }

    public static float getYawDifference(float yaw1, float yaw2) {
        float yawDiff = (yaw1 - yaw2) % 360f;
        if (yawDiff > 180f) {
            yawDiff -= 360f;
        }
        if (yawDiff < -180f) {
            yawDiff += 360f;
        }
        return yawDiff;
    }

    public static float getPitchDifference(float pitch1, float pitch2) {
        return (pitch1 - pitch2);
    }

    public static float[] getRotations(final BlockPos blockPos) {
        final double x = blockPos.getX() + 0.45 - mc.player.getX();
        final double y = blockPos.getY() + 0.45 - (mc.player.getY() + mc.player.getEyeHeight());
        final double z = blockPos.getZ() + 0.45 - mc.player.getZ();
        float yaw = mc.player.getYRot();
        float pitch = mc.player.getXRot();
        float[] targetRots = new float[]{
                yaw + Mth.wrapDegrees((float) (Math.atan2(z, x) * 57.295780181884766) - 90.0f - yaw),
                clamp(pitch + Mth.wrapDegrees((float) (-(Math.atan2(y, Math.sqrt(x * x + z * z)) * 57.295780181884766)) - pitch))};
        float currentYaw = Arsenic.getArsenic().getSilentRotationManager().yaw;
        float currentPitch = Arsenic.getArsenic().getSilentRotationManager().pitch;
        float[] lastRots = new float[]{currentYaw, currentPitch};
        return patchGCD(lastRots, targetRots);
    }

    public static float clamp(final float n) {
        return Mth.clamp(n, -90.0f, 90.0f);
    }

    public static float updateRotation(float current, float target, float speed) {
        float f = Mth.wrapDegrees(target - current);
        if (f > speed) {
            f = speed;
        }
        if (f < -speed) {
            f = -speed;
        }
        return current + f;
    }
}
