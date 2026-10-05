package arsenic.utils.scaffoldcore;

public final class SneakPresses {

    public interface Edge {
        boolean within(double ticks);
    }

    public double hardLead = 1.0;
    public double leadCap = 3;
    public double needLead = 3;
    public double holdScale = 0.5;
    public int minHold = 2;
    public int releaseTicks = 2;

    private int sinceSneak = 1000;
    private int holdLeft;
    private boolean latched;
    private int pressPlaced, sinceHold = 1000, pressAge;

    public void reset() {
        sinceSneak = 1000;
        holdLeft = 0;
        latched = false;
        pressPlaced = 0;
        sinceHold = 1000;
        pressAge = 0;
    }

    public void airborne() {
        latched = false;
        holdLeft = 0;
        pressPlaced = 0;
        pressAge = 0;
        sinceSneak++;
    }

    public boolean next(Edge edge, boolean eagle, double safety, boolean diagonal, boolean placed) {
        boolean hard = edge.within(hardLead);
        double lead = Math.min(safety, leadCap);
        if (safety > 1) lead = Math.max(lead, needLead);
        boolean soft = eagle && lead > 0 && sinceSneak >= releaseTicks && edge.within(lead);
        if (sinceSneak == 0 && placed) {
            latched = false;
            pressPlaced++;
            sinceHold = 0;
            holdLeft = eagle ? Math.max(minHold, (int) Math.round(safety * holdScale)) : minHold;
        }
        if (holdLeft == 0 && (hard || soft)) latched = true;
        if (latched && !hard && sinceSneak == 0 && pressAge >= minHold && !edge.within(Math.max(hardLead, lead)))
            latched = false;
        boolean quota = eagle && safety > (diagonal ? 2 : 1) && pressPlaced >= (diagonal ? 2 : 1);
        if (quota && holdLeft > 0 && !latched && sinceHold >= minHold
                && !edge.within(hardLead + releaseTicks) && edge.within(hardLead + releaseTicks + 2))
            holdLeft = 0;
        boolean shift = latched || holdLeft > 0;
        if (holdLeft > 0) holdLeft--;
        sinceHold++;
        if (!shift) pressPlaced = 0;
        pressAge = shift ? (sinceSneak == 0 ? pressAge + 1 : 1) : 0;
        sinceSneak = shift ? 0 : sinceSneak + 1;
        return shift;
    }

    public boolean peek(Edge edge, boolean eagle, double safety, boolean diagonal) {
        boolean l = latched;
        int h = holdLeft, p = pressPlaced, sh = sinceHold, pa = pressAge, ss = sinceSneak;
        try {
            return next(edge, eagle, safety, diagonal, false);
        } finally {
            latched = l;
            holdLeft = h;
            pressPlaced = p;
            sinceHold = sh;
            pressAge = pa;
            sinceSneak = ss;
        }
    }

    public static double offGrid(float forward, float strafe, float cameraYaw) {
        if (forward == 0 && strafe == 0) return -1;
        double yr = Math.toRadians(cameraYaw), sn = Math.sin(yr), cs = Math.cos(yr);
        double ang = Math.toDegrees(Math.atan2(forward * cs + strafe * sn, forward * -sn + strafe * cs));
        return Math.abs(ang - Math.round(ang / 90.0) * 90.0);
    }

    public static boolean diagonal(float forward, float strafe, float cameraYaw) {
        return offGrid(forward, strafe, cameraYaw) > 22.5;
    }
}
