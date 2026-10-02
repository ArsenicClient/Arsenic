package arsenic.event.impl;

import arsenic.event.types.Event;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class EventPlayerJoinWorld implements Event {

    private final Player entity;
    private final Level world;

    public EventPlayerJoinWorld(Player entity, Level world) {
        this.entity = entity;
        this.world = world;
    }

    public Player getEntity() {
        return entity;
    }

    public Level getWorld() {
        return world;
    }
}
