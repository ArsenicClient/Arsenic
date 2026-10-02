package arsenic.utils.minecraft;

import arsenic.utils.java.UtilityClass;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.equipment.Equippable;

/**
 * Item questions that 1.8 answered with {@code instanceof ItemSword} and friends. Items are data
 * driven now - swords are a tag, armour is an equippable component, damage is an attribute - so
 * the checks live here instead of being spelled out at every call site.
 */
public class ItemUtils extends UtilityClass {

    public static boolean isSword(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(ItemTags.SWORDS);
    }

    public static boolean isAxe(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(ItemTags.AXES);
    }

    public static boolean isWeapon(ItemStack stack) {
        return isSword(stack) || isAxe(stack);
    }

    public static boolean isBlock(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof BlockItem;
    }

    /** The armour slot this stack equips into, or {@code null} if it is not armour. */
    public static EquipmentSlot getArmorSlot(ItemStack stack) {
        if (stack == null || stack.isEmpty())
            return null;
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null || !equippable.slot().isArmor())
            return null;
        return equippable.slot();
    }

    /** 1.8 armour index: 0 helmet, 1 chestplate, 2 leggings, 3 boots. */
    public static EquipmentSlot armorSlotFromIndex(int index) {
        return switch (index) {
            case 0 -> EquipmentSlot.HEAD;
            case 1 -> EquipmentSlot.CHEST;
            case 2 -> EquipmentSlot.LEGS;
            default -> EquipmentSlot.FEET;
        };
    }

    /** The value an attribute would have with only this item equipped in {@code slot}. */
    public static double attribute(ItemStack stack, Holder<Attribute> attribute, double base, EquipmentSlot slot) {
        ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        return modifiers.compute(attribute, base, slot);
    }

    /** Attack damage of a held item, excluding enchantments. */
    public static double attackDamage(ItemStack stack) {
        return attribute(stack, Attributes.ATTACK_DAMAGE, 1.0, EquipmentSlot.MAINHAND);
    }

    public static double armorPoints(ItemStack stack) {
        EquipmentSlot slot = getArmorSlot(stack);
        return slot == null ? 0 : attribute(stack, Attributes.ARMOR, 0, slot) + attribute(stack, Attributes.ARMOR_TOUGHNESS, 0, slot) * 0.5;
    }

    public static int enchantLevel(ResourceKey<Enchantment> enchantment, ItemStack stack) {
        if (stack == null || stack.isEmpty() || mc.level == null)
            return 0;
        Holder<Enchantment> holder = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(enchantment).orElse(null);
        return holder == null ? 0 : EnchantmentHelper.getItemEnchantmentLevel(holder, stack);
    }
}
