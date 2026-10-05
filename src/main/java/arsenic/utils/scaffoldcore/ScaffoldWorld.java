package arsenic.utils.scaffoldcore;

import arsenic.utils.botcore.Box;

public interface ScaffoldWorld {

    final class Hit {
        public int x, y, z, face;
        public double hx, hy, hz;
    }

    boolean isAir(int x, int y, int z);

    boolean isFullCube(int x, int y, int z);

    boolean canPlaceOnSide(int x, int y, int z, int face);

    boolean boxFree(Box b);

    boolean rayTrace(double sx, double sy, double sz, double ex, double ey, double ez, Hit out);
}
