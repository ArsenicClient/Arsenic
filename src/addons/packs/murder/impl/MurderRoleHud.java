import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventTick;
import arsenic.gui.hud.HudElement;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.AntiBot;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.render.DrawUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HUD list of every player seen this round with the role hint from the item they hold (sword: suspected murderer, bow:
 * suspected detective). A hint stays on the list once it was seen, so it survives a player putting the item away. Cleared
 * when the module is enabled.
 */
@ModuleInfo(name = "MurderRoleHud", description = "Lists players seen this round with the role their item suggests", category = ModuleCategory.RENDER)
public class MurderRoleHud extends Module {

    public final BooleanProperty hud = new BooleanProperty("HUD", true);

    private final HudElement panel = hudElement("MurderRoles", 4, 620, 170, 16);
    private final Map<String, String> roles = new LinkedHashMap<>();

    @Override
    protected void onEnable() {
        roles.clear();
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        for (EntityPlayer p : mc.theWorld.playerEntities) {
            if (p == mc.thePlayer || AntiBot.isBot(p)) continue;
            ItemStack held = p.getHeldItem();
            if (held == null) continue;
            if (held.getItem() instanceof ItemSword) roles.put(p.getName(), "suspected murderer");
            else if (held.getItem() instanceof ItemBow) roles.put(p.getName(), "suspected detective");
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRender2D> onRender = event -> {
        if (!hud.getValue() || roles.isEmpty()) return;
        int shown = Math.min(6, roles.size());
        int width = 170;
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (Map.Entry<String, String> e : roles.entrySet()) {
            if (lines.size() == shown) break;
            lines.add(e.getKey() + "  " + e.getValue());
        }
        for (String l : lines) width = Math.max(width, mc.fontRendererObj.getStringWidth(l) + 12);
        panel.setSize(width, 6 + lines.size() * 10);
        DrawUtils.drawRoundedRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 5, 0x96121212);
        int y = panel.y + 4;
        for (String l : lines) {
            mc.fontRendererObj.drawStringWithShadow(l, panel.x + 6, y, l.endsWith("murderer") ? 0xFFFF5555 : 0xFF5599FF);
            y += 10;
        }
    };
}
