package arsenic.module.impl.visual;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.gui.themes.ThemeManager;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.minecraft.WorldUtils;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;

@ModuleInfo(name = "ChestESP", category = ModuleCategory.RENDER, hidden = true)
public class ChestESP extends Module {

    public final BooleanProperty chests = new BooleanProperty("Chests", true);
    public final BooleanProperty enderChests = new BooleanProperty("Ender Chests", true);

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRender = event -> {
        int mainColor = ThemeManager.getMainColor();
        int darkerColor = ThemeManager.getDarkerColor();

        for (BlockEntity te : WorldUtils.loadedBlockEntities()) {
            if (te instanceof ChestBlockEntity && chests.getValue()) {
                RenderUtils.renderBlock(te.getBlockPos(), mainColor, true, true);
            } else if (te instanceof EnderChestBlockEntity && enderChests.getValue()) {
                RenderUtils.renderBlock(te.getBlockPos(), darkerColor, true, true);
            }
        }
    };
}
