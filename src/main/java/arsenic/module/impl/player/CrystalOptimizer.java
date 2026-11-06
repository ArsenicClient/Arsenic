package arsenic.module.impl.player;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventAttack;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;

/**
 * Removes an end crystal on your side the moment you hit it, instead of waiting for the server to say it broke, so
 * the next crystal can go down straight away. It leaves crystals alone while you have Weakness, since the hit may not
 * break them then.
 */
@ModuleInfo(name = "CrystalOptimizer", description = "Removes crystals as soon as you hit them", category = ModuleCategory.PLAYER, tier = ModuleTier.LEGIT)
public class CrystalOptimizer extends Module {

    @RequiresPlayer
    @EventLink
    public final Listener<EventAttack> onAttack = event -> {
        Entity target = event.getTarget();
        if (!(target instanceof EndCrystal) || target.isRemoved() || mc.level == null)
            return;
        if (mc.player.hasEffect(MobEffects.WEAKNESS))
            return;
        // The attack packet has already gone out (EventAttack fires from Player.attack), so this is client-side only.
        mc.level.removeEntity(target.getId(), Entity.RemovalReason.KILLED);
    };
}
