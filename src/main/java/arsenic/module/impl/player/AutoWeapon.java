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
import net.minecraft.world.item.ItemStack;
import arsenic.utils.minecraft.ItemUtils;
import arsenic.utils.minecraft.ContainerUtils;

@ModuleInfo(name = "AutoWeapon", category = ModuleCategory.COMBAT)
public class AutoWeapon extends Module {

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        KillAura aura = Arsenic.getArsenic().getModuleManager().getModuleByClass(KillAura.class);
        if (aura == null || !aura.isEnabled()) return;
        if (aura.target == null && mc.player.getMainHandItem() != null) return;

        AutoSoup soup = Arsenic.getArsenic().getModuleManager().getModuleByClass(AutoSoup.class);
        if (soup != null && soup.isEnabled() && soup.isSwapping()) return;
        if (mc.player.isUsingItem()) return;

        int best = -1;
        double bestDamage = 0;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack == null || !(ItemUtils.isWeapon(stack) || stack.is(net.minecraft.tags.ItemTags.PICKAXES) || stack.is(net.minecraft.tags.ItemTags.SHOVELS))) continue;
            double damage = ContainerUtils.getDamage(stack);
            if (ItemUtils.isSword(stack)) damage += 0.01;
            if (damage > bestDamage) {
                bestDamage = damage;
                best = slot;
            }
        }
        if (best != -1 && mc.player.getInventory().getSelectedSlot() != best) {
            mc.player.getInventory().setSelectedSlot(best);
        }
    };
}
