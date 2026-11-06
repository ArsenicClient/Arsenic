package arsenic.module.impl.ghost;

import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.minecraft.HotbarUse;
import arsenic.utils.minecraft.ItemUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/**
 * When you hit a player while holding a totem in your main hand, the hit is made with the sword from your hotbar
 * instead and the totem is put straight back in your hand.
 */
@ModuleInfo(name = "TotemHit", description = "Hits with your sword while you hold a totem", category = ModuleCategory.COMBAT, tier = ModuleTier.LEGIT)
public class TotemHit extends Module {

    public final BooleanProperty swapBack = new BooleanProperty("Swap Back", true);

    /** Set while our own redirected attack runs, so the mixin lets it through. */
    private boolean attacking;

    /**
     * Called from the head of MultiPlayerGameMode#attack. Returns true when the attack was redone with the sword and
     * the original totem-in-hand attack must be cancelled. The caller still swings afterwards, so the swing packet
     * follows the attack packet as a 1.9+ client sends it.
     */
    public boolean redirectAttack(Player attacker, Entity target) {
        if (attacking || !isEnabled() || mc.player == null || mc.gameMode == null || attacker != mc.player)
            return false;
        if (!(target instanceof Player))
            return false;
        if (!mc.player.getMainHandItem().is(Items.TOTEM_OF_UNDYING))
            return false;
        int sword = HotbarUse.find(ItemUtils::isSword);
        if (sword == -1)
            return false;

        int previous = mc.player.getInventory().getSelectedSlot();
        attacking = true;
        try {
            mc.player.getInventory().setSelectedSlot(sword);
            // attack() sends the carried item change before the attack packet
            mc.gameMode.attack(mc.player, target);
        } finally {
            attacking = false;
            if (swapBack.getValue())
                mc.player.getInventory().setSelectedSlot(previous);
        }
        return true;
    }

    @Override
    public boolean isSwappingHotbar() {
        return attacking;
    }

    @Override
    protected void onDisable() {
        attacking = false;
    }
}
