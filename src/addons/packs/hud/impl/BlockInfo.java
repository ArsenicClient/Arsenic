import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.gui.hud.HudElement;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.render.DrawUtils;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;

import java.util.Locale;

/**
 * Shows the block under your crosshair: its name, its hardness, and an approximate time to break it with the item you
 * hold. The time follows the usual 1.8 formula (hardness times 30 with a suitable tool, times 100 without, divided by
 * the tool's efficiency). It is an estimate: it ignores haste, water and air, and the server can differ.
 */
@ModuleInfo(name = "BlockInfo", description = "Shows the block under your crosshair and how long it takes to break", category = ModuleCategory.RENDER)
public class BlockInfo extends Module {

    public final BooleanProperty hud = new BooleanProperty("HUD", true);

    private final HudElement panel = hudElement("BlockInfo", 4, 360, 170, 30);

    @RequiresPlayer
    @EventLink
    public final Listener<EventRender2D> onRender = event -> {
        if (!hud.getValue()) return;
        MovingObjectPosition over = mc.objectMouseOver;
        if (over == null || over.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;
        BlockPos pos = over.getBlockPos();
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        if (block == null || block.getMaterial().isLiquid()) return;

        float hardness = block.getBlockHardness(mc.theWorld, pos);
        ItemStack held = mc.thePlayer.getHeldItem();
        String time;
        if (hardness < 0) {
            time = "unbreakable";
        } else {
            boolean harvest = held == null ? !block.getMaterial().isToolNotRequired() : held.canHarvestBlock(block);
            float efficiency = held == null ? 1f : PlayerUtils.getEfficiency(held, block);
            double ticks = hardness * (harvest ? 30 : 100) / Math.max(0.1, efficiency);
            time = String.format(Locale.ROOT, "~%.2fs", ticks / 20.0);
        }

        String[] rows = {block.getLocalizedName(), String.format(Locale.ROOT, "hardness %.1f   break %s", hardness, time)};
        int width = 170;
        for (String r : rows) width = Math.max(width, mc.fontRendererObj.getStringWidth(r) + 12);
        panel.setSize(width, 6 + rows.length * 10);
        DrawUtils.drawRoundedRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 5, 0x96121212);
        int y = panel.y + 4;
        for (String r : rows) {
            mc.fontRendererObj.drawStringWithShadow(r, panel.x + 6, y, 0xFFFFFFFF);
            y += 10;
        }
    };
}
