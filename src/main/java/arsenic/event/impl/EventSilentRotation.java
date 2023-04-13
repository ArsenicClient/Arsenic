package arsenic.event.impl;

import arsenic.event.types.Event;

public class EventSilentRotation implements Event {

    private float initYaw, initPitch;
    private float yaw, pitch;

    public EventSilentRotation(float yaw, float pitch) {
        this.initYaw = yaw;
        this.initPitch = pitch;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public boolean hasBeenModified() {
        return initYaw != yaw || initPitch != pitch;
    }

    public float getYaw() { return yaw; }

    public void setYaw(float yaw) { this.yaw = yaw; }

    public float getPitch() { return pitch; }

    public void setPitch(float pitch) { this.pitch = pitch; }
}
