package arsenic.module.impl.ghost;

import arsenic.module.property.impl.BooleanProperty;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.TargetManager;
import arsenic.utils.minecraft.PlayerUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.item.ItemBow;
import net.minecraft.util.Mth;

@ModuleInfo(name = "BowAimbot", category = ModuleCategory.COMBAT)
public class BowAimbot extends Module {
    /** Lead the target by its velocity. More hits, and a rotation the player never made. */
    public final BooleanProperty predict = new BooleanProperty("Predict", true);



    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRender = event -> {
        if (mc.player.getMainHandItem() == null || !(mc.player.getMainHandItem().getItem() instanceof ItemBow) || !mc.player.isUsingItem())
            return;

        LivingEntity target = TargetManager.getTarget();
        if (target == null) {
            target = PlayerUtils.getClosestPlayerWithin(40);
        }
        if (target == null) return;
        if (!PlayerUtils.withinFov(target, (float) 90)) return;

        float[] rots = getBowRotations(target);
        if (rots != null) {
            mc.player.rotationYaw = rots[0];
            mc.player.rotationPitch = rots[1];
        }
    };

    private float[] getBowRotations(LivingEntity target) {
        double x = target.getX() - mc.player.getX();
        double z = target.getZ() - mc.player.getZ();
        double y = target.getY() + target.getEyeHeight() - 0.1 - mc.player.getY() - mc.player.getEyeHeight();

        if (predict.getValue()) {
            double bowPower = mc.player.getItemInUseDuration() / 20.0;
            bowPower = (bowPower * bowPower + bowPower * 2.0) / 3.0;
            if (bowPower > 1.0) bowPower = 1.0;

            double dist = (float) Math.sqrt(x * x + z * z);
            double velocity = bowPower * 3.0;
            double time = dist / velocity;

            x += (target.getX() - target.xo) * time;
            z += (target.getZ() - target.zo) * time;
        }

        double dist = (float) Math.sqrt(x * x + z * z);
        float yaw = (float) (Math.atan2(z, x) * 180.0 / Math.PI) - 90.0f;

        double v = 3.0;
        double g = 0.05;
        double pitch = -Math.toDegrees(Math.atan(
                (Math.pow(v, 2) - Math.sqrt(Math.pow(v, 4) - g * (g * Math.pow(dist, 2) + 2 * y * Math.pow(v, 2)))) / (g * dist)));

        if (Double.isNaN(pitch)) {
            pitch = -(Math.atan2(y, dist) * 180.0 / Math.PI);
        }

        return new float[]{
                mc.player.getYRot() + Mth.wrapDegrees(yaw - mc.player.getYRot()),
                (float) pitch
        };
    }
}
