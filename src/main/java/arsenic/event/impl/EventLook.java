package arsenic.event.impl;

import arsenic.event.types.Event;

public class EventLook implements Event {

    private float pitch, yaw;

    public EventLook(float pitch, float yaw) {
        this.pitch = pitch;
        this.yaw = yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }
}
