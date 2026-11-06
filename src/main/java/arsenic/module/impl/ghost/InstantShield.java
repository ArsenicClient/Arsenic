package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.ShowWhen;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.utils.io.Keys;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Pulls a shield into your hand and raises it in one press, then puts your old item back when you let go. With no
 * bind set the module itself is the switch: enabling it raises the shield, disabling it puts it away.
 */
@ModuleInfo(name = "InstantShield", description = "Swaps to your shield and raises it on a key", category = ModuleCategory.COMBAT, tier = ModuleTier.LEGIT)
public class InstantShield extends Module {

    public enum Bind {
        None, Middle, Mouse4, Mouse5, R, F, G, V, X, Z, C, Q, Tab, Left_Alt, Caps_Lock
    }

    public final EnumProperty<Bind> bind = new EnumProperty<>("Shield Bind", Bind.None);
    @ShowWhen(property = "Shield Bind", not = "None")
    public final BooleanProperty hold = new BooleanProperty("Hold", true);
    public final BooleanProperty inventory = new BooleanProperty("Inventory", false);

    /** Hotbar slot selected before swapping to the shield, -1 when not swapped. */
    private int previousSlot = -1;
    /** Hotbar slot the shield sits in while active. */
    private int shieldSlot = -1;
    /** Inventory index the shield was pulled from (9-35), -1 when it came from the hotbar. */
    private int inventorySlot = -1;
    /** Hotbar slot to go back to once the shield is lowered (slot change never shares a tick with using). */
    private int pendingRestore = -1;
    private boolean wasDown;
    private boolean forcing;

    @Override
    protected void onEnable() {
        wasDown = false;
        if (bind.getValue() == Bind.None && mc.player != null)
            activate();
    }

    @Override
    protected void onDisable() {
        if (mc.player != null && previousSlot != -1)
            deactivate();
        if (mc.player != null && pendingRestore != -1 && pendingRestore != mc.player.getInventory().getSelectedSlot())
            mc.player.getInventory().setSelectedSlot(pendingRestore);
        releaseUse();
        reset();
        pendingRestore = -1;
        wasDown = false;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (mc.level == null)
            return;
        if (!mc.player.isAlive()) {
            reset();
            pendingRestore = -1;
            releaseUse();
            return;
        }

        // the shield we swapped in is gone (dropped, moved, broke)
        if (shieldSlot != -1 && !mc.player.getInventory().getItem(shieldSlot).is(Items.SHIELD))
            reset();

        boolean noScreen = mc.gui.screen() == null;

        if (pendingRestore != -1 && !mc.player.isUsingItem()) {
            if (noScreen && mc.player.getInventory().getSelectedSlot() != pendingRestore)
                mc.player.getInventory().setSelectedSlot(pendingRestore);
            pendingRestore = -1;
        }

        Bind b = bind.getValue();
        if (b != Bind.None) {
            boolean down = bindDown(b);
            boolean pressed = down && !wasDown;
            boolean released = !down && wasDown;
            wasDown = down;
            if (noScreen) {
                if (hold.getValue()) {
                    if (pressed && previousSlot == -1)
                        activate();
                    else if (released && previousSlot != -1)
                        deactivate();
                } else if (pressed) {
                    if (previousSlot != -1)
                        deactivate();
                    else
                        activate();
                }
            }
        }

        // scrolled off the shield: put a borrowed inventory shield back and stop
        if (noScreen && inventorySlot != -1 && shieldSlot != -1 && mc.player.getInventory().getSelectedSlot() != shieldSlot) {
            swap(inventorySlot, shieldSlot);
            reset();
        }

        if (noScreen && previousSlot != -1 && shieldInHand()) {
            mc.options.keyUse.setDown(true);
            forcing = true;
        } else if (forcing) {
            releaseUse();
        }
    };

    private void activate() {
        int selected = mc.player.getInventory().getSelectedSlot();
        if (mc.player.getMainHandItem().is(Items.SHIELD)) {
            // already holding one: just raise it, nothing to put back
            previousSlot = selected;
            shieldSlot = selected;
            inventorySlot = -1;
            pendingRestore = -1;
            return;
        }
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(Items.SHIELD)) {
                previousSlot = selected;
                shieldSlot = i;
                inventorySlot = -1;
                pendingRestore = -1;
                mc.player.getInventory().setSelectedSlot(i);
                return;
            }
        }
        if (!inventory.getValue())
            return;
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getItem(i).is(Items.SHIELD)) {
                previousSlot = selected;
                shieldSlot = selected;
                inventorySlot = i;
                pendingRestore = -1;
                swap(i, selected);
                return;
            }
        }
    }

    private void deactivate() {
        releaseUse();
        if (inventorySlot != -1)
            swap(inventorySlot, shieldSlot);
        else if (previousSlot != -1)
            pendingRestore = previousSlot;
        reset();
    }

    private void reset() {
        previousSlot = -1;
        shieldSlot = -1;
        inventorySlot = -1;
    }

    private void releaseUse() {
        if (!forcing)
            return;
        forcing = false;
        mc.options.keyUse.setDown(mc.gui.screen() == null && Keys.isPhysicallyDown(mc.options.keyUse));
    }

    /** A shield in the main hand, or in the offhand with nothing in the main hand that right click would use first. */
    private boolean shieldInHand() {
        if (mc.player.getMainHandItem().is(Items.SHIELD))
            return true;
        return mc.player.getOffhandItem().is(Items.SHIELD) && !usableInMainHand(mc.player.getMainHandItem());
    }

    private static boolean usableInMainHand(ItemStack stack) {
        return stack.has(DataComponents.FOOD) || stack.has(DataComponents.CONSUMABLE)
                || stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION)
                || stack.is(Items.SNOWBALL) || stack.is(Items.EGG) || stack.is(Items.ENDER_PEARL)
                || stack.is(Items.EXPERIENCE_BOTTLE);
    }

    /** Swaps a main inventory slot (9-35) with a hotbar slot through the inventory menu. */
    private static void swap(int inventoryIndex, int hotbarSlot) {
        if (mc.gameMode == null)
            return;
        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, inventoryIndex, hotbarSlot, ContainerInput.SWAP, mc.player);
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
