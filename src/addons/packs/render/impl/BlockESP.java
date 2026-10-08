import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.TextProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.render.RenderUtils;
import net.minecraft.block.Block;
import net.minecraft.util.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Highlights the block types you list (comma separated, Minecraft names such as bed, diamond_ore, tnt) through walls
 * within a range. The world is scanned every half second, so the highlights lag a little behind block changes.
 * Names that do not match a block are ignored.
 */
@ModuleInfo(name = "BlockESP", description = "Highlights chosen block types through walls", category = ModuleCategory.RENDER)
public class BlockESP extends Module {

    public final TextProperty blocks = new TextProperty("Blocks", "bed,diamond_ore,emerald_ore,tnt,ender_chest", 300);
    public final DoubleProperty range = new DoubleProperty("Range", new DoubleValue(8, 32, 24, 1));
    public final BooleanProperty render = new BooleanProperty("Render", true);

    private static final int MAX_HITS = 2000;

    private final List<Block> wanted = new ArrayList<>();
    private final List<BlockPos> found = new ArrayList<>();
    private String wantedKey = null;
    private int tickCounter;

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (++tickCounter < 10) return;
        tickCounter = 0;
        if (!blocks.getValue().equals(wantedKey)) {
            wantedKey = blocks.getValue();
            wanted.clear();
            for (String name : wantedKey.split(",")) {
                Block b = Block.getBlockFromName(name.trim());
                if (b != null && b != Block.getBlockById(0)) wanted.add(b);
            }
        }
        found.clear();
        if (wanted.isEmpty()) return;

        int r = (int) range.getValue().getInput();
        BlockPos origin = new BlockPos(mc.thePlayer);
        for (int x = -r; x <= r && found.size() < MAX_HITS; x++) for (int y = -r; y <= r && found.size() < MAX_HITS; y++) for (int z = -r; z <= r && found.size() < MAX_HITS; z++) {
            BlockPos p = origin.add(x, y, z);
            if (!mc.theWorld.isBlockLoaded(p)) continue;
            if (wanted.contains(mc.theWorld.getBlockState(p).getBlock())) found.add(p);
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRenderWorld = event -> {
        if (!render.getValue() || found.isEmpty()) return;
        int main = Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor();
        for (BlockPos p : found) {
            RenderUtils.renderBlock(p, ColorUtils.withAlpha(main, 70), false, true);
            RenderUtils.renderBlock(p, ColorUtils.withAlpha(main, 230), true, false);
        }
    };
}
