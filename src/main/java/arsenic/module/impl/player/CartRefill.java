package arsenic.module.impl.player;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.ShowWhen;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.io.Keys;
import arsenic.utils.timer.MSTimer;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Keeps one hotbar slot stocked with a TNT minecart, swapping one up from the inventory whenever the slot is empty.
 */
@ModuleInfo(name = "CartRefill", description = "Keeps a hotbar slot topped up with TNT minecarts", category = ModuleCategory.PLAYER, tier = ModuleTier.LEGIT)
public class CartRefill extends Module {

    public enum Mode {
        /** Refills while the inventory screen is open. */
        Always,
        /** Refills while the bind is held. */
        Keybind
    }

    public enum Bind {
        None, Middle, Mouse4, Mouse5, R, F, G, V, X, Z, C, Q, Tab, Left_Alt, Caps_Lock
    }

    public final EnumProperty<Mode> mode = new EnumProperty<>("Mode", Mode.Always);
    @ShowWhen(property = "Mode", is = "Keybind")
    public final EnumProperty<Bind> key = new EnumProperty<>("Refill Key", Bind.None);
    public final DoubleProperty targetSlot = new DoubleProperty("Target Slot", new DoubleValue(1, 9, 9, 1));
    public final DoubleProperty delay = new DoubleProperty("Delay (ms)", new DoubleValue(0, 1000, 100, 10));

    private final MSTimer timer = new MSTimer();

    @Override
    protected void onEnable() {
        timer.reset();
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (mc.gameMode == null || !timer.hasTimeElapsed((long) delay.getValue().getInput()))
            return;
        if (!active())
            return;
        int hotbar = Math.max(0, Math.min(8, (int) targetSlot.getValue().getInput() - 1));
        if (!mc.player.getInventory().getItem(hotbar).isEmpty())
            return;
        int cart = findCart();
        if (cart == -1)
            return;
        // Inventory indices 9-35 are the same slot numbers in the player's inventory menu.
        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, cart, hotbar, ContainerInput.SWAP, mc.player);
        timer.reset();
    };

    private boolean active() {
        if (mode.getValue() == Mode.Always)
            return mc.gui.screen() instanceof InventoryScreen;
        return bindDown(key.getValue());
    }

    private int findCart() {
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == Items.TNT_MINECART)
                return i;
        }
        return -1;
    }

    private static boolean bindDown(Bind bind) {
        if (bind == Bind.None)
            return false;
        int mask = switch (bind) {
            case Middle -> 2;
            case Mouse4 -> 8;
            case Mouse5 -> 16;
            default -> 0;
        };
        if (mask != 0)
            return (org.lwjgl.sdl.SDLMouse.SDL_GetMouseState(null, null) & mask) != 0;
        return Keys.isKeyDown(Keys.getKeyIndex(bind.name()));
    }

    @Override
    public String getHudInfo() {
        return mode.getValue().name();
    }
}
