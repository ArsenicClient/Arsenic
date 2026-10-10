package arsenic.utils.rotations;

import arsenic.event.impl.EventSilentRotation;
import arsenic.main.Arsenic;
import arsenic.utils.aimcore.AimCore;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class AimController {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public enum RotationMode {
        Instant,
        Lazy,
        /** Laggy, imperfect tracking that keeps rotation-accuracy heuristics quiet at the cost of some misses. */
        Heuristics
    }

    private final AimCore core = new AimCore(AimCore.Tuning.best(), new Random());

    public float defaultPrediction() {
        return core.tun.predictionTicks;
    }

    /** Rough share of the time Heuristics aim spends slipped off the hitbox, 0 to 1. */
    public void setMissChance(float chance) {
        core.missChance = chance;
    }

    public void reset() {
        core.reset();
    }

    public void cancelFlick() {
        core.cancelFlick();
    }

    public void updateDrift() {
        core.updateDrift(input(null));
    }

    public float[] aimAt(Entity e, float ticks) {
        AimCore.Input in = input(e);
        core.observe(in);
        return core.aimRotations(in, ticks);
    }

    public void rotate(EventSilentRotation event, Entity target, float[] rots, RotationMode mode,
                       float minSpeed, float maxSpeed, float budgetTicks) {
        if (mode == RotationMode.Lazy || mode == RotationMode.Heuristics) {
            AimCore.Input in = input(target);
            in.maxSpeed = maxSpeed;
            in.budgetTicks = budgetTicks;
            float[] out = mode == RotationMode.Heuristics ? core.heuristicStep(in) : core.lazyStep(in, rots);
            event.setYaw(out[0]);
            event.setPitch(out[1]);
            event.setSpeed(maxSpeed);
            event.setSmoothing(false);
        } else {
            event.setYaw(rots[0]);
            event.setPitch(rots[1]);
            event.setSpeed(minSpeed + ThreadLocalRandom.current().nextFloat() * (maxSpeed - minSpeed));
        }
    }

    public float[] getPredictedRotations(Entity e, float ticks) {
        return core.peekRotations(input(e), ticks);
    }

    public AxisAlignedBB predictBox(Entity e, float ticks) {
        double[] o = core.predictOffset(input(e), ticks);
        return e.getEntityBoundingBox().offset(o[0], 0, o[1]);
    }

    private AimCore.Input input(Entity e) {
        AimCore.Input in = new AimCore.Input();
        Vec3 eyes = mc.thePlayer.getPositionEyes(1f);
        in.eyeX = eyes.xCoord;
        in.eyeY = eyes.yCoord;
        in.eyeZ = eyes.zCoord;
        in.selfDX = mc.thePlayer.posX - mc.thePlayer.lastTickPosX;
        in.selfDZ = mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ;
        in.selfMotionX = mc.thePlayer.motionX;
        in.selfMotionZ = mc.thePlayer.motionZ;
        SilentRotationManager srm = Arsenic.getArsenic().getSilentRotationManager();
        in.curYaw = srm.yaw;
        in.curPitch = srm.pitch;
        if (e != null) {
            AxisAlignedBB box = e.getEntityBoundingBox();
            in.minX = box.minX;
            in.minY = box.minY;
            in.minZ = box.minZ;
            in.maxX = box.maxX;
            in.maxY = box.maxY;
            in.maxZ = box.maxZ;
            in.targetDX = e.posX - e.lastTickPosX;
            in.targetDZ = e.posZ - e.lastTickPosZ;
            in.targetId = e.getEntityId();
        }
        return in;
    }
}
