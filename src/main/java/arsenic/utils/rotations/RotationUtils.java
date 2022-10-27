package arsenic.utils.rotations;

import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

public class RotationUtils {

    private static Minecraft mc = Minecraft.getMinecraft();

    public static float getYawFromTo(Vec3 from, Vec3 to) {
        final float diffX = (float) (from.xCoord - to.xCoord);
        final float diffZ = (float) (from.zCoord - to.zCoord);
        final float yaw = (float) ((Math.atan2(diffZ, diffX) * 180.0D) / 3.141592653589793D) - 90.0F;
        return yaw;
    }

    public static float getPitchFromTo(Vec3 from, Vec3 to) {
        final float diffY = (float) (from.yCoord - to.yCoord);
        final float diffX = (float) (from.xCoord - to.xCoord);
        final float diffZ = (float) (from.zCoord - to.zCoord);
        final float dist = MathHelper.sqrt_double((diffX * diffX) + (diffZ * diffZ));
        final float pitch = (float) (-((Math.atan2(diffY, dist) * 180.0D) / 3.141592653589793D));
        return pitch;
    }

    public static float[] getRotations(Vec3 from, Vec3 to) {
        final float diffY = (float) (from.yCoord - to.yCoord);
        final float diffX = (float) (from.xCoord - to.xCoord);
        final float diffZ = (float) (from.zCoord - to.zCoord);
        final float dist = MathHelper.sqrt_double((diffX * diffX) + (diffZ * diffZ));

        final float pitch = (float) (-((Math.atan2(diffY, dist) * 180.0D) / 3.141592653589793D));
        final float yaw = (float) ((Math.atan2(diffZ, diffX) * 180.0D) / 3.141592653589793D) - 90.0F;
        return new float[] {yaw, pitch};
    }

    public static float[] getPlayerRotationsToVec(Vec3 to) {
        return getRotations(mc.thePlayer.getPositionVector().addVector(0, mc.thePlayer.eyeHeight, 0), to);
    }

    public static float[] getPlayerRotationsToBlock(BlockPos pos, EnumFacing face) {
        final Vec3 v = new Vec3(pos.getX() + face.getFrontOffsetX(), pos.getY() + face.getFrontOffsetY(), pos.getZ() + face.getFrontOffsetZ());
        return getPlayerRotationsToVec(v);
    }

    public static float getYawDifference(float yaw1, float yaw2) {
        return (((((yaw1 - yaw2) % 360.0F) + 540.0F) % 360.0F) - 180.0F);
    }

    public static float getPitchDifference(float pitch1, float pitch2) {
        return (pitch1 - pitch2);
    }


}
