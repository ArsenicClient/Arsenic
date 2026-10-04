package arsenic.utils.botcore;

/**
 * What the bot wants done this tick: movement keys (W/S/A/D), jump, sneak and sprint; the
 * camera is turned by setting yaw/pitch directly.
 */
public final class Controls {
    public boolean forward, jump, sneak, sprint;
    public boolean back, left, right;
    /** Turn to this yaw/pitch this tick (instantly), when set. */
    public boolean setYaw, setPitch;
    public float yaw, pitch;

    /** Place a block: into cell (placeX, placeY, placeZ) by clicking the given face of the block it touches. */
    public boolean place;
    public int placeX, placeY, placeZ;
    public int againstX, againstY, againstZ, againstFace;
    public double hitX, hitY, hitZ;

    /** Mine (keep hitting) this block, one of the bot's own. */
    public boolean mine;
    public int mineX, mineY, mineZ, mineFace;

    public void clear() {
        forward = back = left = right = jump = sneak = sprint = setYaw = setPitch = place = mine = false;
    }
}
