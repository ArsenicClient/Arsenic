package arsenic.event.impl;

import arsenic.event.types.Event;

/**
 * Fired once per frame while the level is being extracted for rendering, with a gizmo collector
 * open. Listeners draw world-space shapes through {@link arsenic.utils.render.RenderUtils}.
 */
public class EventRenderWorldLast implements Event {

    public final float partialTicks;

    public EventRenderWorldLast(float partialTicks) {
        this.partialTicks = partialTicks;
    }
}
