package arsenic.event.impl;

import arsenic.event.types.Event;

public class EventRenderThirdPerson implements Event {
    private float yaw, pitch;
    private boolean touched;

    public EventRenderThirdPerson(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public float getPitch() {
        return pitch;
    }

    public float getYaw() {
        return yaw;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
        touched = true;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
        touched = true;
    }

    public boolean hasBeenTouched() {
        return touched;
    }

}
