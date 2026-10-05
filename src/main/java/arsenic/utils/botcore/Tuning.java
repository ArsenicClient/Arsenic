package arsenic.utils.botcore;

public final class Tuning {
    public double sprint = 3.246;
    public double diagonalFactor = 1.555;
    public double ascendExtra = 2.002;
    public double dropPerFall = 0.91;
    public double parkourExtra = 1.455;
    public double parkourPerGap = 0.77;
    public double climbUp = 8.5;
    public double climbDown = 6.7;
    public double climbWalkExtra = 3.5;
    public double pillar = 12.0;
    public double bridge = 12.6;
    public double placePenalty = 9.8;
    public double minePerBlock = 12.0;

    public double goalReach = 3.78;

    public double heuristicWeight = 1.092;
    public long searchMillis = 1500;
    public int maxNodes = 200_000;
    public int maxDrop = 256;
    public double[] parkourReach = {3.9, 4.47, 5.1, 5.1};
    public double parkourReachPerSpeed = 2.5;
    public double parkourMargin = 0.1;

    public double parkourTakeoff = 0.24;
    public double parkourEarliest = -0.571;
    public double parkourWindow = 0.429;
    public int parkourYawSteps = 20;
    public double ascendJumpDist = 0.686;
    public int smoothLookahead = 27;
    public double smoothMargin = 0.1;
    public int hopLookSteps = 2;
    public double hopRunFactor = 1.1;
    public double hopMinRun = 1.4;
    public double hopOvershoot = 0.585;
    public double hopSideways = 0.201;
    public double hopLandSlack = 0.3;
    public boolean airControl = true;
    public int airSteerYaws = 36;
    public int stuckTicks = 60;
    public double offPathDistance = 3.25;
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

    public double maxJump(double dy, double speed) {
        int i = dy > 0.5 ? 0 : dy > -0.5 ? 1 : dy > -1.5 ? 2 : 3;
        return parkourReach[i] + (speed - 1) * parkourReachPerSpeed - parkourMargin;
    }
}
