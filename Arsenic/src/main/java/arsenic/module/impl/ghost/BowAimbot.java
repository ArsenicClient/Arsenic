package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.TargetManager;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.PlayerUtils;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemBow;
import net.minecraft.util.MathHelper;

@ModuleInfo(name = "BowAimbot", category = ModuleCategory.GHOST)
public class BowAimbot extends Module {

    public final DoubleProperty fov = new DoubleProperty("FOV", new DoubleValue(10, 360, 90, 1));
    public final DoubleProperty distance = new DoubleProperty("Distance", new DoubleValue(5, 100, 40, 1));
    public final BooleanProperty predict = new BooleanProperty("Predict", true);

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRender = event -> {
        if (mc.thePlayer.getHeldItem() == null || !(mc.thePlayer.getHeldItem().getItem() instanceof ItemBow) || !mc.thePlayer.isUsingItem())
            return;

        EntityLivingBase target = TargetManager.getTarget();
        if (target == null) {
            target = PlayerUtils.getClosestPlayerWithin(distance.getValue().getInput());
        }
        if (target == null) return;
        if (!PlayerUtils.withinFov(target, (float) fov.getValue().getInput())) return;

        float[] rots = getBowRotations(target);
        if (rots != null) {
            mc.thePlayer.rotationYaw = rots[0];
            mc.thePlayer.rotationPitch = rots[1];
        }
    };

    private float[] getBowRotations(EntityLivingBase target) {
        double x = target.posX - mc.thePlayer.posX;
        double z = target.posZ - mc.thePlayer.posZ;
        double y = target.posY + target.getEyeHeight() - 0.1 - mc.thePlayer.posY - mc.thePlayer.getEyeHeight();

        if (predict.getValue()) {
            double bowPower = mc.thePlayer.getItemInUseDuration() / 20.0;
            bowPower = (bowPower * bowPower + bowPower * 2.0) / 3.0;
            if (bowPower > 1.0) bowPower = 1.0;

            double dist = MathHelper.sqrt_double(x * x + z * z);
            double velocity = bowPower * 3.0;
            double time = dist / velocity;

            x += (target.posX - target.prevPosX) * time;
            z += (target.posZ - target.prevPosZ) * time;
        }

        double dist = MathHelper.sqrt_double(x * x + z * z);
        float yaw = (float) (Math.atan2(z, x) * 180.0 / Math.PI) - 90.0f;

        double v = 3.0;
        double g = 0.05;
        double pitch = -Math.toDegrees(Math.atan(
                (Math.pow(v, 2) - Math.sqrt(Math.pow(v, 4) - g * (g * Math.pow(dist, 2) + 2 * y * Math.pow(v, 2)))) / (g * dist)));

        if (Double.isNaN(pitch)) {
            pitch = -(Math.atan2(y, dist) * 180.0 / Math.PI);
        }

        return new float[]{
                mc.thePlayer.rotationYaw + MathHelper.wrapAngleTo180_float(yaw - mc.thePlayer.rotationYaw),
                (float) pitch
        };
    }
}
