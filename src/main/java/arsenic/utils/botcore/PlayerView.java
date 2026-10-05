package arsenic.utils.botcore;

public final class PlayerView {
    public double x, y, z;
    public double motionX, motionY, motionZ;
    public float yaw, pitch;
    public boolean onGround;
    public boolean collidedHorizontally;
    public boolean sprinting;
    public double speed = 1;
    public int blocks;
    public boolean pickaxe;

    public double eyeY() {
        return y + Terrain.EYE;
    }
}
