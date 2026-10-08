import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.gui.hud.HudElement;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.render.DrawUtils;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Counts what you carry in the main inventory and hotbar: placeable blocks, arrows, ender pearls and golden apples.
 * Only the categories you have are shown.
 */
@ModuleInfo(name = "ItemCounter", description = "Counts blocks, arrows, pearls and golden apples in your inventory", category = ModuleCategory.RENDER)
public class ItemCounter extends Module {

    public final BooleanProperty hud = new BooleanProperty("HUD", true);

    private final HudElement panel = hudElement("ItemCounter", 4, 220, 120, 16);

    @RequiresPlayer
    @EventLink
    public final Listener<EventRender2D> onRender = event -> {
        if (!hud.getValue()) return;
        int blocks = 0, arrows = 0, pearls = 0, gaps = 0;
        for (ItemStack s : mc.thePlayer.inventory.mainInventory) {
            if (s == null) continue;
            Item item = s.getItem();
            if (item instanceof ItemBlock) blocks += s.stackSize;
            else if (item == Items.arrow) arrows += s.stackSize;
            else if (item == Items.ender_pearl) pearls += s.stackSize;
            else if (item == Items.golden_apple) gaps += s.stackSize;
        }

        List<String> lines = new ArrayList<>();
        if (blocks > 0) lines.add("Blocks " + blocks);
        if (arrows > 0) lines.add("Arrows " + arrows);
        if (pearls > 0) lines.add("Pearls " + pearls);
        if (gaps > 0) lines.add("Gapples " + gaps);
        if (lines.isEmpty()) return;

        int width = 100;
        for (String l : lines) width = Math.max(width, mc.fontRendererObj.getStringWidth(l) + 12);
        panel.setSize(width, 6 + lines.size() * 10);
        DrawUtils.drawRoundedRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 5, 0x96121212);
        int y = panel.y + 4;
        for (String l : lines) {
            mc.fontRendererObj.drawStringWithShadow(l, panel.x + 6, y, 0xFFFFFFFF);
            y += 10;
        }
    };
}
