package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.HotbarUse;
import arsenic.utils.minecraft.ItemUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.concurrent.ThreadLocalRandom;

/**
 * When you hit with a sword, it swaps to the hotbar mace with the highest Breach just before the attack is sent, so the
 * hit lands with Breach's armour piercing on the sword's charged swing, then swaps back to the sword a tick later.
 */
@ModuleInfo(name = "BreachSwap", description = "Swaps to a Breach mace when you hit with a sword", category = ModuleCategory.COMBAT, tier = ModuleTier.LEGIT)
public class BreachSwap extends Module {

    public final DoubleProperty chance = new DoubleProperty("Chance", new DoubleValue(0, 100, 100, 1));

    private final HotbarUse hotbar = new HotbarUse();
    /** Ticks to keep the mace before swapping back; also blocks another swap while it is above zero. */
    private int hold;

    @Override
    public boolean isSwappingHotbar() {
        return hotbar.active();
    }

    @Override
    protected void onEnable() {
        hold = 0;
    }

    @Override
    protected void onDisable() {
        if (mc.player != null)
            hotbar.release();
        hold = 0;
    }

    /**
     * Called from the head of MultiPlayerGameMode.attack, before the carried item is synced, so the slot change goes
     * out ahead of the attack packet and the hit is dealt with the mace.
     */
    public void onAttack(Player attacker, Entity target) {
        if (mc.player == null || mc.level == null || attacker != mc.player || target == null)
            return;
        if (ThreadLocalRandom.current().nextDouble() * 100 > chance.getValue().getInput())
            hold = 1;
        if (hold > 0 || hotbar.active())
            return;
        // a falling hit is a smash: leave that to the mace you already hold or MaceHelper
        if (mc.player.fallDistance > 1.5)
            return;
        if (!ItemUtils.isSword(mc.player.getMainHandItem()))
            return;
        int mace = bestBreachMace();
        if (mace == -1)
            return;
        hotbar.select(mace);
        hold = 1;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (hold > 0) {
            hold--;
            return;
        }
        if (hotbar.active())
            hotbar.release();
    };

    /** Hotbar slot of the mace with the highest Breach level, or -1 with no mace. */
    private int bestBreachMace() {
        int best = -1;
        int bestLevel = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.is(Items.MACE))
                continue;
            int level = ItemUtils.enchantLevel(Enchantments.BREACH, stack);
            if (level > bestLevel) {
                bestLevel = level;
                best = i;
            }
        }
        return best;
    }
}
