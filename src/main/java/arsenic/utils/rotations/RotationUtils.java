package arsenic.utils.rotations;

import arsenic.utils.java.UtilityClass;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

public class RotationUtils extends UtilityClass {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public static float[] getRotations(Vec3 from, Vec3 to) {
        final float diffY = (float) (from.yCoord - to.yCoord);
        final float diffX = (float) (from.xCoord - to.xCoord);
        final float diffZ = (float) (from.zCoord - to.zCoord);
        final float dist = MathHelper.sqrt_double((diffX * diffX) + (diffZ * diffZ));

        final float pitch = (float) (-Math.toDegrees(Math.atan2(diffY, dist)));
        final float yaw = (float) MathHelper.wrapAngleTo180_double(Math.toDegrees(Math.atan2(diffZ, diffX)) + 90f);
        return new float[] {yaw, pitch};
    }

    public static float[] getPlayerRotationsToVec(Vec3 to) {
        return getRotations(mc.thePlayer.getPositionVector().addVector(0, mc.thePlayer.eyeHeight, 0), to);
    }

    //haven't tested if this works
    public static float[] getPlayerRotationsToBlock(BlockPos pos, EnumFacing face) {
        final Vec3 v = new Vec3(pos.getX() + face.getFrontOffsetX(), pos.getY() + face.getFrontOffsetY(), pos.getZ() + face.getFrontOffsetZ());
        return getPlayerRotationsToVec(v);
    }

    public static float getYawDifference(float yaw1, float yaw2) {
        float yawDiff = MathHelper.wrapAngleTo180_float(yaw1) - MathHelper.wrapAngleTo180_float(yaw2);
        if(Math.abs(yawDiff) > 180)
            yawDiff = yawDiff + 360;
        return MathHelper.wrapAngleTo180_float(yawDiff);
    }

    public static float getPitchDifference(float pitch1, float pitch2) {
        return -(pitch1 - pitch2);
    }

}