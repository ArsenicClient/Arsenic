package arsenic.module.impl.blatant;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.impl.client.TargetManager;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.HotbarUse;
import arsenic.utils.rotations.SilentRotationManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/**
 * Sets up the mace smash. With a target close, a wind charge thrown at your feet launches you, and the mace is in your
 * hand while you fall onto them (a smash hits harder for every block fallen). KillAura or your own click does the
 * attack. The hotbar goes back to where it was once you land or the target is gone.
 */
@ModuleInfo(name = "MaceHelper", description = "Launches you with a wind charge and holds the mace while you fall", category = ModuleCategory.COMBAT, tier = ModuleTier.BLATANT)
public class MaceHelper extends Module {

    private static final int COOLDOWN_TICKS = 25;

    public final DoubleProperty range = new DoubleProperty("Range", new DoubleValue(3, 8, 5, 0.5));
    public final DoubleProperty minFall = new DoubleProperty("Min Fall", new DoubleValue(1.5, 8, 2, 0.5));
    public final BooleanProperty windCharge = new BooleanProperty("Wind Charge", true);

    private final HotbarUse swap = new HotbarUse();
    private int cooldown;
    private boolean launch;

    @Override
    public boolean isSwappingHotbar() {
        return swap.active();
    }

    @Override
    protected void onDisable() {
        swap.release();
        launch = false;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        swap.tick();
        launch = false;
        if (cooldown > 0)
            cooldown--;
        int mace = HotbarUse.find(s -> s.is(Items.MACE));
        Player target = nearest();
        if (mace == -1 || target == null || mc.gui.screen() != null) {
            swap.release();
            return;
        }
        boolean smashing = !mc.player.onGround() && mc.player.getDeltaMovement().y < 0
                && mc.player.fallDistance >= minFall.getValue().getInput();
        if (smashing) {
            if (mc.player.getInventory().getSelectedSlot() != mace)
                swap.select(mace);
        } else if (mc.player.onGround()) {
            swap.release();
            launch = windCharge.getValue() && cooldown == 0
                    && HotbarUse.find(s -> s.is(Items.WIND_CHARGE) && !mc.player.getCooldowns().isOnCooldown(s)) != -1;
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        if (!launch)
            return;
        event.setPitch(90f);
        event.setSpeed(180f);
        event.setPreventDuplicateLook(true);
        event.setMovementFix(SilentRotationManager.MovementFix.SILENT);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onPost = event -> {
        if (!launch || event.getPitch() < 85f)
            return;
        int slot = HotbarUse.find(s -> s.is(Items.WIND_CHARGE) && !mc.player.getCooldowns().isOnCooldown(s));
        if (slot == -1)
            return;
        swap.use(slot);
        cooldown = COOLDOWN_TICKS;
        launch = false;
    };

    private Player nearest() {
        Player best = null;
        double reach = range.getValue().getInput();
        for (Player p : TargetManager.getTargets()) {
            double d = mc.player.distanceTo(p);
            if (d <= reach && (best == null || d < mc.player.distanceTo(best)))
                best = p;
        }
        return best;
    }
}
