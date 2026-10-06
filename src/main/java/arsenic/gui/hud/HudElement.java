package arsenic.gui.hud;

import com.google.gson.JsonObject;

/**
 * A draggable spot on the HUD. A module creates one with {@code Module#hudElement(...)}, draws itself at
 * ({@link #x}, {@link #y}) and keeps {@link #width}/{@link #height} up to date so the HUD editor can show its box.
 * Positions are saved with the owning module's config.
 */
public final class HudElement {

    public final String label;
    /** When true, {@link #x} is an offset from the right edge of the screen (0 = flush right). */
    public final boolean rightAnchored;
    public int x, y;
    public int width, height;

    private final int defaultX, defaultY;

    public HudElement(String label, int x, int y, int width, int height, boolean rightAnchored) {
        this.label = label;
        this.x = defaultX = x;
        this.y = defaultY = y;
        this.width = width;
        this.height = height;
        this.rightAnchored = rightAnchored;
    }

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void reset() {
        x = defaultX;
        y = defaultY;
    }

    public void save(JsonObject hud) {
        JsonObject pos = new JsonObject();
        pos.addProperty("x", x);
        pos.addProperty("y", y);
        hud.add(label, pos);
    }

    public void load(JsonObject hud) {
        JsonObject pos = hud.has(label) && hud.get(label).isJsonObject() ? hud.getAsJsonObject(label) : null;
        if (pos == null)
            return;
        if (pos.has("x")) x = pos.get("x").getAsInt();
        if (pos.has("y")) y = pos.get("y").getAsInt();
    }
}
