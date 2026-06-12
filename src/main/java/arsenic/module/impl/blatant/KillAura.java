package arsenic.module.impl.blatant;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.injection.accessor.IMixinEntity;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.TargetManager;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.timer.MSTimer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemSword;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.security.SecureRandom;

@ModuleInfo(name = "KillAura", category = ModuleCategory.BLATANT)
public class KillAura extends Module {

    public enum BodyPoint { Head, Body, Feet }
    public enum AutoBlockMode { Off, Packet }
    public enum MarkMode { None, Circle, Box }

    public final EnumProperty<AutoBlockMode> autoBlock = new EnumProperty<>("AutoBlock", AutoBlockMode.Off);
    public final EnumProperty<BodyPoint> bodyPoint = new EnumProperty<>("BodyPoint", BodyPoint.Body);
    public final EnumProperty<MarkMode> mark = new EnumProperty<>("Mark", MarkMode.Circle);

    public final RangeProperty cps = new RangeProperty("CPS", new RangeValue(1, 20, 10, 1, 1));
    public final DoubleProperty range = new DoubleProperty("Range", new DoubleValue(1, 6, 3, 0.1));
    public final DoubleProperty throughWallsRange = new DoubleProperty("ThroughWalls", new DoubleValue(0, 6, 0, 0.1));
    public final DoubleProperty fov = new DoubleProperty("FOV", new DoubleValue(0, 180, 180, 1));
    public final DoubleProperty predictTicks = new DoubleProperty("Predict", new DoubleValue(0, 5, 0, 1));
    public final BooleanProperty swing = new BooleanProperty("Swing", true);
    public final BooleanProperty raycast = new BooleanProperty("RayCast", true);

    private EntityPlayer target;
    private final MSTimer attackTimer = new MSTimer();
    private final SecureRandom random = new SecureRandom();

    @Override
    protected void onEnable() {
        target = null;
    }

    @Override
    protected void onDisable() {
        target = null;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> eventSilentRotationListener = event -> {
        if (target == null) return;

        float[] rots = computeRotations();
        if (rots == null) return;

        event.setYaw(rots[0]);
        event.setPitch(rots[1]);
        event.setSpeed(360f);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> eventTickListener = event -> {
        updateTarget();

        if (target == null) return;

        if (autoBlock.getValue() == AutoBlockMode.Packet) {
            handleAutoBlock();
        }

        if (attackTimer.hasTimeElapsed(getAttackDelay())) {
            double dist = mc.thePlayer.getDistanceToEntity(target);
            double maxRange = Math.max(range.getValue().getInput(), throughWallsRange.getValue().getInput());

            if (dist <= maxRange && isHittable()) {
                if (swing.getValue()) mc.thePlayer.swingItem();
                mc.playerController.attackEntity(mc.thePlayer, target);
                attackTimer.reset();
            }
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> renderWorldLast = event -> {
        if (target == null) return;

        switch (mark.getValue()) {
            case Circle:
                RenderUtils.drawCircle(target, event.partialTicks, 0.7,
                        Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor(), 255);
                break;
            case Box:
                RenderUtils.renderBlock(new BlockPos(target.posX, target.posY + 0.1, target.posZ),
                        Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor(), true, true);
                break;
            default:
                break;
        }
        RenderUtils.resetColor();
    };

    private void updateTarget() {
        EntityPlayer tmTarget = TargetManager.getTarget();
        if (tmTarget != null && isValidTarget(tmTarget)) {
            target = tmTarget;
        } else {
            target = null;
        }
    }

    private boolean isValidTarget(EntityPlayer entity) {
        if (entity == null || entity == mc.thePlayer) return false;
        if (!entity.isEntityAlive() || entity.getHealth() <= 0) return false;

        float entityFov = Math.abs(RotationUtils.fovFromEntity(entity));
        if (entityFov > fov.getValue().getInput()) return false;

        double dist = mc.thePlayer.getDistanceToEntity(entity);
        double maxRange = Math.max(range.getValue().getInput(), throughWallsRange.getValue().getInput());
        return dist <= maxRange;
    }

    private float[] computeRotations() {
        if (target == null) return null;

        Vec3 targetVec = getTargetVector();
        Vec3 eyePos = mc.thePlayer.getPositionEyes(1f);

        if (predictTicks.getValue().getInput() > 0) {
            double dx = target.posX - target.prevPosX;
            double dz = target.posZ - target.prevPosZ;
            double factor = predictTicks.getValue().getInput();
            targetVec = targetVec.addVector(dx * factor, 0, dz * factor);
        }

        double x = targetVec.xCoord - eyePos.xCoord;
        double y = targetVec.yCoord - eyePos.yCoord;
        double z = targetVec.zCoord - eyePos.zCoord;
        double dist = MathHelper.sqrt_double(x * x + z * z);

        float yaw = (float) (Math.toDegrees(Math.atan2(z, x)) - 90);
        float pitch = (float) (-Math.toDegrees(Math.atan2(y, dist)));
        pitch = MathHelper.clamp_float(pitch, -90f, 90f);

        float currentYaw = Arsenic.getArsenic().getSilentRotationManager().yaw;
        float currentPitch = Arsenic.getArsenic().getSilentRotationManager().pitch;

        return RotationUtils.patchGCD(new float[]{currentYaw, currentPitch}, new float[]{yaw, pitch});
    }

    private Vec3 getTargetVector() {
        switch (bodyPoint.getValue()) {
            case Head:
                return new Vec3(target.posX, target.posY + target.getEyeHeight(), target.posZ);
            case Feet:
                return new Vec3(target.posX, target.posY + 0.1, target.posZ);
            default:
                return RotationUtils.getBestHitVec(target);
        }
    }

    private boolean isHittable() {
        if (!raycast.getValue()) return true;

        float yaw = Arsenic.getArsenic().getSilentRotationManager().yaw;
        float pitch = Arsenic.getArsenic().getSilentRotationManager().pitch;

        Vec3 eyes = mc.thePlayer.getPositionEyes(1f);
        Vec3 lookVec = ((IMixinEntity) mc.thePlayer).invokeGetVectorForRotation(pitch, yaw);
        double reach = range.getValue().getInput();
        Vec3 end = eyes.addVector(lookVec.xCoord * reach, lookVec.yCoord * reach, lookVec.zCoord * reach);

        MovingObjectPosition mop = mc.theWorld.rayTraceBlocks(eyes, end, false, false, true);
        if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            double blockDist = eyes.distanceTo(mop.hitVec);
            double targetDist = RotationUtils.getDistanceToEntityBox(target);
            if (blockDist < targetDist) {
                return targetDist <= throughWallsRange.getValue().getInput();
            }
        }

        return true;
    }

    private void handleAutoBlock() {
        if (mc.thePlayer.getHeldItem() == null) return;
        if (!(mc.thePlayer.getHeldItem().getItem() instanceof ItemSword)) return;

        double dist = mc.thePlayer.getDistanceToEntity(target);
        if (dist <= range.getValue().getInput() && isHittable()) {
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem());
        }
    }

    private long getAttackDelay() {
        double max = cps.getValue().getMax();
        double min = cps.getValue().getMin();
        float cpsValue = (float)(min + random.nextFloat() * (max - min));
        return (long) (1000L / cpsValue);
    }
}
