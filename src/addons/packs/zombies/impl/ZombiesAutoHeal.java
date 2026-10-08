import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.TextProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Uses a healing item from the hotbar when your health drops below a limit. The item is set by name (default
 * golden_apple; use the item name of whatever healing item the mode gives you). The slot is held until the item has been
 * used, because switching slots would cancel eating, then your slot is put back.
 */
@ModuleInfo(name = "ZombiesAutoHeal", description = "Uses a healing item from the hotbar when your health is low", category = ModuleCategory.PLAYER)
public class ZombiesAutoHeal extends Module {

    public final DoubleProperty healAt = new DoubleProperty("Heal Below (hearts)", new DoubleValue(2, 20, 8, 1));
    public final TextProperty item = new TextProperty("Item", "golden_apple", 40);

    private static final int MAX_USE_TICKS = 40;

    private int restoreSlot = -1, useTicks;

    @Override
    protected void onDisable() {
        restore();
    }

    @Override
    public boolean isSwappingHotbar() {
        return restoreSlot != -1;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (restoreSlot != -1) {
            if (mc.thePlayer.isUsingItem() && useTicks++ < MAX_USE_TICKS) return;
            restore();
            return;
        }
        if (mc.currentScreen != null || mc.thePlayer.getHealth() > healAt.getValue().getInput()) return;
        Item wanted = Item.getByNameOrId(item.getValue().trim());
        if (wanted == null) return;
        int slot = findSlot(wanted);
        if (slot == -1) return;
        restoreSlot = mc.thePlayer.inventory.currentItem;
        useTicks = 0;
        mc.thePlayer.inventory.currentItem = slot;
        ItemStack held = mc.thePlayer.inventory.getCurrentItem();
        if (held != null) mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, held);
    };

    private void restore() {
        if (restoreSlot != -1 && mc.thePlayer != null) mc.thePlayer.inventory.currentItem = restoreSlot;
        restoreSlot = -1;
    }

    private static int findSlot(Item wanted) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.thePlayer.inventory.mainInventory[i];
            if (s != null && s.getItem() == wanted) return i;
        }
        return -1;
    }
}
