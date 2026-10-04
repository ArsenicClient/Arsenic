package arsenic.utils.botcore;

/**
 * Axis-aligned box with the same semantics as Minecraft 1.8.9's AxisAlignedBB (open intervals for
 * intersection, the calculate*Offset sweep helpers used by Entity.moveEntity), Minecraft-free for
 * the bot core.
 */
public final class Box {
    public final double minX, minY, minZ, maxX, maxY, maxZ;

    public Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
    }

    /** A box given in block-local 0..1 coordinates, placed at block (x, y, z). */
    public static Box local(int x, int y, int z, double x0, double y0, double z0, double x1, double y1, double z1) {
        return new Box(x + x0, y + y0, z + z0, x + x1, y + y1, z + z1);
    }

    /** The player's collision box with feet centred at (x, y, z). */
    public static Box player(double x, double y, double z) {
        return new Box(x - 0.3, y, z - 0.3, x + 0.3, y + 1.8, z + 0.3);
    }

    public Box offset(double x, double y, double z) {
        return new Box(minX + x, minY + y, minZ + z, maxX + x, maxY + y, maxZ + z);
    }

    /** Stretches the box in the direction of a movement (AxisAlignedBB.addCoord). */
    public Box addCoord(double x, double y, double z) {
        double a = minX, b = minY, c = minZ, d = maxX, e = maxY, f = maxZ;
        if (x < 0) a += x; else if (x > 0) d += x;
        if (y < 0) b += y; else if (y > 0) e += y;
        if (z < 0) c += z; else if (z > 0) f += z;
        return new Box(a, b, c, d, e, f);
    }

    public Box expand(double x, double y, double z) {
        return new Box(minX - x, minY - y, minZ - z, maxX + x, maxY + y, maxZ + z);
    }

    public boolean intersects(Box o) {
        return o.maxX > minX && o.minX < maxX && o.maxY > minY && o.minY < maxY && o.maxZ > minZ && o.minZ < maxZ;
    }

    public double calculateXOffset(Box other, double dx) {
        if (other.maxY > minY && other.minY < maxY && other.maxZ > minZ && other.minZ < maxZ) {
            if (dx > 0 && other.maxX <= minX) {
                double d = minX - other.maxX;
                if (d < dx) dx = d;
            } else if (dx < 0 && other.minX >= maxX) {
                double d = maxX - other.minX;
                if (d > dx) dx = d;
            }
        }
        return dx;
    }

    public double calculateYOffset(Box other, double dy) {
        if (other.maxX > minX && other.minX < maxX && other.maxZ > minZ && other.minZ < maxZ) {
            if (dy > 0 && other.maxY <= minY) {
                double d = minY - other.maxY;
                if (d < dy) dy = d;
            } else if (dy < 0 && other.minY >= maxY) {
                double d = maxY - other.minY;
                if (d > dy) dy = d;
            }
        }
        return dy;
    }

    public double calculateZOffset(Box other, double dz) {
        if (other.maxX > minX && other.minX < maxX && other.maxY > minY && other.minY < maxY) {
            if (dz > 0 && other.maxZ <= minZ) {
                double d = minZ - other.maxZ;
                if (d < dz) dz = d;
            } else if (dz < 0 && other.minZ >= maxZ) {
                double d = maxZ - other.minZ;
                if (d > dz) dz = d;
            }
        }
        return dz;
    }

    /**
     * Where the segment from (ax,ay,az) to (bx,by,bz) first enters this box, as a fraction 0..1 of
     * the way along, or -1 if it misses. {@code faceOut[0]} gets the face hit (Dir index).
     */
    public double clip(double ax, double ay, double az, double bx, double by, double bz, int[] faceOut) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double tMin = 0, tMax = 1;
        int face = -1;
        double[] o = {ax, ay, az}, d = {dx, dy, dz}, lo = {minX, minY, minZ}, hi = {maxX, maxY, maxZ};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1e-12) {
                if (o[i] < lo[i] || o[i] > hi[i]) return -1;
                continue;
            }
            double t1 = (lo[i] - o[i]) / d[i], t2 = (hi[i] - o[i]) / d[i];
            int f1, f2;
            // face index: 0 down,1 up,2 north,3 south,4 west,5 east (Minecraft EnumFacing order)
            if (i == 0) { f1 = Dir.WEST; f2 = Dir.EAST; }
            else if (i == 1) { f1 = Dir.DOWN; f2 = Dir.UP; }
            else { f1 = Dir.NORTH; f2 = Dir.SOUTH; }
            if (t1 > t2) { double t = t1; t1 = t2; t2 = t; int f = f1; f1 = f2; f2 = f; }
            if (t1 > tMin) { tMin = t1; face = f1; }
            if (t2 < tMax) tMax = t2;
            if (tMin > tMax) return -1;
        }
        if (faceOut != null) faceOut[0] = face;
        return tMin;
    }

    @Override
    public String toString() {
        return String.format("Box[%.3f,%.3f,%.3f -> %.3f,%.3f,%.3f]", minX, minY, minZ, maxX, maxY, maxZ);
    }
}
