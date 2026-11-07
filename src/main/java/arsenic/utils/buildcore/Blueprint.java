package arsenic.utils.buildcore;

import java.util.ArrayList;
import java.util.List;

/**
 * What a build must end up as, cell by cell, in a box of sizeX x sizeY x sizeZ starting at its origin. Each cell
 * holds an index into the palette: {@link #AIR} must be empty, {@link #ANY} is not part of the build and is left as
 * it is. The palette holds block states as text ("minecraft:oak_stairs[facing=east]"), so the core stays free of
 * Minecraft; the mod parses them.
 */
public final class Blueprint {
    public static final int ANY = -1, AIR = 0;

    public final int sizeX, sizeY, sizeZ;
    private final int[] cells;
    public final List<String> palette = new ArrayList<>();

    public Blueprint(int sizeX, int sizeY, int sizeZ) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        cells = new int[sizeX * sizeY * sizeZ];
        palette.add("minecraft:air");
    }

    public boolean inside(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < sizeX && y < sizeY && z < sizeZ;
    }

    private int index(int x, int y, int z) {
        return (y * sizeZ + z) * sizeX + x;
    }

    /** The palette id at (x, y, z) relative to the origin; {@link #ANY} outside the box. */
    public int get(int x, int y, int z) {
        return inside(x, y, z) ? cells[index(x, y, z)] : ANY;
    }

    public void set(int x, int y, int z, int id) {
        cells[index(x, y, z)] = id;
    }

    /** The palette id of this state, added if new. Air in any spelling is {@link #AIR}. */
    public int idOf(String state) {
        if (isAir(state)) return AIR;
        int i = palette.indexOf(state);
        if (i >= 0) return i;
        palette.add(state);
        return palette.size() - 1;
    }

    public static boolean isAir(String state) {
        String name = blockName(state);
        return name.equals("minecraft:air") || name.equals("minecraft:cave_air") || name.equals("minecraft:void_air")
                || name.equals("minecraft:structure_void");
    }

    /** "minecraft:oak_stairs[facing=east]" -> "minecraft:oak_stairs". */
    public static String blockName(String state) {
        int i = state.indexOf('[');
        return i < 0 ? state : state.substring(0, i);
    }

    /** Cells that need a block. */
    public int solidCount() {
        int n = 0;
        for (int c : cells) {
            if (c > AIR) n++;
        }
        return n;
    }

    /** Trims empty and {@link #ANY} layers off every side, so the origin is the corner of the blocks. */
    public Blueprint trimmed() {
        int x0 = sizeX, y0 = sizeY, z0 = sizeZ, x1 = -1, y1 = -1, z1 = -1;
        for (int y = 0; y < sizeY; y++)
            for (int z = 0; z < sizeZ; z++)
                for (int x = 0; x < sizeX; x++) {
                    if (get(x, y, z) <= AIR) continue;
                    x0 = Math.min(x0, x);
                    y0 = Math.min(y0, y);
                    z0 = Math.min(z0, z);
                    x1 = Math.max(x1, x);
                    y1 = Math.max(y1, y);
                    z1 = Math.max(z1, z);
                }
        if (x1 < 0) return new Blueprint(0, 0, 0);
        Blueprint b = new Blueprint(x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1);
        b.palette.clear();
        b.palette.addAll(palette);
        for (int y = 0; y < b.sizeY; y++)
            for (int z = 0; z < b.sizeZ; z++)
                for (int x = 0; x < b.sizeX; x++)
                    b.set(x, y, z, get(x + x0, y + y0, z + z0));
        return b;
    }
}
