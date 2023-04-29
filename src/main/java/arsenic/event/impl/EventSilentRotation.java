package arsenic.event.impl;

import arsenic.event.types.Event;

public class EventSilentRotation implements Event {

    private float initYaw, initPitch;
    private float yaw, pitch;
    private float speed;
    private boolean doMovementFix = true;

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

    public void setDoMovementFix(boolean doMovementFix) {
        this.doMovementFix = doMovementFix;
    }

}
