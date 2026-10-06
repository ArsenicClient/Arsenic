package arsenic.module.impl.player;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.blatant.KillAura;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;

@ModuleInfo(name = "AutoWeapon", category = ModuleCategory.COMBAT)
public class AutoWeapon extends Module {

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        KillAura aura = Arsenic.getArsenic().getModuleManager().getModuleByClass(KillAura.class);
        if (aura == null || !aura.isEnabled()) return;
        if (aura.target == null && mc.thePlayer.inventory.getCurrentItem() != null) return;

        for (Module m : Arsenic.getArsenic().getModuleManager().getModules())
            if (m.isEnabled() && m.isSwappingHotbar()) return;
        if (mc.thePlayer.isUsingItem()) return;

        int best = -1;
        double bestDamage = 0;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (stack == null || !(stack.getItem() instanceof ItemSword || stack.getItem() instanceof ItemTool)) continue;
            double damage = 0;
            for (AttributeModifier mod : stack.getAttributeModifiers().get(SharedMonsterAttributes.attackDamage.getAttributeUnlocalizedName())) {
                damage += mod.getAmount();
            }
            damage += 1.25 * EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId, stack);
            if (stack.getItem() instanceof ItemSword) damage += 0.01;
            if (damage > bestDamage) {
                bestDamage = damage;
                best = slot;
            }
        }
        if (best != -1 && mc.thePlayer.inventory.currentItem != best) {
            mc.thePlayer.inventory.currentItem = best;
        }
    };
}
