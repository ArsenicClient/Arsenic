package arsenic.utils.scaffoldcore;

public final class LaneNudge {

    public double diagonalOffset;
    public boolean diagonals;
    public double window = 4;

    public boolean snap;
    public boolean tracking;
    public double heading, offset;

    private boolean anchored;
    private double anchorX, anchorZ, anchorAng, laneAng;

    public void reset() {
        anchored = false;
    }

    private static int floor(double d) {
        int i = (int) d;
        return d < i ? i - 1 : i;
    }

    private static double wrap180(double v) {
        v %= 360.0;
        if (v >= 180.0) v -= 360.0;
        if (v < -180.0) v += 360.0;
        return v;
    }

    public float[] compute(float forward, float strafe, float cameraYaw, double x, double z, boolean onGround,
                           double dead) {
        tracking = false;
        if (!onGround) return null;
        if (forward == 0 && strafe == 0) {
            anchored = false;
            return null;
        }
        double yr = Math.toRadians(cameraYaw), sn = Math.sin(yr), cs = Math.cos(yr);
        double vx = forward * -sn + strafe * cs, vz = forward * cs + strafe * sn;
        double ang = Math.toDegrees(Math.atan2(vz, vx));
        boolean straight = Math.abs(ang - Math.round(ang / 90.0) * 90.0) <= window;
        boolean diag = Math.abs(ang - Math.round(ang / 45.0) * 45.0) <= window;
        if (!straight && !(diagonals && diag)) {
            anchored = false;
            return null;
        }
        double lr0 = Math.toRadians(snap && anchored ? laneAng : ang);
        double d = anchored ? (x - anchorX) * -Math.sin(lr0) + (z - anchorZ) * Math.cos(lr0) : 0;
        double snapped = Math.round(ang / 45.0) * 45.0;
        boolean onGrid = Math.abs(ang - snapped) <= window;
        boolean turned = snap && onGrid ? Math.abs(wrap180(snapped - laneAng)) > 1e-6 : Math.abs(wrap180(ang - anchorAng)) > 1.0;
        if (!anchored || turned || Math.abs(d) > 1.5) {
            anchorX = onGrid ? floor(x) + 0.5 : x;
            anchorZ = onGrid ? floor(z) + 0.5 : z;
            anchorAng = ang;
            laneAng = snap && onGrid ? snapped : ang;
            anchored = true;
        }
        if (!snap) laneAng = ang;
        double hr = Math.toRadians(laneAng), ux = Math.cos(hr), uz = Math.sin(hr), nx = -uz, nz = ux;
        d = (x - anchorX) * nx + (z - anchorZ) * nz;
        boolean diagonal = Math.abs(Math.round(anchorAng / 45.0) % 2) == 1 && Math.abs(anchorAng - Math.round(anchorAng / 45.0) * 45.0) <= window;
        if (diagonal) d -= diagonalOffset;
        tracking = true;
        heading = laneAng;
        offset = d;
        if (Math.abs(d) < dead) return null;
        double phi = Math.toRadians(-Math.signum(d) * 45.0);
        double wx = Math.cos(phi) * ux + Math.sin(phi) * nx, wz = Math.cos(phi) * uz + Math.sin(phi) * nz;
        double nf = wx * -sn + wz * cs, ns = wx * cs + wz * sn;
        return new float[]{Math.abs(nf) > 0.38 ? (float) Math.signum(nf) : 0, Math.abs(ns) > 0.38 ? (float) Math.signum(ns) : 0};
    }

    public float steerYaw(float forward, float strafe, float cameraYaw, float baseYaw, double gain, double max, double dead, double limit) {
        if (!tracking || forward == 0 && strafe == 0) return Float.NaN;
        double moveYaw = cameraYaw + Math.toDegrees(Math.atan2(-strafe, forward));
        double travel = baseYaw + 45.0 * Math.round(wrap180(moveYaw - baseYaw) / 45.0);
        double off = Math.abs(offset) < dead ? 0 : offset - Math.signum(offset) * dead;
        double corr = Math.max(-max, Math.min(max, -gain * off));
        double dYaw = wrap180(heading - 90.0 + corr - travel);
        if (Math.abs(dYaw) > limit) return Float.NaN;
        return (float) (baseYaw + dYaw);
    }
}
