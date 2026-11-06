package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventLiving;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.timer.MSTimer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Holds back a click on a player who cannot take the hit yet (still in hurt time, or in the air) and lands it the
 * moment they can, so the swing is not wasted and your attack cooldown is not reset for nothing.
 */
@ModuleInfo(name = "HitFix", description = "Delays wasted hits until the target can take damage", category = ModuleCategory.COMBAT, tier = ModuleTier.LEGIT)
public class HitFix extends Module {

    public final BooleanProperty weaponOnly = new BooleanProperty("Weapon Only", true);
    public final BooleanProperty onlyOnGround = new BooleanProperty("Only On Ground", true);
    public final BooleanProperty damageTick = new BooleanProperty("Damage Tick", true);

    /** A held hit is dropped if the target has not become hittable within this long. */
    private static final long HOLD_TIMEOUT_MS = 1000L;
    /** Height above the ground at which the target counts as airborne. */
    private static final double AIRBORNE_HEIGHT = 0.3;

    private final MSTimer holdTimer = new MSTimer();
    private boolean pending;
    private Player heldTarget;

    @Override
    protected void onEnable() {
        reset();
    }

    @Override
    protected void onDisable() {
        reset();
    }

    private void reset() {
        pending = false;
        heldTarget = null;
    }

    /**
     * Called from the left click (Minecraft.startAttack) before vanilla attacks. Returns true when the click should be
     * held: the whole click - attack, swing and cooldown reset - is then skipped and replayed later from the tick hook.
     */
    public boolean shouldHoldClick() {
        if (!isEnabled() || mc.player == null || mc.level == null || mc.gui.screen() != null || !mc.isWindowActive())
            return false;
        if (!(mc.hitResult instanceof EntityHitResult hit) || !(hit.getEntity() instanceof Player target))
            return false;
        if (Arsenic.getArsenic().getFriendManager().isFriend(target))
            return false;
        if (!pending && !shouldDelay(target))
            return false;
        pending = true;
        heldTarget = target;
        holdTimer.reset();
        return true;
    }

    /** Releases the held hit before the movement packet, once the target can take it. */
    @RequiresPlayer
    @EventLink
    public final Listener<EventLiving> onLiving = event -> {
        if (!pending)
            return;
        if (mc.player.isUsingItem() || mc.gui.screen() != null) {
            reset();
            return;
        }
        Player target = heldTarget;
        // the hit stays a legit one: the crosshair must still be on the same, living target
        if (target == null || !target.isAlive() || crosshairEntity() != target) {
            reset();
            return;
        }
        if (shouldDelay(target)) {
            if (holdTimer.hasTimeElapsed(HOLD_TIMEOUT_MS))
                reset();
            return;
        }
        reset();
        // attack before swing, as a 1.9+ client does
        mc.gameMode.attack(mc.player, target);
        PlayerUtils.swingItem();
    };

    private boolean shouldDelay(Player target) {
        if (weaponOnly.getValue() && !PlayerUtils.isPlayerHoldingWeapon())
            return false;
        if (damageTick.getValue() && target.hurtTime != 0)
            return true;
        return onlyOnGround.getValue() && heightAboveGround(target) > AIRBORNE_HEIGHT;
    }

    private static Entity crosshairEntity() {
        return mc.hitResult instanceof EntityHitResult hit ? hit.getEntity() : null;
    }

    private static double heightAboveGround(Player target) {
        Vec3 from = target.position();
        Vec3 to = from.add(0.0, -256.0, 0.0);
        BlockHitResult result = mc.level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, target));
        return result.getType() == HitResult.Type.BLOCK ? target.getY() - result.getLocation().y : 256.0;
    }
}
