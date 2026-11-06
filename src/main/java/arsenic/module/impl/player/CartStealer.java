package arsenic.module.impl.player;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.io.Keys;
import arsenic.utils.timer.MSTimer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;

/** Shift-clicks TNT minecarts out of an open shulker box one at a time, then closes it. */
@ModuleInfo(name = "CartStealer", description = "Pulls TNT minecarts out of open shulker boxes", category = ModuleCategory.PLAYER, tier = ModuleTier.LEGIT)
public class CartStealer extends Module {

    private static final int SHULKER_SLOTS = 27;
    private static final int MENU_SLOTS = 63;

    public enum Bind {
        None, Middle, Mouse4, Mouse5, R, F, G, V, X, Z, C, Q, Tab, Left_Alt, Caps_Lock
    }

    public final RangeProperty delay = new RangeProperty("Delay (ms)", new RangeValue(0, 1000, 50, 100, 5));
    public final BooleanProperty close = new BooleanProperty("Close", true);
    /** Hold to steal; None steals automatically whenever a shulker is open. */
    public final EnumProperty<Bind> activateKey = new EnumProperty<>("Activate Key", Bind.None);

    private final MSTimer timer = new MSTimer();
    private double currentDelay;
    private int lastContainerId = -1;

    @Override
    protected void onEnable() {
        lastContainerId = -1;
        currentDelay = delay.getValue().getRandomInRange();
    }

    @Override
    protected void onDisable() {
        lastContainerId = -1;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> tickListener = event -> {
        if (mc.gameMode == null || !(mc.player.containerMenu instanceof ShulkerBoxMenu menu)) {
            lastContainerId = -1;
            return;
        }
        if (menu.containerId != lastContainerId) {
            // fresh shulker: wait one delay before the first pull
            lastContainerId = menu.containerId;
            timer.reset();
            currentDelay = delay.getValue().getRandomInRange();
        }
        if (activateKey.getValue() != Bind.None && !bindDown(activateKey.getValue()))
            return;
        if (!timer.hasTimeElapsed(currentDelay))
            return;

        if (freeInventorySlots(menu) <= 1) {
            if (close.getValue())
                mc.player.closeContainer();
            return;
        }

        for (int i = 0; i < SHULKER_SLOTS; i++) {
            Slot slot = menu.getSlot(i);
            if (slot.hasItem() && slot.getItem().is(Items.TNT_MINECART)) {
                mc.gameMode.handleContainerInput(menu.containerId, i, 0, ContainerInput.QUICK_MOVE, mc.player);
                timer.reset();
                currentDelay = delay.getValue().getRandomInRange();
                return;
            }
        }

        if (close.getValue())
            mc.player.closeContainer();
    };

    private static int freeInventorySlots(ShulkerBoxMenu menu) {
        int free = 0;
        int end = Math.min(MENU_SLOTS, menu.slots.size());
        for (int i = SHULKER_SLOTS; i < end; i++) {
            if (!menu.getSlot(i).hasItem())
                free++;
        }
        return free;
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
}
