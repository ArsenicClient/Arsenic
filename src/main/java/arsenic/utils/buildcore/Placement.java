package arsenic.utils.buildcore;

import arsenic.utils.botcore.Dir;

import java.util.ArrayList;
import java.util.List;

/**
 * How a block state has to be clicked into place, for the blocks whose state depends on the click: stairs (facing from
 * the player's heading, top or bottom half from where the face is hit), slabs (top or bottom from the hit) and blocks
 * that face the player (furnaces, carved pumpkins, glazed terracotta). Everything else is placed the same whichever
 * way it is clicked and has no constraints.
 * <p>
 * {@link #key} is the state cut down to the properties that matter and that a click decides, so a stair that the
 * world has with another shape (corner) still matches, but one facing the wrong way does not.
 */
public final class Placement {
    public static final Placement NONE = new Placement("", -1, 0, false);

    /** The way the player must be heading (a horizontal {@link Dir}) when clicking, or -1. */
    public final int look;
    /** 0 either, 1 bottom half, 2 top half. */
    public final int half;
    /** A single slab: clicking a slab of the same kind on its flat face would merge into a double slab. */
    public final boolean slab;
    public final String key;

    private Placement(String key, int look, int half, boolean slab) {
        this.key = key;
        this.look = look;
        this.half = half;
        this.slab = slab;
    }

    public boolean constrained() {
        return look >= 0 || half != 0;
    }

    /** Whether a click on {@code face} of an existing block can ever give this placement. */
    public boolean allows(int face) {
        if (half == 1 && face == Dir.DOWN) return false;
        if (half == 2 && face == Dir.UP) return false;
        // heading the way the face looks means moving away from it: the ray cannot reach it
        return look < 0 || face != look;
    }

    /** Whether a heading (yaw, degrees) gives the wanted facing, with a margin off the quadrant's edges. */
    public boolean heading(float yaw, double margin) {
        if (look < 0) return true;
        double d = Math.abs(wrap(yaw - centre(look)));
        return d <= margin;
    }

    /** Whether hitting a side face at this height above the placed cell gives the wanted half. */
    public boolean hitHeight(double frac, double margin) {
        if (half == 1) return frac <= 0.5 - margin;
        if (half == 2) return frac >= 0.5 + margin;
        return true;
    }

    private static double centre(int look) {
        switch (look) {
            case Dir.WEST: return 90;
            case Dir.NORTH: return 180;
            case Dir.EAST: return 270;
            default: return 0;
        }
    }

    private static double wrap(double a) {
        a %= 360;
        if (a >= 180) a -= 360;
        if (a < -180) a += 360;
        return a;
    }

    /** Whether this block is one a click decides the state of (so double slabs, which one click cannot make, are not). */
    public static boolean doubleSlab(String state) {
        return Blueprint.blockName(state).endsWith("_slab") && "double".equals(prop(state, "type"));
    }

    /** The properties of a block that matter when matching a placed block against the blueprint, by block name. */
    public static List<String> relevant(String block) {
        List<String> p = new ArrayList<>(2);
        if (block.endsWith("_stairs")) {
            p.add("facing");
            p.add("half");
        } else if (block.endsWith("_slab")) {
            p.add("type");
        } else if (facesPlayer(block)) {
            p.add("facing");
        }
        return p;
    }

    private static boolean facesPlayer(String block) {
        String n = block.replace("minecraft:", "");
        return n.equals("furnace") || n.equals("blast_furnace") || n.equals("smoker") || n.equals("carved_pumpkin")
                || n.equals("jack_o_lantern") || n.equals("loom") || n.equals("stonecutter") || n.equals("lectern")
                || n.endsWith("_glazed_terracotta");
    }

    public static Placement of(String state) {
        String block = Blueprint.blockName(state);
        List<String> props = relevant(block);
        if (props.isEmpty()) return new Placement(block, -1, 0, false);
        StringBuilder key = new StringBuilder(block).append('[');
        for (int i = 0; i < props.size(); i++) {
            String v = prop(state, props.get(i));
            if (v == null) v = defaultOf(props.get(i));
            key.append(i == 0 ? "" : ",").append(props.get(i)).append('=').append(v);
        }
        key.append(']');
        String facing = prop(state, "facing");
        int dir = dir(facing == null ? "north" : facing);
        if (block.endsWith("_stairs")) {
            return new Placement(key.toString(), dir, "top".equals(prop(state, "half")) ? 2 : 1, false);
        }
        if (block.endsWith("_slab")) {
            String t = prop(state, "type");
            return new Placement(key.toString(), -1, "top".equals(t) ? 2 : "double".equals(t) ? 0 : 1, !"double".equals(t));
        }
        return new Placement(key.toString(), Dir.opposite(dir), 0, false);
    }

    private static String defaultOf(String prop) {
        switch (prop) {
            case "half": return "bottom";
            case "type": return "bottom";
            default: return "north";
        }
    }

    private static int dir(String name) {
        switch (name) {
            case "south": return Dir.SOUTH;
            case "west": return Dir.WEST;
            case "east": return Dir.EAST;
            default: return Dir.NORTH;
        }
    }

    /** "minecraft:oak_stairs[facing=east,half=top]", "half" -> "top"; null if absent. */
    public static String prop(String state, String name) {
        int i = state.indexOf('[');
        if (i < 0) return null;
        for (String kv : state.substring(i + 1, state.length() - 1).split(",")) {
            int e = kv.indexOf('=');
            if (e > 0 && kv.substring(0, e).trim().equals(name)) return kv.substring(e + 1).trim();
        }
        return null;
    }
}
