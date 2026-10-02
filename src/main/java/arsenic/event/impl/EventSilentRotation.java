package arsenic.event.impl;

import arsenic.event.types.Event;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import arsenic.utils.rotations.SilentRotationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;


public class EventSilentRotation implements Event {

    private final float initYaw;
    private final float initPitch;
    private float yaw, pitch;
    private float speed;
    private SilentRotationManager.MovementFix movementFix = SilentRotationManager.MovementFix.SILENT;
    private boolean doJumpFix = true;
    private boolean preventDuplicateLook = false;
    private boolean blockUserInput = false;
    private boolean smoothing = true;
    private static Minecraft mc = Minecraft.getInstance();

    public EventSilentRotation(float yaw, float pitch,float speed) {
        this.initYaw = yaw;
        this.initPitch = pitch;
        this.yaw = yaw;
        this.pitch = pitch;
        this.speed = speed;
    }

    public boolean hasBeenModified() {
        return initYaw != yaw || initPitch != pitch;
    }

    public float getYaw() { return yaw; }

    public void setYaw(float yaw) { this.yaw = yaw; }

    public float getPitch() { return pitch; }

    public void setPitch(float pitch) { this.pitch = pitch; }

    public float getSpeed() {
        return speed;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public SilentRotationManager.MovementFix getMovementFix() {
        return movementFix;
    }

    public boolean doJumpFix(){
        return doJumpFix;
    }

    public void setJumpFix(boolean doJumpFix){
        this.doJumpFix = doJumpFix;
    }

    public void setMovementFix(SilentRotationManager.MovementFix movementFix) {
        this.movementFix = movementFix;
    }

    public boolean isPreventDuplicateLook() {
        return preventDuplicateLook;
    }

    public void setPreventDuplicateLook(boolean preventDuplicateLook) {
        this.preventDuplicateLook = preventDuplicateLook;
    }

    public boolean isSmoothing() {
        return smoothing;
    }

    /**
     * When {@code false}, the manager skips its ease-out/momentum curve and moves straight toward
     * the requested rotation, capped only by {@link #getSpeed()}. For modules that shape their own
     * per-tick motion and need it applied as-is. Defaults to {@code true} and resets every tick.
     */
    public void setSmoothing(boolean smoothing) {
        this.smoothing = smoothing;
    }

    public boolean isBlockUserInput() {
        return blockUserInput;
    }

    /**
     * When {@code true}, the player's own attack/use inputs (break block, place block, hit) are
     * swallowed for this tick — the keybinds behave as if they were never pressed, and queued
     * presses are drained so nothing fires once blocking stops. Client-side invocations of
     * {@code clickMouse()}/{@code rightClickMouse()} (Clicker, KillAura, Scaffold, ...) are
     * unaffected. Defaults to {@code false} and resets every tick, so a module must re-assert it.
     */
    public void setBlockUserInput(boolean blockUserInput) {
        this.blockUserInput = blockUserInput;
    }

    /**
     * Fired once per tick after the {@code SilentRotationManager} has settled on the
     * final rotations it will apply (post GCD-patch, speed limiting and duplicate-look
     * handling). Read-only: consumers observe the values the manager committed to, they
     * do not influence them — use the enclosing {@link EventSilentRotation} for that.
     */
    public static class Post implements Event {

        private final float yaw, pitch;
        private final float prevYaw, prevPitch;
        private final boolean modified;
        private final float speed;

        public Post(float yaw, float pitch, float prevYaw, float prevPitch, boolean modified, float speed) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.prevYaw = prevYaw;
            this.prevPitch = prevPitch;
            this.modified = modified;
            this.speed = speed;
        }

        public float getYaw() { return yaw; }

        public float getPitch() { return pitch; }

        public float getPrevYaw() { return prevYaw; }

        public float getPrevPitch() { return prevPitch; }

        public boolean isModified() { return modified; }

        public float getSpeed() { return speed; }

        /** Block the committed rotation is looking at, within vanilla reach. */
        public HitResult getRayTrace() {
            Vec3 eyes = mc.player.getEyePosition(1);
            Vec3 end = eyes.add(Entity.calculateViewVector(pitch, yaw).scale(4.5));
            return mc.level.clip(new ClipContext(eyes, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        }

        /** Whatever the committed rotation hits first, entity or block. */
        public HitResult getRayTraceEntity() {
            Vec3 eyes = mc.player.getEyePosition(1);
            Vec3 look = Entity.calculateViewVector(pitch, yaw);
            Vec3 end = eyes.add(look.scale(4.5));
            HitResult blockHit = getRayTrace();
            double blockDistance = blockHit.getType() != HitResult.Type.MISS ? eyes.distanceToSqr(blockHit.getLocation()) : 4.5 * 4.5;
            AABB area = mc.player.getBoundingBox().expandTowards(look.scale(4.5)).inflate(1, 1, 1);
            EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(mc.player, eyes, end, area,
                    e -> !e.isSpectator() && e.isPickable(), blockDistance);
            return entityHit != null ? entityHit : blockHit;
        }
    }
}
