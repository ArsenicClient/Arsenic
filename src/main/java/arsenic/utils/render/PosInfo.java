package arsenic.utils.render;

public class PosInfo {
    int x, y;

    public PosInfo(int x , int y) {
        this.x = x;
        this.y = y;
    }

    public void moveY(int m) {
        y += m;
    }

    public void moveX(int m) {
        x += m;
    }

    public int getY() {
        return y;
    }

    public int getX() {
        return x;
    }
}
