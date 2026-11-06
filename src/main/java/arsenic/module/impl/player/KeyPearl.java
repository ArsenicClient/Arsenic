package arsenic.module.impl.player;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.minecraft.HotbarUse;
import arsenic.utils.timer.MSTimer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Bind it to a key: turning it on switches to an ender pearl, throws it, switches back and turns itself off.
 */
@ModuleInfo(name = "KeyPearl", description = "Swaps to a pearl, throws it and swaps back when enabled", category = ModuleCategory.PLAYER, tier = ModuleTier.BLATANT)
public class KeyPearl extends Module {

    public final RangeProperty delay = new RangeProperty("Delay (ms)", new RangeValue(0, 50, 10, 30, 1));

    private final HotbarUse swap = new HotbarUse();
    private final MSTimer timer = new MSTimer();
    private long nextDelay;
    private boolean thrown;

    @Override
    public boolean isSwappingHotbar() {
        return swap.active();
    }

    @Override
    protected void onEnable() {
        thrown = false;
        reroll();
    }

    @Override
    protected void onDisable() {
        swap.release();
        thrown = false;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (mc.gameMode == null || mc.gui.screen() != null || !mc.isWindowActive())
            return;
        if (timer.getTime() < nextDelay)
            return;

        if (thrown) {
            swap.release();
            setEnabled(false);
            return;
        }
        if (!isPearl(mc.player.getMainHandItem())) {
            if (mc.player.isUsingItem())
                return;
            int slot = HotbarUse.find(s -> isPearl(s) && !mc.player.getCooldowns().isOnCooldown(s));
            if (slot == -1) {
                setEnabled(false);
                return;
            }
            swap.select(slot);
            reroll();
            return;
        }
        if (mc.player.getCooldowns().isOnCooldown(mc.player.getMainHandItem())) {
            swap.release();
            setEnabled(false);
            return;
        }
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        thrown = true;
        reroll();
    };

    private void reroll() {
        timer.reset();
        nextDelay = (long) delay.getValue().getRandomInRange();
    }

    private static boolean isPearl(ItemStack stack) {
        return stack.is(Items.ENDER_PEARL);
    }
}
