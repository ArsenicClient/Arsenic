package arsenic.event.impl;

import arsenic.event.types.Event;

public class EventRotate implements Event{

    private float yaw, pitch, speed;
    private boolean touched;

    public EventRotate(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        touched = true;
        this.yaw = yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setPitch(float pitch) {
        touched = true;
        this.pitch = pitch;
    }

    public boolean hasBeenTouched() {
        return touched;
    }
}
