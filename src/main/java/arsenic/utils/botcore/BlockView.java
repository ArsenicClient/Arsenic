package arsenic.utils.botcore;

import java.util.List;

/**
 * Read-only view of the world for the bot. The mod implements it on top of Minecraft (collision
 * boxes come straight from the game).
 * Must be safe to call from the planning thread.
 */
public interface BlockView {
    /** Ladder or vine: the player climbs when inside it. */
    int CLIMBABLE = 1;
    /** Water or lava. Treated as impassable. */
    int LIQUID = 2;
    /** Hurts or traps: lava, fire, cactus, cobweb. Never enter. */
    int DANGER = 4;
    /** Empty or replaceable (air, tall grass): a block can be placed here. */
    int REPLACEABLE = 8;
    /** Solid and not something a right click would open: a block can be placed against it. */
    int PLACE_AGAINST = 16;
    /** A block the bot placed itself; it may mine it back out with a pickaxe. */
    int OWN_BLOCK = 32;
    /** Not loaded yet. Treated as solid. */
    int UNLOADED = 64;

    /** Adds the collision boxes of the block at (x, y, z), in world coordinates. */
    void collisionBoxes(int x, int y, int z, List<Box> out);

    /** Adds the boxes a ray (line of sight) stops at, in world coordinates. */
    void rayBoxes(int x, int y, int z, List<Box> out);

    /** Bitmask of the flags above for the block at (x, y, z). */
    int flags(int x, int y, int z);
}
