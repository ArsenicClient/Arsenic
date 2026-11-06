package arsenic.module.impl.blatant;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventSilentRotation;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.rotations.SilentRotationManager;
import arsenic.utils.timer.MSTimer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Hits wind charges thrown by other players before they burst on you, knocking them away. The aim is silent and the
 * hit only goes out once the rotation being sent points at the charge.
 */
@ModuleInfo(name = "WindChargeAura", description = "Automatically hits wind charges around you", category = ModuleCategory.COMBAT, tier = ModuleTier.BLATANT)
public class WindChargeAura extends Module {

    /** Charges further than this from the player are not considered at all. */
    private static final double SEARCH_RANGE = 5.0;
    /** Minimum time between two hits. */
    private static final long HIT_DELAY_MS = 100;
    private static final float AIM_TOLERANCE = 8f;

    public final DoubleProperty reach = new DoubleProperty("Reach", new DoubleValue(1, 3, 2.95, 0.05));
    public final BooleanProperty humanizedAim = new BooleanProperty("Humanized Aim", true);
    public final DoubleProperty maxYaw = new DoubleProperty("Max Yaw", new DoubleValue(0, 180, 180, 1));
    public final BooleanProperty inAirOnly = new BooleanProperty("In Air Only", true);

    private final MSTimer hitTimer = new MSTimer();
    private Entity target;
    private float aimYaw, aimPitch;

    @Override
    protected void onDisable() {
        target = null;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        target = null;
        if (mc.gameMode == null || mc.gui.screen() != null || mc.player.isUsingItem())
            return;
        if (inAirOnly.getValue() && mc.player.onGround())
            return;
        if (!hitTimer.hasTimeElapsed(HIT_DELAY_MS))
            return;
        Entity charge = nearestCharge();
        if (charge == null || !mc.player.hasLineOfSight(charge) || eyeDistance(charge) > reach.getValue().getInput())
            return;

        Vec3 eyes = mc.player.getEyePosition(1f);
        Vec3 center = charge.getBoundingBox().getCenter();
        float[] rots = RotationUtils.rotationsTo(eyes, center);
        if (Math.abs(RotationUtils.getYawDifference(mc.player.getYRot(), rots[0])) > maxYaw.getValue().getInput())
            return;
        if (humanizedAim.getValue()) {
            // Small jitter that shrinks with distance, so every hit does not send the exact eye-to-centre angle.
            double spread = Math.min(1.5, 1.0 / Math.max(0.5, eyes.distanceTo(center)));
            ThreadLocalRandom r = ThreadLocalRandom.current();
            rots[0] += (float) ((r.nextDouble() - 0.5) * 2 * spread);
            rots[1] = Mth.clamp(rots[1] + (float) ((r.nextDouble() - 0.5) * 2 * spread), -89.9f, 89.9f);
        }
        target = charge;
        aimYaw = rots[0];
        aimPitch = rots[1];
        event.setYaw(aimYaw);
        event.setPitch(aimPitch);
        event.setSpeed(180f);
        event.setPreventDuplicateLook(true);
        event.setMovementFix(SilentRotationManager.MovementFix.SILENT);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onPost = event -> {
        Entity charge = target;
        target = null;
        if (charge == null || !charge.isAlive() || mc.gameMode == null)
            return;
        if (Math.abs(RotationUtils.getYawDifference(event.getYaw(), aimYaw)) > AIM_TOLERANCE
                || Math.abs(event.getPitch() - aimPitch) > AIM_TOLERANCE)
            return;
        if (eyeDistance(charge) > reach.getValue().getInput())
            return;
        // Attack before swing, like a 1.9+ client.
        mc.gameMode.attack(mc.player, charge);
        PlayerUtils.swingItem();
        hitTimer.reset();
    };

    /** The closest live wind charge within the search range that someone else threw. */
    private Entity nearestCharge() {
        Entity best = null;
        double bestSq = SEARCH_RANGE * SEARCH_RANGE;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof WindCharge charge) || !charge.isAlive() || charge.getOwner() == mc.player)
                continue;
            Vec3 c = charge.getBoundingBox().getCenter();
            double sq = mc.player.distanceToSqr(c.x, c.y, c.z);
            if (sq <= bestSq) {
                best = charge;
                bestSq = sq;
            }
        }
        return best;
    }

    /** Distance from the eyes to the closest point of the entity's hitbox. */
    private double eyeDistance(Entity e) {
        Vec3 eyes = mc.player.getEyePosition(1f);
        AABB box = e.getBoundingBox();
        double dx = eyes.x - Mth.clamp(eyes.x, box.minX, box.maxX);
        double dy = eyes.y - Mth.clamp(eyes.y, box.minY, box.maxY);
        double dz = eyes.z - Mth.clamp(eyes.z, box.minZ, box.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
