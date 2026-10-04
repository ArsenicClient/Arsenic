package arsenic.utils.bot;

import arsenic.utils.botcore.Goal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.function.Supplier;

/**
 * Gets the player to a moving entity or a fixed spot, for FightBot and AutoHunt.
 * <p>
 * Far away or out of sight, it goes through the pathfinder ({@link BotDriver}). The goal is only
 * re-set once the target has wandered {@link #REGOAL_DISTANCE} from where the current path was
 * aimed - every re-set throws the path away and starts a fresh search, so doing it every tick
 * would mean never moving at all. Up close with a clear line, it walks straight at them instead:
 * a planner a few ticks behind a strafing player only ever runs to where they used to be.
 */
public final class Chaser {
    private static final Minecraft mc = Minecraft.getMinecraft();

    /** How far the target can move from the current path's goal before it's re-aimed. */
    private static final double REGOAL_DISTANCE = 3.0;
    /** Within this (flat) distance and in plain sight, walk straight at the target. */
    private static final double DIRECT_RANGE = 6.0;
    /** Height difference past which straight walking is not trusted (stairs, ledges, pits). */
    private static final double DIRECT_MAX_DY = 1.2;
    /** Ticks to wait before searching again after the pathfinder found nothing. */
    private static final int FAIL_COOLDOWN_TICKS = 20;

    private double[] goalPoint;
    private int failCooldown;
    private boolean keysHeld;

    /**
     * Moves toward {@code target} until within {@code stopAt} of its body, in sight.
     *
     * @return whether it's close enough and has stopped
     */
    public boolean chase(Entity target, double stopAt) {
        double tx = target.posX, ty = target.posY + target.height / 2, tz = target.posZ;
        EntityPlayerSP p = mc.thePlayer;
        Vec3 eyes = p.getPositionEyes(1f);
        boolean visible = canSee(eyes, new Vec3(tx, ty, tz));
        double flat = Math.hypot(tx - p.posX, tz - p.posZ);

        if (visible && eyes.distanceTo(new Vec3(tx, ty, tz)) <= stopAt) {
            stop();
            return true;
        }
        if (visible && flat <= DIRECT_RANGE && Math.abs(target.posY - p.posY) <= DIRECT_MAX_DY) {
            walkStraight(tx, tz);
            return false;
        }
        pathTo(tx, ty, tz, () -> new Goal.NearPoint(tx, ty, tz, stopAt), tx, tz);
        return false;
    }

    /**
     * Walks back to a fixed spot (feet position).
     *
     * @return whether it's there and has stopped
     */
    public boolean goTo(double x, double y, double z, double stopAt) {
        EntityPlayerSP p = mc.thePlayer;
        double flat = Math.hypot(x - p.posX, z - p.posZ);
        if (flat <= stopAt && Math.abs(y - p.posY) < 1.5) {
            stop();
            return true;
        }
        int bx = MathHelper.floor_double(x), by = MathHelper.floor_double(y + 1e-3), bz = MathHelper.floor_double(z);
        pathTo(x, y, z, () -> new Goal.Block(bx, by, bz), x, z);
        return false;
    }

    /** Lets go of everything: the pathfinder and any keys held for straight walking. */
    public void stop() {
        if (BotDriver.isActive())
            BotDriver.stop();
        goalPoint = null;
        failCooldown = 0;
        releaseKeys();
    }

    private void pathTo(double x, double y, double z, Supplier<Goal> goal, double fallbackX, double fallbackZ) {
        // No route from here: head straight for it for a moment, then search again. (Stopping
        // clears the failed flag, so this triggers once per failure.)
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
        EntityPlayerSP p = mc.thePlayer;
        p.rotationYaw = (float) (MathHelper.atan2(z - p.posZ, x - p.posX) * 180.0 / Math.PI) - 90.0f;
        p.rotationPitch = 0;
        GameSettings gs = mc.gameSettings;
        KeyBinding.setKeyBindState(gs.keyBindForward.getKeyCode(), true);
        KeyBinding.setKeyBindState(gs.keyBindSprint.getKeyCode(), true);
        KeyBinding.setKeyBindState(gs.keyBindJump.getKeyCode(), p.isCollidedHorizontally);
        keysHeld = true;
    }

    private void releaseKeys() {
        if (!keysHeld)
            return;
        GameSettings gs = mc.gameSettings;
        KeyBinding.setKeyBindState(gs.keyBindForward.getKeyCode(), false);
        KeyBinding.setKeyBindState(gs.keyBindSprint.getKeyCode(), false);
        KeyBinding.setKeyBindState(gs.keyBindJump.getKeyCode(), false);
        keysHeld = false;
    }

    private static boolean canSee(Vec3 from, Vec3 to) {
        MovingObjectPosition mop = mc.theWorld.rayTraceBlocks(from, to, false, true, false);
        return mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK;
    }

    private static double sq(double d) {
        return d * d;
    }
}
