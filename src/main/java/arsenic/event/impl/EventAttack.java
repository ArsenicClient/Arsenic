package arsenic.event.impl;

import arsenic.event.types.Event;
import net.minecraft.world.entity.Entity;

public class EventAttack implements Event {

    private final Entity target;

    public EventAttack(Entity target){
        this.target = target;
    }

    public Entity getTarget(){
        return this.target;
    }
}
