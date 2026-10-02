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
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityEnderChest;

@ModuleInfo(name = "ChestESP", category = ModuleCategory.RENDER, hidden = true)
public class ChestESP extends Module {

    public final BooleanProperty chests = new BooleanProperty("Chests", true);
    public final BooleanProperty enderChests = new BooleanProperty("Ender Chests", true);

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRender = event -> {
        int mainColor = ThemeManager.getMainColor();
        int darkerColor = ThemeManager.getDarkerColor();

        for (TileEntity te : mc.level.loadedTileEntityList) {
            if (te instanceof TileEntityChest && chests.getValue()) {
                RenderUtils.renderBlock(te.getPos(), mainColor, true, true);
            } else if (te instanceof TileEntityEnderChest && enderChests.getValue()) {
                RenderUtils.renderBlock(te.getPos(), darkerColor, true, true);
            }
        }
    };
}
