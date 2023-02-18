package arsenic.utils.render;

public class DimensionInfo {

    private int x, y, x1, y1;

    public DimensionInfo(int x, int y, int x1, int y1) {
        this.x = x;
        this.y = y;
        this.x1 = x1;
        this.y1 = y1;
    }

    public int getX() {
        return x;
    }
    public void setX(int x) {
        this.x = x;
    }
    public void moveX(int v) {
        x += v;
        x1 += v;
    }
    public void moveY(int v) {
        y += v;
        y1 += v;
    }
    public int getY() {
        return y;
    }
    public void setY(int y) {
        this.y = y;
    }
    public int getX1() {
        return x1;
    }
    public void setX1(int x1) {
        this.x1 = x1;
    }
    public int getY1() {
        return y1;
    }
    public void setY1(int y1) {
        this.y1 = y1;
    }
    public int getWidth() {return x1 - x;}
    public int getHeight() {return y1 - y;}
}
