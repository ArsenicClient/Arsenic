package arsenic.utils.botcore;

/** Where the bot is trying to get to. */
public interface Goal {
    /** Whether standing with feet at block (x, y, z), height {@code feet}, counts as arrived. */
    boolean reached(Terrain t, int x, int y, int z, double feet);

    /** Estimated ticks still to go from there (lower is closer). */
    double heuristic(int x, int y, int z, double feet, Tuning tuning, double speed);

    /** A point to show/aim at. */
    double[] centre();

    /** Whether the player's actual eye position is good enough (default: no extra condition). */
    default boolean reachedFrom(Terrain t, double ex, double ey, double ez) {
        return true;
    }

    /**
     * Arrive within reach of a block (a chest) with a clear line of sight to it, so it can be
     * opened from where the bot stops.
     */
    final class NearBlock implements Goal {
        public final int bx, by, bz;
        public final double reach;

        public NearBlock(int bx, int by, int bz, double reach) {
            this.bx = bx;
            this.by = by;
            this.bz = bz;
            this.reach = reach;
        }

        /** The planner only stops where the chest is still in sight a little off the middle too. */
        private static final double[][] AROUND = {{0, 0}, {0.2, 0}, {-0.2, 0}, {0, 0.2}, {0, -0.2}};

        @Override
        public boolean reached(Terrain t, int x, int y, int z, double feet) {
            for (double[] o : AROUND) {
                if (!reachedFrom(t, x + 0.5 + o[0], feet + Terrain.EYE, z + 0.5 + o[1])) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean reachedFrom(Terrain t, double ex, double ey, double ez) {
            double cx = bx + 0.5, cy = by + 0.5, cz = bz + 0.5;
            double dx = cx - ex, dy = cy - ey, dz = cz - ez;
            if (dx * dx + dy * dy + dz * dz > reach * reach) {
                return false;
            }
            return t.rayTrace(ex, ey, ez, cx, cy, cz, null) == Terrain.key(bx, by, bz);
        }

        @Override
        public double heuristic(int x, int y, int z, double feet, Tuning tuning, double speed) {
            double dx = bx + 0.5 - (x + 0.5), dz = bz + 0.5 - (z + 0.5);
            double flat = Math.max(0, Math.sqrt(dx * dx + dz * dz) - reach);
            double up = (by + 0.5) - (feet + Terrain.EYE);
            double vertical = up > reach ? (up - reach) * tuning.sprint : 0;
            return flat * tuning.sprint / speed + vertical;
        }

        @Override
        public double[] centre() {
            return new double[]{bx + 0.5, by + 0.5, bz + 0.5};
        }
    }

    /**
     * Arrive within reach of a point in the air (a player's body) with nothing in between, so
     * whoever is standing there can be hit from where the bot stops.
     */
    final class NearPoint implements Goal {
        public final double px, py, pz;
        public final double reach;

        public NearPoint(double px, double py, double pz, double reach) {
            this.px = px;
            this.py = py;
            this.pz = pz;
            this.reach = reach;
        }

        @Override
        public boolean reached(Terrain t, int x, int y, int z, double feet) {
            return reachedFrom(t, x + 0.5, feet + Terrain.EYE, z + 0.5);
        }

        @Override
        public boolean reachedFrom(Terrain t, double ex, double ey, double ez) {
            double dx = px - ex, dy = py - ey, dz = pz - ez;
            if (dx * dx + dy * dy + dz * dz > reach * reach) {
                return false;
            }
            return t.rayTrace(ex, ey, ez, px, py, pz, null) == Long.MIN_VALUE;
        }

        @Override
        public double heuristic(int x, int y, int z, double feet, Tuning tuning, double speed) {
            double dx = px - (x + 0.5), dz = pz - (z + 0.5);
            double flat = Math.max(0, Math.sqrt(dx * dx + dz * dz) - reach);
            double up = py - (feet + Terrain.EYE);
            double vertical = up > reach ? (up - reach) * tuning.sprint : 0;
            return flat * tuning.sprint / speed + vertical;
        }

        @Override
        public double[] centre() {
            return new double[]{px, py, pz};
        }
    }

    /** Stand in one exact block. */
    final class Block implements Goal {
        public final int gx, gy, gz;

        public Block(int gx, int gy, int gz) {
            this.gx = gx;
            this.gy = gy;
            this.gz = gz;
        }

        @Override
        public boolean reached(Terrain t, int x, int y, int z, double feet) {
            return x == gx && y == gy && z == gz;
        }

        @Override
        public double heuristic(int x, int y, int z, double feet, Tuning tuning, double speed) {
            double dx = gx - x, dz = gz - z;
            double up = gy - y;
            return Math.sqrt(dx * dx + dz * dz) * tuning.sprint / speed + (up > 0 ? up * tuning.sprint : 0);
        }

        @Override
        public double[] centre() {
            return new double[]{gx + 0.5, gy, gz + 0.5};
        }
    }
}
