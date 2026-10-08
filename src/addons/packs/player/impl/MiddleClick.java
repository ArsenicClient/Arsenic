import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventMouse;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.EnumProperty;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;

/**
 * Middle click uses a chosen item from the hotbar (pearl, rod or golden apple): it switches to the item for the click,
 * uses it, and puts your slot back on the next tick.
 *
 * It stays out of the way of the built-in middle-click friend toggle: when you are aiming at a player, the click goes to
 * the friend toggle and nothing is used. It does nothing while a GUI is open.
 */
@ModuleInfo(name = "MiddleClick", description = "Middle click uses a chosen hotbar item (pearl, rod or golden apple)", category = ModuleCategory.PLAYER)
public class MiddleClick extends Module {

    public enum UseItem {
        PEARL("Ender Pearl", Items.ender_pearl),
        ROD("Fishing Rod", Items.fishing_rod),
        GAPPLE("Golden Apple", Items.golden_apple);

        final Item item;
        private final String label;

        UseItem(String label, Item item) {
            this.label = label;
            this.item = item;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public final EnumProperty<UseItem> item = new EnumProperty<>("Item", UseItem.PEARL);

    private int restoreSlot = -1;

    @Override
    protected void onDisable() {
        restore();
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventMouse.Down> onMouse = event -> {
        if (event.button != 2 || mc.currentScreen != null) return;
        MovingObjectPosition over = mc.objectMouseOver;
        if (over != null && over.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY
                && over.entityHit instanceof EntityPlayer) return;     // the built-in friend toggle owns this click

        if (restoreSlot != -1) return;                                  // one use at a time
        int slot = findSlot(item.getValue().item);
        if (slot == -1) return;

        restoreSlot = mc.thePlayer.inventory.currentItem;
        mc.thePlayer.inventory.currentItem = slot;
        ItemStack held = mc.thePlayer.inventory.getCurrentItem();
        if (held != null) mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, held);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (restoreSlot != -1) restore();
    };

    private void restore() {
        if (restoreSlot != -1 && mc.thePlayer != null) mc.thePlayer.inventory.currentItem = restoreSlot;
        restoreSlot = -1;
    }

    @Override
    public boolean isSwappingHotbar() {
        return restoreSlot != -1;
    }

    private static int findSlot(Item wanted) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.thePlayer.inventory.mainInventory[i];
            if (s != null && s.getItem() == wanted) return i;
        }
        return -1;
    }
}
