import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.gui.hud.HudElement;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.minecraft.ContainerUtils;
import arsenic.utils.render.DrawUtils;
import net.minecraft.item.ItemStack;

import java.util.Locale;

/**
 * Shows the damage of the weapon in your hand next to the best one in your hotbar and main inventory, and the slot to
 * switch to when the best is stronger. Damage comes from ContainerUtils.getDamage, so it follows the client's own
 * weapon scoring.
 */
@ModuleInfo(name = "WeaponCompare", description = "Shows your held weapon's damage next to the best one in your inventory", category = ModuleCategory.RENDER)
public class WeaponCompare extends Module {

    public final BooleanProperty hud = new BooleanProperty("HUD", true);

    private final HudElement panel = hudElement("WeaponCompare", 4, 400, 150, 30);

    @RequiresPlayer
    @EventLink
    public final Listener<EventRender2D> onRender = event -> {
        if (!hud.getValue()) return;
        ItemStack held = mc.thePlayer.getHeldItem();
        double heldDamage = held == null ? 0 : ContainerUtils.getDamage(held);

        double best = 0;
        int bestSlot = -1;
        for (int i = 0; i < mc.thePlayer.inventory.mainInventory.length; i++) {
            ItemStack s = mc.thePlayer.inventory.mainInventory[i];
            if (s == null) continue;
            double d = ContainerUtils.getDamage(s);
            if (d > best) {
                best = d;
                bestSlot = i;
            }
        }

        String[] rows = {
                String.format(Locale.ROOT, "Held  %.1f", heldDamage),
                bestSlot == -1 ? "Best  none" : String.format(Locale.ROOT, "Best  %.1f  (slot %d)", best, bestSlot + 1)
        };
        boolean upgrade = bestSlot != -1 && best > heldDamage;
        int width = 150;
        for (String r : rows) width = Math.max(width, mc.fontRendererObj.getStringWidth(r) + 12);
        panel.setSize(width, 6 + rows.length * 10);
        DrawUtils.drawRoundedRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 5, 0x96121212);
        int y = panel.y + 4;
        for (int i = 0; i < rows.length; i++) {
            int color = i == 1 && upgrade ? 0xFFFFFF55 : 0xFFFFFFFF;
            mc.fontRendererObj.drawStringWithShadow(rows[i], panel.x + 6, y, color);
            y += 10;
        }
    };
}
