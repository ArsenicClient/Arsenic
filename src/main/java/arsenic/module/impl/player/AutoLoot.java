package arsenic.module.impl.player;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.io.Keys;
import arsenic.utils.timer.MSTimer;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Frees inventory space for looting: while your inventory is open (and the bind is held, if one is set) it drops
 * spare totem and pearl stacks beyond the amounts you want to keep, one stack at a time with a random delay.
 */
@ModuleInfo(name = "AutoLoot", description = "Drops spare totems and pearls to make room for loot", category = ModuleCategory.PLAYER, tier = ModuleTier.QOL)
public class AutoLoot extends Module {

    public enum Bind {
        None, Middle, Mouse4, Mouse5, R, F, G, V, X, Z, C, Q, Tab, Left_Alt, Caps_Lock
    }

    public final RangeProperty delay = new RangeProperty("Delay (ms)", new RangeValue(0, 300, 30, 50, 1));
    public final DoubleProperty minTotems = new DoubleProperty("Min Totems", new DoubleValue(1, 20, 1, 1));
    public final DoubleProperty minPearls = new DoubleProperty("Min Pearls", new DoubleValue(1, 10, 5, 1));
    /** None runs whenever the inventory is open; any other bind must be held. */
    public final EnumProperty<Bind> key = new EnumProperty<>("Key", Bind.None);

    private final MSTimer timer = new MSTimer();
    private long rolledDelay;

    @Override
    protected void onEnable() {
        rolledDelay = 0;
        timer.lastMS = 0;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (mc.gameMode == null || !(mc.gui.screen() instanceof InventoryScreen))
            return;
        if (!mc.player.containerMenu.getCarried().isEmpty())
            return;
        if (key.getValue() != Bind.None && !bindDown(key.getValue()))
            return;
        if (!timer.hasTimeElapsed(rolledDelay))
            return;

        if (dropExcess(Items.TOTEM_OF_UNDYING, (int) minTotems.getValue().getInput()))
            return;
        dropExcess(Items.ENDER_PEARL, (int) minPearls.getValue().getInput());
    };

    /**
     * Drops one stack of {@code item} when the inventory holds more than {@code keep} stacks of it. Main inventory
     * stacks go first so hotbar ones stay ready to use.
     */
    private boolean dropExcess(Item item, int keep) {
        int count = 0;
        int mainSlot = -1, hotbarSlot = -1;
        for (int i = 0; i <= 35; i++) {
            if (mc.player.getInventory().getItem(i).getItem() != item)
                continue;
            count++;
            if (i >= 9) {
                if (mainSlot == -1)
                    mainSlot = i;
            } else if (hotbarSlot == -1) {
                hotbarSlot = i;
            }
        }
        if (count <= keep)
            return false;

        int index = mainSlot != -1 ? mainSlot : hotbarSlot;
        int menuSlot = index < 9 ? index + 36 : index;
        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, menuSlot, 1, ContainerInput.THROW, mc.player);
        rolledDelay = (long) delay.getValue().getRandomInRange();
        timer.reset();
        return true;
    }

    private static boolean bindDown(Bind b) {
        int mask = switch (b) {
            case Middle -> 2;
            case Mouse4 -> 8;
            case Mouse5 -> 16;
            default -> 0;
        };
        if (mask != 0)
            return (org.lwjgl.sdl.SDLMouse.SDL_GetMouseState(null, null) & mask) != 0;
        return Keys.isKeyDown(Keys.getKeyIndex(b.name()));
    }
}
