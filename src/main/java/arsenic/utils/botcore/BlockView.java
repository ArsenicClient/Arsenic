package arsenic.utils.botcore;

import java.util.List;

public interface BlockView {
    int CLIMBABLE = 1;
    int LIQUID = 2;
    int DANGER = 4;
    int REPLACEABLE = 8;
    int PLACE_AGAINST = 16;
    int OWN_BLOCK = 32;
    int UNLOADED = 64;

    void collisionBoxes(int x, int y, int z, List<Box> out);

    void rayBoxes(int x, int y, int z, List<Box> out);

    int flags(int x, int y, int z);
}
