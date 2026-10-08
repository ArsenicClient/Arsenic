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
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * Panel with the durability of your armour and held item, and optionally the same for the nearest player within 10
 * blocks. Durability is shown as a percentage of the item's maximum; "--" means no item in that slot.
 */
@ModuleInfo(name = "ArmorStatus", description = "Shows the durability of your armour and held item, and the nearest player's", category = ModuleCategory.RENDER)
public class ArmorStatus extends Module {

    public final BooleanProperty showNearest = new BooleanProperty("Show Nearest", true);
    public final BooleanProperty hud = new BooleanProperty("HUD", true);

    private final HudElement panel = hudElement("ArmorStatus", 4, 140, 130, 62);

    private static final String[] LABELS = {"Helm", "Chest", "Legs", "Boots", "Held"};

    @EventLink
    public final Listener<EventRender2D> onRender = event -> {
        if (!hud.getValue() || mc.thePlayer == null) return;

        EntityPlayer near = null;
        if (showNearest.getValue()) {
            List<EntityPlayer> players = PlayerUtils.getPlayersWithin(10);
            for (EntityPlayer p : players) {
                if (near == null || mc.thePlayer.getDistanceToEntity(p) < mc.thePlayer.getDistanceToEntity(near)) near = p;
            }
        }

        int width = near != null ? 170 : 110;
        int rows = LABELS.length + 1;
        panel.setSize(width, 6 + rows * 10);
        DrawUtils.drawRoundedRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 5, 0x96121212);

        int y = panel.y + 5;
        mc.fontRendererObj.drawStringWithShadow("Durability", panel.x + 6, y, 0xFFAAAAAA);
        if (near != null) {
            mc.fontRendererObj.drawStringWithShadow("Near", panel.x + 110, y, 0xFFAAAAAA);
        }
        y += 10;

        for (int i = 0; i < LABELS.length; i++) {
            mc.fontRendererObj.drawStringWithShadow(LABELS[i], panel.x + 6, y, 0xFFFFFFFF);
            String you = i < 4 ? percent(mc.thePlayer.inventory.armorInventory[3 - i]) : percent(mc.thePlayer.getHeldItem());
            mc.fontRendererObj.drawStringWithShadow(you, panel.x + 50, y, color(you));
            if (near != null) {
                String theirs = i < 4 ? percent(near.inventory.armorInventory[3 - i]) : percent(near.getHeldItem());
                mc.fontRendererObj.drawStringWithShadow(theirs, panel.x + 110, y, color(theirs));
            }
            y += 10;
        }
    };

    private static String percent(ItemStack stack) {
        if (stack == null) return "--";
        if (!stack.isItemStackDamageable() || stack.getMaxDamage() <= 0) return "-";
        int left = stack.getMaxDamage() - stack.getItemDamage();
        return Math.max(0, left * 100 / stack.getMaxDamage()) + "%";
    }

    private static int color(String value) {
        if (value.endsWith("%")) {
            try {
                if (Integer.parseInt(value.substring(0, value.length() - 1)) <= 20) return 0xFFFF5555;
            } catch (NumberFormatException ignored) {
            }
        }
        return 0xFFFFFFFF;
    }
}
