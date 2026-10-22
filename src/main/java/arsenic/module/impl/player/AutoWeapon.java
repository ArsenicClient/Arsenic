package arsenic.module.impl.player;

import arsenic.module.ModuleTier;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventSilentRotation;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.utils.minecraft.ItemUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

@ModuleInfo(name = "AutoWeapon", category = ModuleCategory.COMBAT, tier = ModuleTier.LEGIT)
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
        HitResult hit = event.getRayTraceEntity();
        if (!(hit instanceof EntityHitResult entityHit) || !(entityHit.getEntity() instanceof Player player))
            return false;
        return player != mc.player && player.isAlive()
                && !Arsenic.getArsenic().getFriendManager().isFriend(player);
    }

    private void equipBestWeapon() {
        if (mc.player.isUsingItem()) return;
        for (Module m : Arsenic.getArsenic().getModuleManager().getModules())
            if (m.isEnabled() && m.isSwappingHotbar()) return;

        int best = bestWeaponSlot();
        if (best == -1) return;
        originalSlot = mc.player.getInventory().getSelectedSlot();
        weaponSlot = best;
        mc.player.getInventory().setSelectedSlot(best);
    }

    private void restoreSlot() {
        if (weaponSlot == -1) return;
        // If the player picked a different slot themselves, leave it alone
        if (mc.player != null && mc.player.getInventory().getSelectedSlot() == weaponSlot)
            mc.player.getInventory().setSelectedSlot(originalSlot);
        weaponSlot = -1;
        originalSlot = -1;
    }

    private int bestWeaponSlot() {
        int best = -1;
        double bestDamage = 0;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (!ItemUtils.isWeapon(stack)) continue;
            double damage = ItemUtils.attackDamage(stack);
            int sharpness = ItemUtils.enchantLevel(Enchantments.SHARPNESS, stack);
            if (sharpness > 0)
                damage += 0.5 * sharpness + 0.5;
            if (ItemUtils.isSword(stack)) damage += 0.01;
            if (damage > bestDamage) {
                bestDamage = damage;
                best = slot;
            }
        }
        return best;
    }
}
