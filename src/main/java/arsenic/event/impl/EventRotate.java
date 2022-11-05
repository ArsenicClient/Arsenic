package arsenic.event.impl;

import arsenic.event.types.Event;

public class EventRotate implements Event{

    private float initYaw, initPitch;
    private float yaw, pitch, speed;
    private boolean onMove, onLook;

    public EventRotate(float yaw, float pitch) {
        this.initYaw = yaw;
        this.initPitch = pitch;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public boolean hasBeenModified() {
        return initYaw != yaw || initPitch != pitch;
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public float getSpeed() {
        return speed;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public boolean isOnMove() {
        return onMove;
    }

    public void setOnMove(boolean onMove) {
        this.onMove = onMove;
    }

    public boolean isOnLook() {
        return onLook;
    }

    public void setOnLook(boolean onLook) {
        this.onLook = onLook;
    }
}
