package arsenic.utils.botcore;

/** One move of a path: where it ends, how to get there, and what (if anything) to place. */
public final class Step {
    public static final int START = 0, WALK = 1, DIAGONAL = 2, ASCEND = 3, DROP = 4, PARKOUR = 5,
            CLIMB_UP = 6, CLIMB_DOWN = 7, PILLAR = 8, BRIDGE = 9, MINE_DOWN = 10;
    public static final String[] NAMES = {"start", "walk", "diagonal", "ascend", "drop", "parkour",
            "climb-up", "climb-down", "pillar", "bridge", "mine-down"};

    /** Feet block this step ends in. */
    public final int x, y, z;
    /** Feet height (world Y) when standing at the end of this step. */
    public final double feet;
    public final int type;
    /** Block to place first (BRIDGE, PILLAR), or Integer.MIN_VALUE in px when none. */
    public final int px, py, pz;
    public final double cost;

    public Step(int x, int y, int z, double feet, int type, int px, int py, int pz, double cost) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.feet = feet;
        this.type = type;
        this.px = px;
        this.py = py;
        this.pz = pz;
        this.cost = cost;
    }

    public Step(int x, int y, int z, double feet, int type, double cost) {
        this(x, y, z, feet, type, Integer.MIN_VALUE, 0, 0, cost);
    }

    public boolean places() {
        return px != Integer.MIN_VALUE;
    }

    public boolean same(int x, int y, int z) {
        return this.x == x && this.y == y && this.z == z;
    }

    /** Ground-level move that keeps to one height (smoothing can cut across these). */
    public boolean flat() {
        return type == WALK || type == DIAGONAL;
    }

    @Override
    public String toString() {
        return NAMES[type] + "(" + x + "," + y + "," + z + ")";
    }
}
