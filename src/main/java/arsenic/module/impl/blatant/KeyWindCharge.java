package arsenic.module.impl.blatant;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.minecraft.HotbarUse;
import arsenic.utils.timer.MSTimer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Bind it to a key: turning it on throws one wind charge from the hotbar, puts the old slot back and turns itself off.
 */
@ModuleInfo(name = "KeyWindCharge", description = "Throws a wind charge when enabled, then turns itself off", category = ModuleCategory.COMBAT, tier = ModuleTier.BLATANT)
public class KeyWindCharge extends Module {

    public enum Mode {
        /** Switches to the charge, throws it and switches back, with a random wait between each step. */
        Normal,
        /** Throws in a single tick and puts the slot back the next one. */
        Silent
    }

    public final EnumProperty<Mode> mode = new EnumProperty<>("Mode", Mode.Normal);
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

        if (mode.getValue() == Mode.Silent) {
            if (thrown) {
                swap.tick();
                setEnabled(false);
                return;
            }
            if (mc.player.isUsingItem())
                return;
            int slot = findCharge();
            if (slot == -1) {
                setEnabled(false);
                return;
            }
            swap.use(slot);
            thrown = true;
            return;
        }

        if (timer.getTime() < nextDelay)
            return;
        if (thrown) {
            swap.release();
            setEnabled(false);
            return;
        }
        if (!isCharge(mc.player.getMainHandItem())) {
            if (mc.player.isUsingItem())
                return;
            int slot = findCharge();
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

    private int findCharge() {
        return HotbarUse.find(s -> isCharge(s) && !mc.player.getCooldowns().isOnCooldown(s));
    }

    private static boolean isCharge(ItemStack stack) {
        return stack.is(Items.WIND_CHARGE);
    }
}
