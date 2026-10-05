package arsenic.utils.botcore;

public interface Goal {
    boolean reached(Terrain t, int x, int y, int z, double feet);

    double heuristic(int x, int y, int z, double feet, Tuning tuning, double speed);

    double[] centre();

    default boolean reachedFrom(Terrain t, double ex, double ey, double ez) {
        return true;
    }

    final class NearBlock implements Goal {
        public final int bx, by, bz;
        public final double reach;

        public NearBlock(int bx, int by, int bz, double reach) {
            this.bx = bx;
            this.by = by;
            this.bz = bz;
            this.reach = reach;
        }

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
