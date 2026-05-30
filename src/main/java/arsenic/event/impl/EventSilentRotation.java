package arsenic.event.impl;

import arsenic.event.types.Event;

public class EventSilentRotation implements Event {

    private final float initYaw;
    private final float initPitch;
    private float yaw, pitch;
    private float speed;
    private boolean doMovementFix = true;
    private boolean doJumpFix = true;
    private boolean preventDuplicateLook = false;

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

    public boolean doMovementFix() {
        return doMovementFix;
    }

    public boolean doJumpFix(){
        return doJumpFix;
    }

    public void setJumpFix(boolean doJumpFix){
        this.doJumpFix = doJumpFix;
    }

    public void setDoMovementFix(boolean doMovementFix) {
        this.doMovementFix = doMovementFix;
    }

    public boolean isPreventDuplicateLook() {
        return preventDuplicateLook;
    }

    public void setPreventDuplicateLook(boolean preventDuplicateLook) {
        this.preventDuplicateLook = preventDuplicateLook;
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

        /** Whether a silent rotation is currently active (the manager is overriding the player's look). */
        public boolean isModified() { return modified; }

        public float getSpeed() { return speed; }
    }
}
