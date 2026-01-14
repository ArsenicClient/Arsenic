package arsenic.utils.minecraft;

import net.minecraft.block.*;
import net.minecraft.client.Minecraft;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.Slot;
import net.minecraft.item.*;
import net.minecraft.util.DamageSource;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ContainerUtils {

    private static final Minecraft mc = Minecraft.getMinecraft();

    //Inv Manager Actions

    public static void click(int slot) {
        mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId, slot, 0, 1, mc.thePlayer);
    }

    public static void drop(int slot) {
        mc.playerController.windowClick(0, slot, 1, 4, mc.thePlayer);
    }

    public static void swap(int slot, int targetSlot) {
        mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId, slot, targetSlot, 2, mc.thePlayer);
    }


    public static List<SlotItem> getInventoryItems() {
        return IntStream.range(9, 45)
                .mapToObj(i -> new SlotItem(i, getItemStack(i)))
                .filter(si -> si.item != null)
                .collect(Collectors.toList());
    }

    public static List<SlotItem> getInventoryItemsWithArmor() {
        return IntStream.range(5, 45)
                .mapToObj(i -> new SlotItem(i, getItemStack(i)))
                .filter(si -> si.item != null)
                .collect(Collectors.toList());
    }

    //Get best items
    public static int getBestWeapon() {
        return getInventoryItems().stream()
                .filter(s -> s.item.getItem() instanceof ItemSword)
                .max(Comparator.comparingDouble(si -> getDamage(si.item)))
                .map(si -> si.slot).
                orElse(-1);
    }

    public static int getMostProjectiles() {
        return getInventoryItems().stream()
                .filter(si -> isProjectiles(si.item))
                .max(Comparator.comparingDouble(si -> si.item.stackSize))
                .map(si -> si.slot).
                orElse(-1);
    }

    public static int getMostBlocks() {
        return getInventoryItems().stream()
                .filter(si -> si.item.getItem() instanceof ItemBlock && canBePlaced((ItemBlock) si.item.getItem()))
                .max(Comparator.comparingDouble(si -> si.item.stackSize))
                .map(si -> si.slot).
                orElse(-1);
    }

    public static int getBiggestStack(Item item) {
        return getInventoryItems().stream()
                .filter(si -> si.item.getItem().getRegistryName().equals(item.getRegistryName()))
                .max(Comparator.comparingDouble(si -> si.item.stackSize))
                .map(si -> si.slot).
                orElse(-1);
    }

    public static int getBestBow() {
        return getInventoryItems().stream()
                .filter(si -> si.item.getItem() instanceof ItemBow)
                .max(Comparator.comparingDouble(si -> getPower(si.item)))
                .map(si -> si.slot).
                orElse(-1);
    }

    public static <T extends ItemTool> int getBestTool(Class<T> toolClass) {
        return getInventoryItems().stream()
                .filter(si -> toolClass.isInstance(si.item.getItem()))
                .max(Comparator.comparingDouble(si -> getEffeciency(si.item)))
                .map(si -> si.slot).
                orElse(-1);
    }

    public static int getBestArmor(int index) {
        return getInventoryItemsWithArmor().stream()
                .filter(si -> si.item.getItem() instanceof ItemArmor && ((ItemArmor) si.item.getItem()).armorType == index)
                .max(Comparator.comparingDouble(si -> getArmorLevel(si.item)))
                .map(si -> si.slot).
                orElse(-1);
    }



    public static double getEffeciency(ItemStack stack) {
        ItemTool tool = (ItemTool) stack.getItem();
        float value = tool.getToolMaterial().getEfficiencyOnProperMaterial();

        // Add enchantment bonus
        int efficiencyLevel = net.minecraft.enchantment.EnchantmentHelper.getEnchantmentLevel(
                net.minecraft.enchantment.Enchantment.efficiency.effectId, stack);
        value += efficiencyLevel * 2;
        return value;
    }


    public static boolean canBePlaced(ItemBlock itemBlock) {
        Block block = itemBlock.getBlock();
        if (block == null) {
            return false;
        }
        return !isInteractable(block) && !(block instanceof BlockTNT) && !(block instanceof BlockSlab) && !(block instanceof BlockWeb) && !(block instanceof BlockLever) && !(block instanceof BlockButton) && !(block instanceof BlockSkull) && !(block instanceof BlockLiquid) && !(block instanceof BlockCactus) && !(block instanceof BlockCarpet) && !(block instanceof BlockTripWire) && !(block instanceof BlockTripWireHook) && !(block instanceof BlockTallGrass) && !(block instanceof BlockFlower) && !(block instanceof BlockFlowerPot) && !(block instanceof BlockSign) && !(block instanceof BlockLadder) && !(block instanceof BlockTorch) && !(block instanceof BlockRedstoneTorch) && !(block instanceof BlockFence) && !(block instanceof BlockPane) && !(block instanceof BlockStainedGlassPane) && !(block instanceof BlockGravel) && !(block instanceof BlockClay) && !(block instanceof BlockSand) && !(block instanceof BlockSoulSand);
    }

    public static boolean isInteractable(Block block) {
        return block instanceof BlockFurnace || block instanceof BlockFenceGate || block instanceof BlockChest || block instanceof BlockEnderChest || block instanceof BlockEnchantmentTable || block instanceof BlockBrewingStand || block instanceof BlockBed || block instanceof BlockDropper || block instanceof BlockDispenser || block instanceof BlockHopper || block instanceof BlockAnvil || block == Blocks.crafting_table;
    }

    public static float getEfficiency(final ItemStack itemStack, final Block block) {
        float getStrVsBlock = itemStack.getStrVsBlock(block);
        if (getStrVsBlock > 1.0f) {
            final int getEnchantmentLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.efficiency.effectId, itemStack);
            if (getEnchantmentLevel > 0) {
                getStrVsBlock += getEnchantmentLevel * getEnchantmentLevel + 1;
            }
        }
        return getStrVsBlock;
    }

    public static double getDamage(final ItemStack itemStack) {
        double getAmount = 0;
        for (final Map.Entry<String, AttributeModifier> entry : itemStack.getAttributeModifiers().entries()) {
            if (entry.getKey().equals("generic.attackDamage")) {
                getAmount = entry.getValue().getAmount();
                break;
            }
        }
        return getAmount + EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId, itemStack) * 1.25;
    }


    public static float getPower(ItemStack stack) {
        float score = 0;
        Item item = stack.getItem();
        if (item instanceof ItemBow) {
            score += EnchantmentHelper.getEnchantmentLevel(Enchantment.power.effectId, stack);
            score += EnchantmentHelper.getEnchantmentLevel(Enchantment.flame.effectId, stack) * 0.5;
            score += EnchantmentHelper.getEnchantmentLevel(Enchantment.unbreaking.effectId, stack) * 0.1;
        }
        return score;
    }



    public static ItemStack getItemStack(int i) {
        Slot slot = mc.thePlayer.inventoryContainer.getSlot(i);
        if (slot == null) {
            return null;
        }
        return slot.getStack();
    }


    public static int getArmorLevel(final @NotNull ItemStack itemStack) {
        int level = 0;

        final Item item = itemStack.getItem();
        if (item == Items.diamond_helmet || item == Items.diamond_chestplate || item == Items.diamond_leggings || item == Items.diamond_boots)
            level += 15;
        else if (item == Items.iron_helmet || item == Items.iron_chestplate || item == Items.iron_leggings || item == Items.iron_boots)
            level += 10;
        else if (item == Items.golden_helmet || item == Items.golden_chestplate || item == Items.golden_leggings || item == Items.golden_boots)
            level += 5;
        else if (item == Items.chainmail_helmet || item == Items.chainmail_chestplate || item == Items.chainmail_leggings || item == Items.chainmail_boots)
            level += 5;

        level += getProtection(itemStack);

        return level;
    }

    public static int getProtection(final @NotNull ItemStack itemStack) {
        return ((ItemArmor)itemStack.getItem()).damageReduceAmount + EnchantmentHelper.getEnchantmentModifierDamage(new ItemStack[] { itemStack }, DamageSource.generic);
    }


    public static boolean isProjectiles(ItemStack itemStackInSlot) {
        return itemStackInSlot != null && (itemStackInSlot.getItem() instanceof ItemEgg || itemStackInSlot.getItem() instanceof ItemSnowball);
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