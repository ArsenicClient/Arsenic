package arsenic.module.impl.visual;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.gui.themes.ThemeManager;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.minecraft.WorldUtils;
import arsenic.utils.render.RenderUtils;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

/** Outlines the containers around you through walls, each kind in its own colour. */
@ModuleInfo(name = "StorageESP", description = "Highlights chests, barrels, shulker boxes and other containers", category = ModuleCategory.RENDER, tier = ModuleTier.VISUAL)
public class StorageESP extends Module {

    public final BooleanProperty chests = new BooleanProperty("Chests", true);
    public final BooleanProperty enderChests = new BooleanProperty("Ender Chests", true);
    public final BooleanProperty barrels = new BooleanProperty("Barrels", true);
    public final BooleanProperty shulkers = new BooleanProperty("Shulker Boxes", true);
    public final BooleanProperty other = new BooleanProperty("Furnaces, Hoppers, Dispensers", false);

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRender = event -> {
        int main = ThemeManager.getMainColor();
        int darker = ThemeManager.getDarkerColor();
        int accent = ThemeManager.getGradientColor();

        for (BlockEntity be : WorldUtils.loadedBlockEntities()) {
            int colour;
            if (be instanceof ChestBlockEntity && chests.getValue())
                colour = main;
            else if (be instanceof EnderChestBlockEntity && enderChests.getValue())
                colour = darker;
            else if (be instanceof BarrelBlockEntity && barrels.getValue())
                colour = main;
            else if (be instanceof ShulkerBoxBlockEntity && shulkers.getValue())
                colour = accent;
            else if ((be instanceof AbstractFurnaceBlockEntity || be instanceof HopperBlockEntity
                    || be instanceof DispenserBlockEntity) && other.getValue())
                colour = ThemeManager.getWhite();
            else
                continue;
            RenderUtils.renderBlock(be.getBlockPos(), colour, true, true);
        }
    };
}
