package arsenic.utils.bot;

import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.botcore.Goal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.Options;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

public final class Chaser {
    private static final Minecraft mc = Minecraft.getInstance();

    private static final double REGOAL_DISTANCE = 3.0;
    private static final double DIRECT_RANGE = 6.0;
    private static final double DIRECT_MAX_DY = 1.2;
    private static final int FAIL_COOLDOWN_TICKS = 20;

    private double[] goalPoint;
    private int failCooldown;
    private boolean keysHeld;

    public boolean chase(Entity target, double stopAt) {
        double tx = target.getX(), ty = target.getY() + target.getBbHeight() / 2, tz = target.getZ();
        LocalPlayer p = mc.player;
        Vec3 eyes = p.getEyePosition(1f);
        boolean visible = canSee(eyes, new Vec3(tx, ty, tz));
        double flat = Math.hypot(tx - p.getX(), tz - p.getZ());

        if (visible && eyes.distanceTo(new Vec3(tx, ty, tz)) <= stopAt) {
            stop();
            return true;
        }
        if (visible && flat <= DIRECT_RANGE && Math.abs(target.getY() - p.getY()) <= DIRECT_MAX_DY) {
            walkStraight(tx, tz);
            return false;
        }
        pathTo(tx, ty, tz, () -> new Goal.NearPoint(tx, ty, tz, stopAt), tx, tz);
        return false;
    }

    public boolean goTo(double x, double y, double z, double stopAt) {
        LocalPlayer p = mc.player;
        double flat = Math.hypot(x - p.getX(), z - p.getZ());
        if (flat <= stopAt && Math.abs(y - p.getY()) < 1.5) {
            stop();
            return true;
        }
        int bx = Mth.floor(x), by = Mth.floor(y + 1e-3), bz = Mth.floor(z);
        pathTo(x, y, z, () -> new Goal.Block(bx, by, bz), x, z);
        return false;
    }

    public void stop() {
        if (BotDriver.isActive())
            BotDriver.stop();
        goalPoint = null;
        failCooldown = 0;
        releaseKeys();
    }

    private void pathTo(double x, double y, double z, Supplier<Goal> goal, double fallbackX, double fallbackZ) {
        if (BotDriver.bot.failed()) {
            BotDriver.stop();
            goalPoint = null;
            failCooldown = FAIL_COOLDOWN_TICKS;
        }
        if (failCooldown > 0) {
            failCooldown--;
            walkStraight(fallbackX, fallbackZ);
            return;
        }

        releaseKeys();
        boolean moved = goalPoint == null
                || Math.sqrt(sq(goalPoint[0] - x) + sq(goalPoint[1] - y) + sq(goalPoint[2] - z)) > REGOAL_DISTANCE;
        if (moved || !BotDriver.isActive()) {
            BotDriver.goTo(goal.get());
            goalPoint = new double[]{x, y, z};
        }
        BotDriver.onTick();
    }

    private void walkStraight(double x, double z) {
        if (BotDriver.isActive())
            BotDriver.stop();
        goalPoint = null;
        LocalPlayer p = mc.player;
        p.setYRot(RotationUtils.yawTo(x - p.getX(), z - p.getZ()));
        p.setXRot(0);
        Options gs = mc.options;
        gs.keyUp.setDown(true);
        gs.keySprint.setDown(true);
        gs.keyJump.setDown(p.horizontalCollision);
        keysHeld = true;
    }

    private void releaseKeys() {
        if (!keysHeld)
            return;
        Options gs = mc.options;
        gs.keyUp.setDown(false);
        gs.keySprint.setDown(false);
        gs.keyJump.setDown(false);
        keysHeld = false;
    }

    private static boolean canSee(Vec3 from, Vec3 to) {
        HitResult mop = arsenic.utils.minecraft.PlayerUtils.rayTraceBlocks(from, to);
        return mop == null || mop.getType() != HitResult.Type.BLOCK;
    }

    private static double sq(double d) {
        return d * d;
    }
}
