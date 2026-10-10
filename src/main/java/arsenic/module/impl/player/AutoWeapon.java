package arsenic.module.impl.player;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventSilentRotation;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.util.MovingObjectPosition;

@ModuleInfo(name = "AutoWeapon", category = ModuleCategory.COMBAT)
public class AutoWeapon extends Module {

    // Slot to return to once we stop looking at a player
    private int originalSlot = -1;
    // Slot we switched to; -1 when we are not holding a swapped weapon
    private int weaponSlot = -1;

    @Override
    protected void onDisable() {
        restoreSlot();
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onRotation = event -> {
        if (isLookingAtPlayer(event)) {
            if (weaponSlot == -1)
                equipBestWeapon();
        } else {
            restoreSlot();
        }
    };

    // Uses the same ray the attack would use: the silent rotation, not the camera
    private boolean isLookingAtPlayer(EventSilentRotation.Post event) {
        MovingObjectPosition hit = event.getRayTraceEntity();
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY || !(hit.entityHit instanceof EntityPlayer))
            return false;
        EntityPlayer player = (EntityPlayer) hit.entityHit;
        return player != mc.thePlayer && player.isEntityAlive()
                && !Arsenic.getArsenic().getFriendManager().isFriend(player);
    }

    private void equipBestWeapon() {
        if (mc.thePlayer.isUsingItem()) return;
        for (Module m : Arsenic.getArsenic().getModuleManager().getModules())
            if (m.isEnabled() && m.isSwappingHotbar()) return;

        int best = bestWeaponSlot();
        if (best == -1) return;
        originalSlot = mc.thePlayer.inventory.currentItem;
        weaponSlot = best;
        mc.thePlayer.inventory.currentItem = best;
    }

    private void restoreSlot() {
        if (weaponSlot == -1) return;
        // If the player picked a different slot themselves, leave it alone
        if (mc.thePlayer != null && mc.thePlayer.inventory.currentItem == weaponSlot)
            mc.thePlayer.inventory.currentItem = originalSlot;
        weaponSlot = -1;
        originalSlot = -1;
    }

    private int bestWeaponSlot() {
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
        return best;
    }
}
