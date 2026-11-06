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
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.io.Keys;
import arsenic.utils.timer.MSTimer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Switches to your best pickaxe while you mine the block under your feet, and switches back afterwards. */
@ModuleInfo(name = "AutoPickaxe", description = "Switches to a pickaxe when mining the block below you", category = ModuleCategory.PLAYER, tier = ModuleTier.LEGIT)
public class AutoPickaxe extends Module {
    public final BooleanProperty onlyWhenMining = new BooleanProperty("Only When Mining", true);
    public final BooleanProperty fromInventory = new BooleanProperty("From Inventory", true);
    public final RangeProperty delay = new RangeProperty("Delay (ms)", new RangeValue(0, 300, 30, 50, 1));

    /** Best pickaxe first. */
    private static final Item[] PICKAXES = {
            Items.NETHERITE_PICKAXE, Items.DIAMOND_PICKAXE, Items.IRON_PICKAXE,
            Items.STONE_PICKAXE, Items.GOLDEN_PICKAXE, Items.WOODEN_PICKAXE
    };

    private final MSTimer timer = new MSTimer();
    private double nextDelay;
    /** Hotbar slot that was selected before switching, -1 when nothing to restore. */
    private int previousSlot = -1;
    /** Inventory slot the pickaxe was swapped out of, -1 when it came from the hotbar. */
    private int swappedFrom = -1;

    @Override
    protected void onEnable() {
        previousSlot = -1;
        swappedFrom = -1;
        rollTimer();
    }

    @Override
    public void onDisable() {
        previousSlot = -1;
        swappedFrom = -1;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (mc.level == null || mc.gameMode == null || mc.gui.screen() != null || !mc.isWindowActive()) {
            return;
        }

        BlockPos below = mc.player.blockPosition().below();
        boolean notMining = onlyWhenMining.getValue() && !Keys.isMouseDown(0);
        HitResult hit = mc.hitResult;
        boolean lookingBelow = hit != null && hit.getType() == HitResult.Type.BLOCK
                && ((BlockHitResult) hit).getBlockPos().equals(below);

        if (!lookingBelow || notMining) {
            if (!restore()) rollTimer();
            return;
        }

        BlockState state = mc.level.getBlockState(below);
        if (state.canBeReplaced() || state.is(Blocks.BEDROCK)) {
            if (!restore()) rollTimer();
            return;
        }

        if (isPickaxe(mc.player.getMainHandItem())) {
            rollTimer();
            return;
        }

        if (!timer.hasTimeElapsed(nextDelay)) {
            return;
        }

        int hotbar = findPickaxe(0, 9);
        if (hotbar != -1) {
            if (previousSlot == -1) previousSlot = mc.player.getInventory().getSelectedSlot();
            swappedFrom = -1;
            mc.player.getInventory().setSelectedSlot(hotbar);
            rollTimer();
        } else if (fromInventory.getValue()) {
            int inv = findPickaxe(9, 36);
            if (inv != -1) {
                int selected = mc.player.getInventory().getSelectedSlot();
                previousSlot = selected;
                swappedFrom = inv;
                swap(inv, selected);
                rollTimer();
            }
        }
    };

    /**
     * Puts things back once the switch is no longer needed.
     * @return true while a restore is pending or was just done, false when there was nothing to restore
     */
    private boolean restore() {
        if (previousSlot == -1) {
            return false;
        }
        if (!timer.hasTimeElapsed(nextDelay)) {
            return true;
        }
        if (swappedFrom != -1) {
            swap(swappedFrom, previousSlot);
        } else if (previousSlot >= 0 && previousSlot <= 8) {
            mc.player.getInventory().setSelectedSlot(previousSlot);
        }
        previousSlot = -1;
        swappedFrom = -1;
        rollTimer();
        return true;
    }

    /** Swaps a main-inventory slot (9-35, same index in the player menu) with a hotbar slot. */
    private void swap(int inventorySlot, int hotbarSlot) {
        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, inventorySlot, hotbarSlot, ContainerInput.SWAP, mc.player);
    }

    private int findPickaxe(int from, int to) {
        for (Item pickaxe : PICKAXES) {
            for (int i = from; i < to; i++) {
                if (mc.player.getInventory().getItem(i).getItem() == pickaxe) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static boolean isPickaxe(ItemStack stack) {
        Item item = stack.getItem();
        for (Item pickaxe : PICKAXES) {
            if (item == pickaxe) return true;
        }
        return false;
    }

    private void rollTimer() {
        timer.reset();
        nextDelay = delay.getValue().getRandomInRange();
    }
}
