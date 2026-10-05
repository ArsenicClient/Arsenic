package arsenic.utils.minecraft;

import arsenic.utils.java.UtilityClass;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Inventory slot numbers are the player's inventory menu: 5-8 armour, 9-35 main inventory, 36-44
 * hotbar - the same layout 1.8 used, so callers that hardcode slot indices still line up.
 */
public class ContainerUtils extends UtilityClass {

    public static void click(int slot) {
        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, slot, 0, ContainerInput.QUICK_MOVE, mc.player);
    }

    public static void drop(int slot) {
        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, slot, 1, ContainerInput.THROW, mc.player);
    }

    public static void swap(int slot, int targetSlot) {
        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, slot, targetSlot, ContainerInput.SWAP, mc.player);
    }

    public static List<SlotItem> getInventoryItems() {
        return IntStream.range(9, 45)
                .mapToObj(i -> new SlotItem(i, getItemStack(i)))
                .filter(si -> si.item != null && !si.item.isEmpty())
                .collect(Collectors.toList());
    }

    public static List<SlotItem> getInventoryItemsWithArmor() {
        return IntStream.range(5, 45)
                .mapToObj(i -> new SlotItem(i, getItemStack(i)))
                .filter(si -> si.item != null && !si.item.isEmpty())
                .collect(Collectors.toList());
    }

    public static int getBestWeapon() {
        return getInventoryItems().stream()
                .filter(s -> ItemUtils.isSword(s.item))
                .max(Comparator.comparingDouble(si -> getDamage(si.item)))
                .map(si -> si.slot)
                .orElse(-1);
    }

    public static int getMostProjectiles() {
        return getInventoryItems().stream()
                .filter(si -> isProjectiles(si.item))
                .max(Comparator.comparingDouble(si -> si.item.getCount()))
                .map(si -> si.slot)
                .orElse(-1);
    }

    public static int getMostBlocks() {
        return getInventoryItems().stream()
                .filter(si -> si.item.getItem() instanceof BlockItem && canBePlaced((BlockItem) si.item.getItem()))
                .max(Comparator.comparingDouble(si -> si.item.getCount()))
                .map(si -> si.slot)
                .orElse(-1);
    }

    public static int getBiggestStack(Item item) {
        return getInventoryItems().stream()
                .filter(si -> si.item.is(item))
                .max(Comparator.comparingDouble(si -> si.item.getCount()))
                .map(si -> si.slot)
                .orElse(-1);
    }

    public static int getBestBow() {
        return getInventoryItems().stream()
                .filter(si -> si.item.getItem() instanceof BowItem)
                .max(Comparator.comparingDouble(si -> getPower(si.item)))
                .map(si -> si.slot)
                .orElse(-1);
    }

    /** Best tool of a kind, e.g. {@code ItemTags.PICKAXES}. */
    public static int getBestTool(TagKey<Item> toolTag) {
        return getInventoryItems().stream()
                .filter(si -> si.item.is(toolTag))
                .max(Comparator.comparingDouble(si -> getEffeciency(si.item)))
                .map(si -> si.slot)
                .orElse(-1);
    }

    public static int getBestArmor(int index) {
        EquipmentSlot wanted = ItemUtils.armorSlotFromIndex(index);
        return getInventoryItemsWithArmor().stream()
                .filter(si -> ItemUtils.getArmorSlot(si.item) == wanted)
                .max(Comparator.comparingDouble(si -> getArmorLevel(si.item)))
                .map(si -> si.slot)
                .orElse(-1);
    }

    /** Mining speed against stone - a fair stand-in for 1.8's tool-material efficiency. */
    public static double getEffeciency(ItemStack stack) {
        double value = Math.max(stack.getDestroySpeed(Blocks.STONE.defaultBlockState()),
                Math.max(stack.getDestroySpeed(Blocks.OAK_LOG.defaultBlockState()), stack.getDestroySpeed(Blocks.DIRT.defaultBlockState())));
        value += ItemUtils.enchantLevel(Enchantments.EFFICIENCY, stack) * 2;
        return value;
    }

    public static boolean canBePlaced(BlockItem blockItem) {
        Block block = blockItem.getBlock();
        if (block == null || isInteractable(block) || block instanceof FallingBlock || block instanceof TntBlock)
            return false;
        // a full, solid cube - this rules out slabs, webs, plants, torches, panes, fences and so on
        BlockState state = block.defaultBlockState();
        return state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) && !(block instanceof SoulSandBlock);
    }

    public static boolean isInteractable(Block block) {
        return block instanceof AbstractFurnaceBlock || block instanceof FenceGateBlock || block instanceof ChestBlock
                || block instanceof EnderChestBlock || block instanceof EnchantingTableBlock || block instanceof BrewingStandBlock
                || block instanceof BedBlock || block instanceof DispenserBlock || block instanceof HopperBlock
                || block instanceof AnvilBlock || block instanceof CraftingTableBlock || block instanceof BarrelBlock
                || block instanceof ShulkerBoxBlock || block instanceof DoorBlock || block instanceof TrapDoorBlock;
    }

    public static float getEfficiency(final ItemStack itemStack, final BlockState block) {
        return PlayerUtils.getEfficiency(itemStack, block);
    }

    public static double getDamage(final ItemStack itemStack) {
        return ItemUtils.attackDamage(itemStack) + ItemUtils.enchantLevel(Enchantments.SHARPNESS, itemStack) * 1.25;
    }

    public static float getPower(ItemStack stack) {
        float score = 0;
        if (stack.getItem() instanceof BowItem) {
            score += ItemUtils.enchantLevel(Enchantments.POWER, stack);
            score += ItemUtils.enchantLevel(Enchantments.FLAME, stack) * 0.5f;
            score += ItemUtils.enchantLevel(Enchantments.UNBREAKING, stack) * 0.1f;
        }
        return score;
    }

    public static ItemStack getItemStack(int i) {
        if (i < 0 || i >= mc.player.inventoryMenu.slots.size())
            return null;
        Slot slot = mc.player.inventoryMenu.getSlot(i);
        return slot == null ? null : slot.getItem();
    }

    public static int getArmorLevel(final @NotNull ItemStack itemStack) {
        return (int) Math.round(ItemUtils.armorPoints(itemStack) * 2) + getProtection(itemStack);
    }

    public static int getProtection(final @NotNull ItemStack itemStack) {
        return ItemUtils.enchantLevel(Enchantments.PROTECTION, itemStack);
    }

    public static boolean isProjectiles(ItemStack stack) {
        return stack != null && (stack.getItem() instanceof EggItem || stack.getItem() instanceof SnowballItem);
    }

    public static class SlotItem {
        public final int slot;
        public final ItemStack item;

        public SlotItem(int slot, ItemStack item) {
            this.slot = slot;
            this.item = item;
        }
    }
}
