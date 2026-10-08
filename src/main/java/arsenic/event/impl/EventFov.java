package arsenic.event.impl;

import arsenic.event.types.Event;

/**
 * Posted when the game asks for the camera's field of view (in degrees, 70 by default). Set a new value with setFov;
 * only the camera uses it, not the held items. Addons can change the FOV with this (CustomFov, Zoom).
 */
public class EventFov implements Event {
    private float fov;
    private boolean modified;

    public EventFov(float fov) {
        this.fov = fov;
    }

    public float getFov() {
        return fov;
    }

    public void setFov(float fov) {
        this.fov = fov;
        this.modified = true;
    }

    public boolean isModified() {
        return modified;
    }
}
