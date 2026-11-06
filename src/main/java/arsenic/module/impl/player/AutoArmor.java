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
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.io.Keys;
import arsenic.utils.minecraft.ItemUtils;
import arsenic.utils.timer.MSTimer;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Puts on the best armour in your inventory, scoring each piece by its armour value and then its enchantments.
 */
@ModuleInfo(name = "AutoArmor", description = "Equips the best armor from your inventory", category = ModuleCategory.PLAYER, tier = ModuleTier.LEGIT)
public class AutoArmor extends Module {

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public enum Mode {
        /** Swaps whenever something better is in the inventory. */
        Always,
        /** Swaps only while the inventory screen is open. */
        Inventory,
        /** Swaps only while the bind is held. */
        Keybind
    }

    public enum Bind {
        None, Middle, Mouse4, Mouse5, R, F, G, V, X, Z, C, Q, Tab, Left_Alt, Caps_Lock
    }

    public final EnumProperty<Mode> mode = new EnumProperty<>("Mode", Mode.Always);
    @ShowWhen(property = "Mode", is = "Keybind")
    public final EnumProperty<Bind> key = new EnumProperty<>("Key", Bind.None);
    public final BooleanProperty avoidCursed = new BooleanProperty("Avoid Cursed", true);
    public final DoubleProperty delay = new DoubleProperty("Delay (ms)", new DoubleValue(0, 1000, 100, 10));

    private final MSTimer timer = new MSTimer();

    @Override
    protected void onEnable() {
        timer.lastMS = 0;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (mc.gameMode == null || !timer.hasTimeElapsed((long) delay.getValue().getInput()) || !allowed())
            return;
        // the inventory menu's slot numbers only apply with no screen or the player's own inventory open
        if (mc.gui.screen() != null && !(mc.gui.screen() instanceof InventoryScreen))
            return;
        if (!mc.player.inventoryMenu.getCarried().isEmpty())
            return;
        for (EquipmentSlot slot : SLOTS) {
            if (equipBest(slot)) {
                timer.reset();
                return;
            }
        }
    };

    private boolean allowed() {
        return switch (mode.getValue()) {
            case Inventory -> mc.gui.screen() instanceof InventoryScreen;
            case Keybind -> bindDown(key.getValue());
            default -> true;
        };
    }

    /** Equips a better piece for this slot if the inventory has one; true when it clicked. */
    private boolean equipBest(EquipmentSlot slot) {
        ItemStack worn = mc.player.getItemBySlot(slot);
        if (slot == EquipmentSlot.CHEST && worn.is(Items.ELYTRA))
            return false;
        if (!worn.isEmpty() && ItemUtils.enchantLevel(Enchantments.BINDING_CURSE, worn) > 0)
            return false;

        int best = worn.isEmpty() ? -1 : score(worn);
        int bestSlot = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty() || ItemUtils.getArmorSlot(stack) != slot || ItemUtils.armorPoints(stack) <= 0)
                continue;
            if (avoidCursed.getValue() && (ItemUtils.enchantLevel(Enchantments.BINDING_CURSE, stack) > 0
                    || ItemUtils.enchantLevel(Enchantments.VANISHING_CURSE, stack) > 0))
                continue;
            int s = score(stack);
            if (s > best) {
                best = s;
                bestSlot = i < 9 ? 36 + i : i;
            }
        }
        if (bestSlot == -1)
            return false;

        int armorSlot = menuSlot(slot);
        if (worn.isEmpty()) {
            click(bestSlot, ContainerInput.QUICK_MOVE);
        } else {
            // pick the new piece up, swap it with the worn one, put the old one where the new one was
            click(bestSlot, ContainerInput.PICKUP);
            click(armorSlot, ContainerInput.PICKUP);
            click(bestSlot, ContainerInput.PICKUP);
        }
        return true;
    }

    /** Armour value first, then enchantments as a tie-breaker, weighted as openprestige did. */
    private static int score(ItemStack stack) {
        int score = (int) Math.round(ItemUtils.armorPoints(stack) * 1000);
        score += ItemUtils.enchantLevel(Enchantments.PROTECTION, stack) * 5;
        score += ItemUtils.enchantLevel(Enchantments.BLAST_PROTECTION, stack) * 3;
        score += ItemUtils.enchantLevel(Enchantments.PROJECTILE_PROTECTION, stack) * 3;
        score += ItemUtils.enchantLevel(Enchantments.FIRE_PROTECTION, stack) * 2;
        score += ItemUtils.enchantLevel(Enchantments.THORNS, stack) * 2;
        score += ItemUtils.enchantLevel(Enchantments.MENDING, stack) * 3;
        score += ItemUtils.enchantLevel(Enchantments.UNBREAKING, stack);
        score += ItemUtils.enchantLevel(Enchantments.FEATHER_FALLING, stack) * 3;
        score += ItemUtils.enchantLevel(Enchantments.DEPTH_STRIDER, stack);
        score += ItemUtils.enchantLevel(Enchantments.SOUL_SPEED, stack);
        score += ItemUtils.enchantLevel(Enchantments.AQUA_AFFINITY, stack);
        score += ItemUtils.enchantLevel(Enchantments.RESPIRATION, stack);
        score += ItemUtils.enchantLevel(Enchantments.SWIFT_SNEAK, stack) * 2;
        return score;
    }

    private static int menuSlot(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> 5;
            case CHEST -> 6;
            case LEGS -> 7;
            default -> 8;
        };
    }

    private void click(int menuSlot, ContainerInput input) {
        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, menuSlot, 0, input, mc.player);
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
