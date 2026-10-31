package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.impl.blatant.KillAura;
import arsenic.module.impl.client.AntiBot;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.io.Keys;
import arsenic.utils.minecraft.ItemUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Raises the shield while a player close to you is swinging at you or has a weapon pointed at you, and lowers it when
 * the threat is gone. It stays out of the way while you attack or KillAura has a target.
 */
@ModuleInfo(name = "AutoShield", description = "Raises your shield when someone attacks you", category = ModuleCategory.COMBAT, tier = ModuleTier.LEGIT)
public class AutoShield extends Module {

    public final DoubleProperty range = new DoubleProperty("Range", new DoubleValue(2, 6, 4, 0.5));

    private boolean holding;

    @Override
    protected void onDisable() {
        if (holding)
            release();
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        boolean want = mc.gui.screen() == null && shieldReady() && !attacking() && threatened();
        if (want) {
            mc.options.keyUse.setDown(true);
            holding = true;
        } else if (holding) {
            release();
        }
    };

    private void release() {
        holding = false;
        mc.options.keyUse.setDown(mc.gui.screen() == null && Keys.isMouseDown(1));
    }

    /** A shield in either hand that is not on cooldown (an axe disables it for a while). */
    private boolean shieldReady() {
        ItemStack main = mc.player.getMainHandItem();
        ItemStack off = mc.player.getOffhandItem();
        return (main.is(Items.SHIELD) && !mc.player.getCooldowns().isOnCooldown(main))
                || (off.is(Items.SHIELD) && !mc.player.getCooldowns().isOnCooldown(off));
    }

    private boolean attacking() {
        KillAura aura = Arsenic.getArsenic().getModuleManager().getModuleByClass(KillAura.class);
        return mc.options.keyAttack.isDown() || (aura != null && aura.isEnabled() && aura.target != null);
    }

    private boolean threatened() {
        double max = range.getValue().getInput();
        Vec3 eyes = mc.player.getEyePosition(1f);
        for (Player p : mc.level.players()) {
            if (p == mc.player || !p.isAlive() || AntiBot.isBot(p)
                    || Arsenic.getArsenic().getFriendManager().isFriend(p))
                continue;
            double dist = mc.player.distanceTo(p);
            if (dist > max)
                continue;
            if (p.isSwinging())
                return true;
            // a weapon held up and looking at us from striking distance
            if (dist <= 3.5 && ItemUtils.isWeapon(p.getMainHandItem())) {
                Vec3 toUs = eyes.subtract(p.getEyePosition(1f)).normalize();
                if (p.getViewVector(1f).dot(toUs) > 0.9)
                    return true;
            }
        }
        return false;
    }
}
