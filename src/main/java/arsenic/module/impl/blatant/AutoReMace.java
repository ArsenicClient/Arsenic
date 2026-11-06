package arsenic.module.impl.blatant;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventAttack;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.impl.ghost.AutoElytra;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.rotations.SilentRotationManager;
import arsenic.utils.timer.MSTimer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * After you hit a player with the mace, takes off again with AutoElytra, climbs toward a point high above them and
 * then turns to dive back down onto them for the next smash.
 */
@ModuleInfo(name = "AutoReMace", description = "Flies back up with an elytra after a mace hit and dives on the target again", category = ModuleCategory.COMBAT, tier = ModuleTier.BLATANT)
public class AutoReMace extends Module {

    /** Before this the hit is still settling: nothing is done yet. */
    private static final long WAIT_MS = 100;
    /** Until this the climb runs: AutoElytra takes off and the aim goes above the target. */
    private static final long CLIMB_MS = 600;
    /** Until this the aim goes to the target's eyes for the dive. */
    private static final long DIVE_MS = 1400;
    /** Landing after this ends the sequence. */
    private static final long LAND_MS = 1000;
    /** The sequence never runs longer than this without a landing. */
    private static final long MAX_MS = 5000;
    private static final double MAX_DISTANCE = 100;
    /** Degrees per tick at a smoothing of 0, before the random spread. */
    private static final float BASE_SPEED = 40f;

    public final DoubleProperty smoothing = new DoubleProperty("Rotation Smoothing", new DoubleValue(0, 2, 0.5, 0.05));
    public final DoubleProperty aboveThem = new DoubleProperty("Above Them", new DoubleValue(0, 100, 30, 1));

    private final MSTimer timer = new MSTimer();
    private Player target;
    private boolean active;
    /** Where the aim goes this tick, or null for no aim. */
    private Vec3 aimPoint;
    private boolean aiming;

    @Override
    public String getHudInfo() {
        return active && target != null ? target.getName().getString() : null;
    }

    @Override
    protected void onDisable() {
        stop();
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventAttack> onAttack = event -> {
        if (event.getTarget() instanceof Player player && mc.player.getMainHandItem().is(Items.MACE)) {
            target = player;
            active = true;
            timer.reset();
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        aimPoint = null;
        if (!active)
            return;
        if (target == null || !target.isAlive() || target.isRemoved() || mc.player.distanceTo(target) > MAX_DISTANCE) {
            stop();
            return;
        }
        long elapsed = timer.getTime();
        if (elapsed > LAND_MS && (mc.player.onGround() || elapsed > MAX_MS)) {
            stop();
            return;
        }
        if (elapsed <= WAIT_MS)
            return;
        if (elapsed <= CLIMB_MS) {
            AutoElytra elytra = Arsenic.getArsenic().getModuleManager().getModuleByClass(AutoElytra.class);
            if (elytra != null && !elytra.isEnabled())
                elytra.setEnabled(true);
            aimPoint = target.position().add(0, aboveThem.getValue().getInput(), 0);
            return;
        }
        if (elapsed <= DIVE_MS) {
            aimPoint = target.getEyePosition();
            return;
        }
        if (heightAboveGround() < 1.0)
            stop();
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        aiming = false;
        if (aimPoint == null || mc.gui.screen() != null)
            return;
        float[] rots = RotationUtils.rotationsTo(mc.player.getEyePosition(), aimPoint);
        float scale = 1.1f - (float) smoothing.getValue().getInput() / 2f;
        float speed = BASE_SPEED * scale * (0.5f + (float) Math.random() * 0.5f);
        event.setYaw(rots[0]);
        event.setPitch(Mth.clamp(rots[1], -90f, 90f));
        event.setSpeed(Math.max(1f, speed));
        event.setMovementFix(SilentRotationManager.MovementFix.SILENT);
        aiming = true;
    };

    /** The glide follows where the player looks, so the settled rotation is shown on the player too. */
    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onRotationPost = event -> {
        if (!aiming)
            return;
        mc.player.setYRot(event.getYaw());
        mc.player.setXRot(event.getPitch());
    };

    private void stop() {
        active = false;
        target = null;
        aimPoint = null;
        aiming = false;
    }

    /** Blocks between the player's feet and the ground below, or 256 when there is none in reach. */
    private double heightAboveGround() {
        Vec3 from = mc.player.position();
        BlockHitResult hit = mc.level.clip(new ClipContext(from, from.add(0, -256, 0),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == HitResult.Type.BLOCK ? mc.player.getY() - hit.getLocation().y : 256.0;
    }
}
