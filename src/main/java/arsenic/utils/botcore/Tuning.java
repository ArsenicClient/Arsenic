package arsenic.utils.botcore;

/**
 * Every number the planner and follower depend on, in one place. Costs are in ticks at normal
 * speed.
 */
public final class Tuning {
    // ---- movement costs (ticks) ----
    /** Sprinting one block on flat ground (vanilla: about 5.6 blocks/s). */
    public double sprint = 3.246;
    /** Diagonal step cost as a multiple of a straight one (geometry says 1.414; turning adds a bit). */
    public double diagonalFactor = 1.555;
    public double ascendExtra = 2.002;
    public double dropPerFall = 0.91;
    public double parkourExtra = 1.455;
    public double parkourPerGap = 0.77;
    public double climbUp = 8.5;
    public double climbDown = 6.7;
    /** Extra for walking through a vine/ladder block (speed is capped there). */
    public double climbWalkExtra = 3.5;
    public double pillar = 12.0;
    public double bridge = 12.6;
    /** Extra cost per block placed, so blocks are only spent when it clearly saves time. */
    public double placePenalty = 9.8;
    public double minePerBlock = 12.0;

    /** How close (eye to chest centre, with line of sight) the planner aims to stop. */
    public double goalReach = 3.78;

    // ---- search ----
    /** Weight on the heuristic (1 = plain A*, higher = greedier and faster to search). */
    public double heuristicWeight = 1.092;
    public long searchMillis = 1500;
    public int maxNodes = 200_000;
    public int maxDrop = 256;
    // ---- parkour reach: longest jump, centre to centre, at
    // normal speed, per landing height (index 0 = 1 up, 1 = level, 2 = 1 down, 3 = 2+ down) ----
    public double[] parkourReach = {3.9, 4.47, 5.1, 5.1};
    /** Extra reach per 1.0 of extra speed (measured: about half a block per +0.2). */
    public double parkourReachPerSpeed = 2.5;
    /** Kept short of the measured limit, for safety. */
    public double parkourMargin = 0.1;

    // ---- follower ----
    /** How far past the centre of the take-off block to jump for parkour (0.5 = the edge). */
    public double parkourTakeoff = 0.24;
    /** Earliest point on the take-off block (along the jump) a parkour jump may start. */
    public double parkourEarliest = -0.571;
    /** How far from the landing block's middle a predicted landing may be and still be taken. */
    public double parkourWindow = 0.429;
    /** Yaws tried either side of the target when lining up a jump (2 degrees apart). */
    public int parkourYawSteps = 20;
    /** Jump for a 1-block ascend when this close (horizontally) to the step. */
    public double ascendJumpDist = 0.686;
    /** Look this many path steps ahead for a straight line to cut across open ground. */
    public int smoothLookahead = 27;
    /** Extra clearance either side of the player when cutting corners. */
    public double smoothMargin = 0.1;
    // sprint-jumping (the Sprint Jump toggle)
    /** Steps ahead that must be plain walking (no jumps, drops or building) to hop. */
    public int hopLookSteps = 2;
    /** Open running costs this much of normal when sprint-jumping (7.1 against 5.6 blocks/s). */
    public double hopRunFactor = 1.1;
    /** Only hop with at least this much clear straight run ahead. */
    public double hopMinRun = 1.4;
    /** How far past the end of the clear run, and to the side of it, a hop may land. */
    public double hopOvershoot = 0.585;
    public double hopSideways = 0.201;
    /** The landing must still be held up this far short of and past the predicted spot. */
    public double hopLandSlack = 0.3;
    /** Turn and steer in the air (yaw, keys): through corners mid-hop, onto jump landings. */
    public boolean airControl = true;
    /** Directions tried when steering a jump or drop in the air. */
    public int airSteerYaws = 36;
    /** Ticks without progress before the watchdog steps in. */
    public int stuckTicks = 60;
    /** Blocks off the path before re-planning. */
    public double offPathDistance = 3.25;
    /** Replan ahead when this many steps of a partial path are left. */
    public int planAheadSteps = 6;

    public Tuning copy() {
        Tuning t = new Tuning();
        t.sprint = sprint;
        t.ascendExtra = ascendExtra;
        t.diagonalFactor = diagonalFactor;
        t.dropPerFall = dropPerFall;
        t.parkourExtra = parkourExtra;
        t.parkourPerGap = parkourPerGap;
        t.climbUp = climbUp;
        t.climbDown = climbDown;
        t.climbWalkExtra = climbWalkExtra;
        t.pillar = pillar;
        t.bridge = bridge;
        t.placePenalty = placePenalty;
        t.minePerBlock = minePerBlock;
        t.heuristicWeight = heuristicWeight;
        t.goalReach = goalReach;
        t.searchMillis = searchMillis;
        t.maxNodes = maxNodes;
        t.maxDrop = maxDrop;
        t.parkourReach = parkourReach.clone();
        t.parkourReachPerSpeed = parkourReachPerSpeed;
        t.parkourMargin = parkourMargin;
        t.parkourTakeoff = parkourTakeoff;
        t.parkourEarliest = parkourEarliest;
        t.parkourWindow = parkourWindow;
        t.parkourYawSteps = parkourYawSteps;
        t.ascendJumpDist = ascendJumpDist;
        t.smoothLookahead = smoothLookahead;
        t.smoothMargin = smoothMargin;
        t.stuckTicks = stuckTicks;
        t.offPathDistance = offPathDistance;
        t.planAheadSteps = planAheadSteps;
        t.hopLookSteps = hopLookSteps;
        t.hopRunFactor = hopRunFactor;
        t.hopMinRun = hopMinRun;
        t.hopOvershoot = hopOvershoot;
        t.hopSideways = hopSideways;
        t.hopLandSlack = hopLandSlack;
        t.airControl = airControl;
        t.airSteerYaws = airSteerYaws;
        return t;
    }

    /** Longest sprint-jump (centre to centre) landing {@code dy} blocks higher (negative = lower). */
    public double maxJump(double dy, double speed) {
        int i = dy > 0.5 ? 0 : dy > -0.5 ? 1 : dy > -1.5 ? 2 : 3;
        return parkourReach[i] + (speed - 1) * parkourReachPerSpeed - parkourMargin;
    }
}
