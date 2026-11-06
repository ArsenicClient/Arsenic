package arsenic.module.impl.blatant;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.impl.client.TargetManager;
import arsenic.module.property.ShowWhen;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.HotbarUse;
import arsenic.utils.minecraft.PlayerUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Hits a raised shield several times a tick with a mace to wear its durability down during mace fights. */
@ModuleInfo(name = "ShieldDrain", description = "Repeatedly hits a target's shield with a mace to drain its durability", category = ModuleCategory.COMBAT, tier = ModuleTier.BLATANT)
public class ShieldDrain extends Module {

    public final DoubleProperty horizontalDist = new DoubleProperty("Horizontal Target Dist", new DoubleValue(0, 6, 5, 0.1));
    public final DoubleProperty maxRange = new DoubleProperty("Max Range", new DoubleValue(3, 6, 4.5, 0.1));
    public final DoubleProperty minFall = new DoubleProperty("Min Fall Distance", new DoubleValue(0, 50, 0, 0.5));
    public final BooleanProperty crosshairOnly = new BooleanProperty("Only On Crosshair", false);
    public final BooleanProperty requireMace = new BooleanProperty("Require Mace", true);
    public final BooleanProperty pauseOnGround = new BooleanProperty("Pause On Ground", false);
    public final DoubleProperty clicksPerTick = new DoubleProperty("Clicks Per Tick", new DoubleValue(1, 10, 2, 1));
    @ShowWhen(property = "Require Mace", is = "false")
    public final BooleanProperty autoSwap = new BooleanProperty("Auto Swap", true);
    @ShowWhen(property = "Require Mace", is = "false")
    @ShowWhen(property = "Auto Swap", is = "true")
    public final BooleanProperty silent = new BooleanProperty("Silent", false);

    /** Set while a drain is in progress this tick, so other mace modules can hold off their own hits. */
    private static boolean draining;

    private final HotbarUse swap = new HotbarUse();

    /** Whether ShieldDrain attacked a shield this tick. */
    public static boolean isDraining() {
        return draining;
    }

    @Override
    public boolean isSwappingHotbar() {
        return swap.active();
    }

    @Override
    protected void onDisable() {
        draining = false;
        swap.release();
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        draining = false;
        if (mc.level == null || mc.gameMode == null || mc.gui.screen() != null || !mc.isWindowActive())
            return;
        if (mc.player.isUsingItem())
            return;
        if ((pauseOnGround.getValue() && mc.player.onGround()) || mc.player.fallDistance < minFall.getValue().getInput()) {
            swap.release();
            return;
        }
        Player target = findTarget();
        if (target == null || mc.player.distanceTo(target) > maxRange.getValue().getInput()) {
            swap.release();
            return;
        }

        if (!mc.player.getMainHandItem().is(Items.MACE)) {
            if (requireMace.getValue() || !autoSwap.getValue())
                return;
            int mace = HotbarUse.find(s -> s.is(Items.MACE));
            if (mace == -1)
                return;
            if (silent.getValue())
                swap.select(mace);
            else
                mc.player.getInventory().setSelectedSlot(mace);
            // attack from the next tick so the slot change never lands in the same tick as the hits
            return;
        }

        draining = true;
        int clicks = (int) clicksPerTick.getValue().getInput();
        for (int i = 0; i < clicks; i++) {
            mc.gameMode.attack(mc.player, target);
            PlayerUtils.swingItem();
        }
    };

    private Player findTarget() {
        if (mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Player player && isShieldTarget(player))
            return player;
        if (crosshairOnly.getValue())
            return null;

        double max = horizontalDist.getValue().getInput();
        double maxSq = max * max;
        Player best = null;
        double bestSq = Double.MAX_VALUE;
        for (Player player : mc.level.players()) {
            if (!isShieldTarget(player))
                continue;
            double dx = player.getX() - mc.player.getX();
            double dz = player.getZ() - mc.player.getZ();
            double distSq = dx * dx + dz * dz;
            if (distSq <= maxSq && distSq < bestSq) {
                bestSq = distSq;
                best = player;
            }
        }
        return best;
    }

    private boolean isShieldTarget(Player player) {
        return player != mc.player && player.isAlive() && !player.isSpectator()
                && TargetManager.isValidTarget(player) && isShielding(player);
    }

    /** Holding a raised shield (not eating) while facing us, so hits land on the shield. */
    private boolean isShielding(Player player) {
        if (!player.isUsingItem() || !player.getUseItem().is(Items.SHIELD))
            return false;
        Vec3 toUs = mc.player.getEyePosition().subtract(player.position()).normalize();
        return player.getLookAngle().dot(toUs) >= 0;
    }
}
