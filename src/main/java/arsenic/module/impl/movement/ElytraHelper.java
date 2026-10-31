package arsenic.module.impl.movement;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.HotbarUse;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Puts the elytra on when you fall far, starts the glide, fires a firework when you slow down, and puts the chestplate
 * back when you land.
 */
@ModuleInfo(name = "ElytraHelper", description = "Equips the elytra in a long fall and boosts with fireworks", category = ModuleCategory.MOVEMENT, tier = ModuleTier.LEGIT)
public class ElytraHelper extends Module {

    private static final int CHEST_SLOT = 6;
    private static final int BOOST_COOLDOWN_TICKS = 30;
    /** Blocks per tick below which a glide gets a firework. */
    private static final double SLOW_SPEED = 0.9;

    public final DoubleProperty minFall = new DoubleProperty("Min Fall", new DoubleValue(4, 40, 10, 1));
    public final BooleanProperty autoBoost = new BooleanProperty("Auto Boost", true);
    public final BooleanProperty swapBack = new BooleanProperty("Swap Back", true);

    private final HotbarUse swap = new HotbarUse();
    private int swappedFrom = -1;
    private int boostCooldown, startCooldown;

    @Override
    public boolean isSwappingHotbar() {
        return swap.active();
    }

    @Override
    protected void onDisable() {
        swap.release();
        swappedFrom = -1;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        swap.tick();
        if (boostCooldown > 0)
            boostCooldown--;
        if (startCooldown > 0)
            startCooldown--;

        if (mc.player.onGround() || mc.player.isInWater() || mc.player.isInLava()) {
            if (swappedFrom != -1 && swapBack.getValue() && canClick() && !mc.player.isFallFlying())
                swapChest(swappedFrom);
            swappedFrom = -1;
            return;
        }
        boolean wearing = usable(mc.player.getItemBySlot(EquipmentSlot.CHEST));
        if (!wearing) {
            if (mc.player.fallDistance < minFall.getValue().getInput() || !canClick())
                return;
            int slot = HotbarUse.findMenuSlot(ElytraHelper::usable);
            if (slot == -1)
                return;
            swapChest(slot);
            swappedFrom = slot;
            return;
        }
        if (!mc.player.isFallFlying()) {
            if (swappedFrom != -1 && mc.player.getDeltaMovement().y < -0.3 && startCooldown == 0) {
                mc.player.tryToStartFallFlying();
                startCooldown = 3;
            }
            return;
        }
        if (autoBoost.getValue() && boostCooldown == 0 && mc.gui.screen() == null
                && mc.player.getDeltaMovement().length() < SLOW_SPEED) {
            int rocket = HotbarUse.find(s -> s.is(Items.FIREWORK_ROCKET));
            if (rocket != -1) {
                swap.use(rocket);
                boostCooldown = BOOST_COOLDOWN_TICKS;
            }
        }
    };

    private static boolean usable(ItemStack stack) {
        return stack != null && stack.is(Items.ELYTRA) && stack.getDamageValue() < stack.getMaxDamage() - 1;
    }

    /** Container clicks are only made with no screen or the inventory open, where the slot numbers are the menu's. */
    private static boolean canClick() {
        return mc.gui.screen() == null || mc.gui.screen() instanceof InventoryScreen;
    }

    /** Swaps the chest slot with another slot through the cursor: pick up, put down, put the displaced item back. */
    private static void swapChest(int slot) {
        int id = mc.player.inventoryMenu.containerId;
        mc.gameMode.handleContainerInput(id, slot, 0, ContainerInput.PICKUP, mc.player);
        mc.gameMode.handleContainerInput(id, CHEST_SLOT, 0, ContainerInput.PICKUP, mc.player);
        mc.gameMode.handleContainerInput(id, slot, 0, ContainerInput.PICKUP, mc.player);
    }
}
