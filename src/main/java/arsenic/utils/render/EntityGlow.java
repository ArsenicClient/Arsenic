package arsenic.utils.render;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.world.entity.Entity;

/**
 * Coloured outlines through walls for any entity, drawn with vanilla's glowing-entity outline. A module (or addon)
 * calls {@link #glow} for each entity every frame it wants outlined, typically from an EventRenderWorldLast listener;
 * an entity that is not asked for again stops glowing a moment later, so nothing has to be cleaned up.
 */
public final class EntityGlow {

    /** How long a request lasts, so one missed frame does not make the outline flicker. */
    private static final long LIFETIME_MS = 150L;

    private record Request(int colour, long until) {}

    private static final Map<Integer, Request> requests = new ConcurrentHashMap<>();

    private EntityGlow() {
    }

    /** Outlines the entity in {@code rgb} (the alpha is ignored) until shortly after the last call. */
    public static void glow(Entity entity, int rgb) {
        if (entity != null)
            requests.put(entity.getId(), new Request(rgb & 0xFFFFFF, System.currentTimeMillis() + LIFETIME_MS));
    }

    /** The outline colour asked for this entity, or null when it should not glow. Read by the outline mixins. */
    public static Integer colourOf(Entity entity) {
        Request request = requests.get(entity.getId());
        if (request == null)
            return null;
        if (request.until < System.currentTimeMillis()) {
            requests.remove(entity.getId(), request);
            return null;
        }
        return request.colour;
    }
}
