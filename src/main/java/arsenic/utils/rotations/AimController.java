package arsenic.utils.rotations;

import arsenic.event.impl.EventSilentRotation;
import arsenic.main.Arsenic;
import arsenic.utils.aimcore.AimCore;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class AimController {

    private static final Minecraft mc = Minecraft.getInstance();

    public enum RotationMode {
        Instant,
        Lazy
    }

    private final AimCore core = new AimCore(AimCore.Tuning.best(), new Random());

    public float defaultPrediction() {
        return core.tun.predictionTicks;
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
        if (mode == RotationMode.Lazy) {
            AimCore.Input in = input(target);
            in.maxSpeed = maxSpeed;
            in.budgetTicks = budgetTicks;
            float[] out = core.lazyStep(in, rots);
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

    public AABB predictBox(Entity e, float ticks) {
        double[] o = core.predictOffset(input(e), ticks);
        return e.getBoundingBox().move(o[0], 0, o[1]);
    }

    private AimCore.Input input(Entity e) {
        AimCore.Input in = new AimCore.Input();
        Vec3 eyes = mc.player.getEyePosition(1f);
        in.eyeX = eyes.x;
        in.eyeY = eyes.y;
        in.eyeZ = eyes.z;
        in.selfDX = mc.player.getX() - mc.player.xOld;
        in.selfDZ = mc.player.getZ() - mc.player.zOld;
        Vec3 motion = mc.player.getDeltaMovement();
        in.selfMotionX = motion.x;
        in.selfMotionZ = motion.z;
        SilentRotationManager srm = Arsenic.getArsenic().getSilentRotationManager();
        in.curYaw = srm.yaw;
        in.curPitch = srm.pitch;
        if (e != null) {
            AABB box = e.getBoundingBox();
            in.minX = box.minX;
            in.minY = box.minY;
            in.minZ = box.minZ;
            in.maxX = box.maxX;
            in.maxY = box.maxY;
            in.maxZ = box.maxZ;
            in.targetDX = e.getX() - e.xOld;
            in.targetDZ = e.getZ() - e.zOld;
            in.targetId = e.getId();
        }
        return in;
    }
}
