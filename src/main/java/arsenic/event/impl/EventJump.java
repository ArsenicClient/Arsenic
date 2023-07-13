package arsenic.event.impl;

import arsenic.event.types.CancellableEvent;
import arsenic.event.types.Event;

public class EventJump extends CancellableEvent implements Event {
    private float yaw;
    private double motion;

    public EventJump(float yaw, double motion){
        this.yaw = yaw;
        this.motion = motion;
    }

    public float getYaw(){
        return this.yaw;
    }

    public void setYaw(float yaw){
        this.yaw = yaw;
    }

    public double getMotion(){
        return this.motion;
    }

    public void setMotion(double motion){
        this.motion = motion;
    }
}
