package arsenic.gui;

import arsenic.event.bus.EventErrors;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.gui.click.ClickGuiScreen;
import arsenic.main.Arsenic;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.interfaces.ISerializable;
import arsenic.utils.render.RenderUtils;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.Gui;

import java.awt.Color;
import java.util.List;
import java.util.Optional;

/**
 * Draws whatever is currently throwing on the event bus in the top left corner, so a broken module
 * announces itself instead of quietly spamming the console.
 *
 * <p>Each line names the module, the event it died on, and where it threw. Errors fade out a few
 * seconds after they stop happening, so this is invisible unless something is actually wrong.
 */
public class ErrorOverlay implements ISerializable {

    /**
     * Off by default - errors are always collected and readable through {@code .errors}, this only
     * controls whether they are painted over the game. Toggle with {@code .errors hud enable}.
     */
    private static boolean enabled;

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    /** Sits below the default watermark position so the two do not overlap. */
    private static final int X = 4;
    private static final int Y = 18;

    private static final int LINE_HEIGHT = 11;
    private static final int PADDING = 3;

    @EventLink
    public final Listener<EventRender2D> onRender2D = event -> {
        if (!enabled)
            return;

        List<EventErrors.Entry> errors = EventErrors.getActive();
        if (errors.isEmpty())
            return;

        // an overlay that reports errors must never be the thing that throws, so every hop to the
        // font renderer is treated as optional
        FontRendererExtension<?> fr = Optional.ofNullable(Arsenic.getArsenic())
                .map(Arsenic::getClickGuiScreen)
                .map(ClickGuiScreen::getFontRenderer)
                .orElse(null);
        if (fr == null)
            return;

        float y = Y;
        for (EventErrors.Entry error : errors) {
            float fade = error.getFade();
            if (fade <= 0f)
                continue;

            String title = "§c" + error.getOwner() + " §7errored"
                    + (error.getCount() > 1 ? " §8x" + error.getCount() : "");
            String detail = "§7" + error.getEvent() + " §8- §f" + error.getMessage();
            String site = "§8at " + error.getSite();

            int width = (int) Math.max(fr.getWidth(stripColour(title)),
                    Math.max(fr.getWidth(stripColour(detail)), fr.getWidth(stripColour(site))));

            int backdrop = (int) (0x99 * fade) << 24;
            Gui.drawRect(X - PADDING, (int) y - PADDING, X + width + PADDING,
                    (int) y + LINE_HEIGHT * 3 - 1, backdrop);

            int colour = RenderUtils.alpha(Color.WHITE, (int) (255 * fade));
            fr.drawStringWithShadow(title, X, y, colour);
            fr.drawStringWithShadow(detail, X, y + LINE_HEIGHT, colour);
            fr.drawStringWithShadow(site, X, y + LINE_HEIGHT * 2, colour);

            y += LINE_HEIGHT * 3 + PADDING * 2;
        }
    };

    /** Width measuring should not count the colour codes. */
    private static String stripColour(String text) {
        return text.replaceAll("§.", "");
    }

    // -----------------------------------------------------------------
    //  persisted with the client config so the toggle survives a restart
    // -----------------------------------------------------------------

    @Override
    public void loadFromJson(JsonObject obj) {
        if (obj != null && obj.has("enabled"))
            enabled = obj.get("enabled").getAsBoolean();
    }

    @Override
    public JsonObject saveInfoToJson(JsonObject obj) {
        obj.addProperty("enabled", enabled);
        return obj;
    }

    @Override
    public String getJsonKey() {
        return "errorHud";
    }
}
