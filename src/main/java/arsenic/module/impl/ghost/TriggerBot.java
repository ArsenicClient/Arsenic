package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.impl.client.TargetManager;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.minecraft.PlayerUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

/** Attacks the moment your crosshair is on a target, once the weapon has recharged. */
@ModuleInfo(name = "TriggerBot", description = "Attacks when your crosshair is on a player", category = ModuleCategory.COMBAT, tier = ModuleTier.LEGIT)
public class TriggerBot extends Module {

    public final BooleanProperty cooldown = new BooleanProperty("Attack Cooldown", true);
    public final BooleanProperty weaponOnly = new BooleanProperty("Weapon Only", true);

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (mc.gui.screen() != null || mc.player.isUsingItem())
            return;
        if (weaponOnly.getValue() && !PlayerUtils.isPlayerHoldingWeapon())
            return;
        if (cooldown.getValue() && mc.player.getAttackStrengthScale(0.5f) < 0.95f)
            return;
        if (!(mc.hitResult instanceof EntityHitResult hit) || !(hit.getEntity() instanceof Player target)
                || !TargetManager.isValidTarget(target))
            return;
        mc.gameMode.attack(mc.player, target);
        PlayerUtils.swingItem();
    };
}
