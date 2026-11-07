package arsenic.utils.buildcore;

/**
 * Creative flight in 26.x, without collisions, to pick the keys that fly to a point: KeyboardInput's normalized move
 * vector, LocalPlayer.modifyInput (x0.98, then stretched to the unit square), Entity.getInputVector, Player's flying
 * speed (doubled while sprinting) and LivingEntity.travelInAir with Player.travel's 0.6 on vertical motion. Sneaking
 * does not slow flight (the player is not crouching while flying).
 */
public final class Flight {
    public static final float FLY_SPEED = 0.05F;

    /** Keys held for one tick. */
    public static final class Keys {
        public boolean forward, back, left, right, up, down, sprint;

        void set(int fwd, int strafe, int vert, boolean sprint) {
            forward = fwd > 0;
            back = fwd < 0;
            left = strafe > 0;
            right = strafe < 0;
            up = vert > 0;
            down = vert < 0;
            this.sprint = sprint;
        }
    }

    /** Motion and position change from one tick of these keys. */
    public static final class Step {
        public double dx, dy, dz, mx, my, mz;
        public boolean sprinting;
    }

    /** One tick of flight from motion (mx, my, mz) with the camera at yaw. */
    public static void step(double mx, double my, double mz, float yaw, boolean sprinting, Keys k, Step out) {
        float fwd = (k.forward ? 1 : 0) - (k.back ? 1 : 0);
        float strafe = (k.left ? 1 : 0) - (k.right ? 1 : 0);
        boolean forwardImpulse = fwd > 0;
        if (!sprinting && forwardImpulse && k.sprint) sprinting = true;
        if (sprinting && !forwardImpulse) sprinting = false;

        int vert = (k.up ? 1 : 0) - (k.down ? 1 : 0);
        if (vert != 0) my += vert * FLY_SPEED * 3.0F;
        if (mx * mx + mz * mz < 9.0E-6) {
            mx = 0;
            mz = 0;
        }
        if (Math.abs(my) < 0.003) my = 0;

        // KeyboardInput.tick then LocalPlayer.modifyInput
        float ix = strafe, iz = fwd;
        float len = (float) Math.sqrt(ix * ix + iz * iz);
        if (len >= 1.0E-4F) {
            ix /= len;
            iz /= len;
            ix *= 0.98F;
            iz *= 0.98F;
            float l = (float) Math.sqrt(ix * ix + iz * iz);
            float dxn = ix / l, dzn = iz / l;
            float ax = Math.abs(dxn), az = Math.abs(dzn);
            float tan = az > ax ? ax / az : az / ax;
            float m = Math.min(l * (float) Math.sqrt(1.0F + tan * tan), 1.0F);
            ix = dxn * m;
            iz = dzn * m;
        } else {
            ix = 0;
            iz = 0;
        }
        float speed = sprinting ? FLY_SPEED * 2.0F : FLY_SPEED;
        double lsq = (double) ix * ix + (double) iz * iz;
        if (lsq >= 1.0E-7) {
            double vx = ix, vz = iz;
            if (lsq > 1.0) {
                double l = Math.sqrt(lsq);
                vx /= l;
                vz /= l;
            }
            vx *= speed;
            vz *= speed;
            float sin = Ray.sin(yaw * (float) (Math.PI / 180.0));
            float cos = Ray.cos(yaw * (float) (Math.PI / 180.0));
            mx += vx * cos - vz * sin;
            mz += vz * cos + vx * sin;
        }
        out.dx = mx;
        out.dy = my;
        out.dz = mz;
        out.mx = mx * 0.91F;
        out.mz = mz * 0.91F;
        out.my = my * 0.6;
        out.sprinting = sprinting;
    }

    private final Keys trial = new Keys();
    private final Step s = new Step();

    /**
     * Keys that bring the player toward (tx, ty, tz) and slow down on arrival: each tick the motion closest to
     * {@code gain} times the distance left, at most {@code maxSpeed} blocks a tick horizontally. {@code floorGap} is
     * how far the feet are above the highest block under the player.
     */
    public void steer(double x, double y, double z, double mx, double my, double mz, float yaw, boolean sprinting,
                      double tx, double ty, double tz, double gain, double maxSpeed, boolean allowSprint, double floorGap,
                      Keys out) {
        double best = Double.MAX_VALUE;
        for (int fwd = -1; fwd <= 1; fwd++) {
            for (int strafe = -1; strafe <= 1; strafe++) {
                for (int vert = -1; vert <= 1; vert++) {
                    for (int sp = 0; sp <= (allowSprint && fwd > 0 ? 1 : 0); sp++) {
                        trial.set(fwd, strafe, vert, sp == 1);
                        step(mx, my, mz, yaw, sprinting, trial, s);
                        double nx = x + s.dx, ny = y + s.dy, nz = z + s.dz;
                        double ex = tx - nx, ez = tz - nz, ey = ty - ny;
                        // never sink onto a block (touching ground ends the flight), even with up held next tick
                        double fall = Math.max(0, -(s.my + FLY_SPEED * 3.0F));
                        if (s.dy < 0 && floorGap - (y - ny) - fall < 0.03) continue;
                        double hd = Math.sqrt(ex * ex + ez * ez);
                        double want = Math.min(maxSpeed, hd * gain);
                        double wx = hd > 1e-9 ? ex / hd * want : 0, wz = hd > 1e-9 ? ez / hd * want : 0;
                        double wy = Math.max(-0.225, Math.min(0.225, ey * 0.45));
                        double cost = sq(s.mx - wx) + sq(s.mz - wz) + 1.5 * sq(s.my - wy) + 0.02 * (ex * ex + ez * ez) + 1.0 * ey * ey;
                        // prefer fewer keys when it makes no difference
                        cost += 1e-6 * (Math.abs(fwd) + Math.abs(strafe) + Math.abs(vert) + sp);
                        if (cost < best) {
                            best = cost;
                            out.set(fwd, strafe, vert, sp == 1);
                        }
                    }
                }
            }
        }
    }

    private static double sq(double d) {
        return d * d;
    }
}
