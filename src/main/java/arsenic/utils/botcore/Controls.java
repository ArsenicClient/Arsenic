package arsenic.utils.botcore;

public final class Controls {
    public boolean forward, jump, sneak, sprint;
    public boolean back, left, right;
    public boolean setYaw, setPitch;
    public float yaw, pitch;

    public boolean place;
    public int placeX, placeY, placeZ;
    public int againstX, againstY, againstZ, againstFace;
    public double hitX, hitY, hitZ;

    public boolean mine;
    public int mineX, mineY, mineZ, mineFace;

    public void clear() {
        forward = back = left = right = jump = sneak = sprint = setYaw = setPitch = place = mine = false;
    }
}
