package arsenic.utils.botcore;

/** Snapshot of the player for one tick. */
public final class PlayerView {
    public double x, y, z;
    public double motionX, motionY, motionZ;
    public float yaw, pitch;
    public boolean onGround;
    public boolean collidedHorizontally;
    public boolean sprinting;
    /** Movement speed relative to normal (1 = no effects). */
    public double speed = 1;
    /** Blocks available to build with. */
    public int blocks;
    public boolean pickaxe;

    public double eyeY() {
        return y + Terrain.EYE;
    }
}
