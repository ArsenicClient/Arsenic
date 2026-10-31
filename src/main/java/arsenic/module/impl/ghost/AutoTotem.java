package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.HotbarUse;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;

/** Keeps a totem of undying in the offhand, putting a new one there after a pop. */
@ModuleInfo(name = "AutoTotem", description = "Keeps a totem in your offhand", category = ModuleCategory.COMBAT, tier = ModuleTier.LEGIT)
public class AutoTotem extends Module {

    /** The swap button that targets the offhand. */
    private static final int OFFHAND_BUTTON = 40;

    public final DoubleProperty delay = new DoubleProperty("Delay (ticks)", new DoubleValue(0, 10, 2, 1));

    private int wait;

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            wait = (int) delay.getValue().getInput();
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        // any other screen's container has its own slot numbers
        if (mc.gui.screen() != null && !(mc.gui.screen() instanceof InventoryScreen))
            return;
        int slot = HotbarUse.findMenuSlot(s -> s.is(Items.TOTEM_OF_UNDYING));
        if (slot == -1)
            return;
        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, slot, OFFHAND_BUTTON, ContainerInput.SWAP, mc.player);
        wait = (int) delay.getValue().getInput();
    };
}
