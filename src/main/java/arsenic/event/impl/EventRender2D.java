package arsenic.event.impl;

import arsenic.event.types.Event;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Fired once per frame while the HUD is drawn. A {@link arsenic.utils.render.RenderContext} is open
 * for the duration, so listeners can use the static draw helpers directly.
 */
public class EventRender2D implements Event {

    private final GuiGraphicsExtractor graphics;
    private final float partialTicks;

    public EventRender2D(GuiGraphicsExtractor graphics, float partialTicks) {
        this.graphics = graphics;
        this.partialTicks = partialTicks;
    }

    public GuiGraphicsExtractor getGraphics() { return graphics; }

    public float getPartialTicks() { return partialTicks; }

    /** Scaled screen width, what 1.8 called {@code ScaledResolution#getScaledWidth()}. */
    public int getWidth() { return graphics.guiWidth(); }

    public int getHeight() { return graphics.guiHeight(); }
}
