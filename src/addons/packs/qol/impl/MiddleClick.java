import arsenic.module.ModuleTier;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventMouse;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.EnumProperty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;

/**
 * Middle click uses a chosen item from the hotbar (pearl, rod or golden apple): it switches to the item for the click,
 * uses it, and puts your slot back on the next tick.
 *
 * It stays out of the way of the built-in middle-click friend toggle: when you are aiming at a player, the click goes to
 * the friend toggle and nothing is used. It does nothing while a GUI is open.
 */
@ModuleInfo(name = "MiddleClick", description = "Middle click uses a chosen hotbar item (pearl, rod or golden apple)", category = ModuleCategory.PLAYER, tier = ModuleTier.QOL)
public class MiddleClick extends Module {

    public enum UseItem {
        PEARL("Ender Pearl", Items.ENDER_PEARL),
        ROD("Fishing Rod", Items.FISHING_ROD),
        GAPPLE("Golden Apple", Items.GOLDEN_APPLE);

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

    /** Longest we keep the chosen slot while an item is in use (a golden apple takes 32 ticks). */
    private static final int MAX_USE_TICKS = 40;

    private int restoreSlot = -1;
    private int useTicks;

    @Override
    protected void onDisable() {
        restore();
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventMouse.Down> onMouse = event -> {
        if (event.button != 2 || mc.gui.screen() != null) return;
        HitResult over = mc.hitResult;
        if (over instanceof net.minecraft.world.phys.EntityHitResult entityHit
                && entityHit.getEntity() instanceof Player) return;     // the built-in friend toggle owns this click

        if (restoreSlot != -1) return;                                  // one use at a time
        int slot = findSlot(item.getValue().item);
        if (slot == -1) return;

        restoreSlot = mc.player.getInventory().getSelectedSlot();
        useTicks = 0;
        mc.player.getInventory().setSelectedSlot(slot);
        ItemStack held = mc.player.getMainHandItem();
        if (held != null) mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (restoreSlot == -1) return;
        // Switching slots cancels eating, so a golden apple keeps the slot until it has been eaten (or the cap is hit).
        if (mc.player.isUsingItem() && useTicks++ < MAX_USE_TICKS) return;
        restore();
    };

    private void restore() {
        if (restoreSlot != -1 && mc.player != null) mc.player.getInventory().setSelectedSlot(restoreSlot);
        restoreSlot = -1;
    }

    @Override
    public boolean isSwappingHotbar() {
        return restoreSlot != -1;
    }

    private static int findSlot(Item wanted) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (s != null && s.getItem() == wanted) return i;
        }
        return -1;
    }
}
