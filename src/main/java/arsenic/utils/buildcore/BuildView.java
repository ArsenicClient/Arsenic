package arsenic.utils.buildcore;

import arsenic.utils.botcore.BlockView;

/** The world as the builder sees it: the bot's block view plus which build block each cell holds. */
public interface BuildView extends BlockView {
    /**
     * The block at (x, y, z) as an id in this blueprint's palette: {@link Blueprint#AIR} for air or anything
     * replaceable, -2 for a block that is not in the palette.
     */
    int idAt(int x, int y, int z);
}
