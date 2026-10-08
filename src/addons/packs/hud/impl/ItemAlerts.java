import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.TextProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.timer.MSTimer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Chat alert when an item you care about is dropped near you, once per dropped stack. Items are a comma-separated list
 * of Minecraft names (diamond, emerald, golden_apple, iron_sword). The most recent alert also shows on the HUD for a few
 * seconds.
 */
@ModuleInfo(name = "ItemAlerts", description = "Alerts when chosen items are dropped near you", category = ModuleCategory.RENDER)
public class ItemAlerts extends Module {

    public final TextProperty items = new TextProperty("Items", "diamond,emerald,golden_apple", 300);
    public final DoubleProperty range = new DoubleProperty("Range", new DoubleValue(4, 64, 24, 1));
    public final BooleanProperty chat = new BooleanProperty("Chat Alert", true);
    public final BooleanProperty hud = new BooleanProperty("HUD", true);

    private final Set<Integer> alerted = new HashSet<>();
    private final MSTimer lastAlert = MSTimer.expired();
    private String lastText = "";

    @Override
    protected void onEnable() {
        alerted.clear();
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        List<Item> wanted = new ArrayList<>();
        for (String name : items.getValue().split(",")) {
            Item item = Item.getByNameOrId(name.trim());
            if (item != null) wanted.add(item);
        }

        Set<Integer> present = new HashSet<>();
        double max = range.getValue().getInput();
        for (Entity e : mc.theWorld.loadedEntityList) {
            if (!(e instanceof EntityItem)) continue;
            present.add(e.getEntityId());
            if (alerted.contains(e.getEntityId()) || mc.thePlayer.getDistanceToEntity(e) > max) continue;
            ItemStack stack = ((EntityItem) e).getEntityItem();
            if (stack == null || !wanted.contains(stack.getItem())) continue;

            alerted.add(e.getEntityId());
            String text = stack.getDisplayName().replaceAll("§.", "") + " x" + stack.stackSize
                    + " (" + (int) mc.thePlayer.getDistanceToEntity(e) + "m)";
            lastText = "Dropped: " + text;
            lastAlert.reset();
            if (chat.getValue()) PlayerUtils.addWaterMarkedMessageToChat(lastText);
        }
        Iterator<Integer> it = alerted.iterator();
        while (it.hasNext()) if (!present.contains(it.next())) it.remove();
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRender2D> onRender = event -> {
        if (!hud.getValue() || lastAlert.getTime() > 4000 || lastText.isEmpty()) return;
        int width = Math.max(120, mc.fontRendererObj.getStringWidth(lastText) + 12);
        DrawUtils.drawRoundedRect(4, 340, 4 + width, 356, 5, 0x96121212);
        mc.fontRendererObj.drawStringWithShadow(lastText, 10, 344, 0xFFFFFFFF);
    };
}
