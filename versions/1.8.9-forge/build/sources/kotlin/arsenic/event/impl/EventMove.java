package arsenic.event.impl;

import arsenic.event.types.Event;

public class EventMove implements Event {

    private float strafe, forward, friction, yaw, strictYaw;

    public EventMove(float strafe, float forward, float friction, float yaw) {
        this.strafe = strafe;
        this.forward = forward;
        this.friction = friction;
        this.yaw = yaw;
    }

    public float getStrafe() { return strafe; }

    public void setStrafe(float strafe) { this.strafe = strafe; }

    public float getForward() { return forward; }

    public void setForward(float forward) { this.forward = forward; }

    public float getFriction() { return friction; }

    public void setFriction(float friction) { this.friction = friction; }

    public float getYaw() { return yaw; }

    public void setYaw(float yaw) { this.yaw = yaw; }

}
