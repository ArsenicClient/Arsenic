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

public class ErrorOverlay implements ISerializable {

    private static boolean enabled;

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

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
                    (int) y + LINE_HEIGHT * 3 + PADDING, backdrop);

            int colour = RenderUtils.alpha(Color.WHITE, (int) (255 * fade));
            fr.drawStringWithShadow(title, X, y + LINE_HEIGHT * 0.5f, colour, fr.CENTREY);
            fr.drawStringWithShadow(detail, X, y + LINE_HEIGHT * 1.5f, colour, fr.CENTREY);
            fr.drawStringWithShadow(site, X, y + LINE_HEIGHT * 2.5f, colour, fr.CENTREY);

            y += LINE_HEIGHT * 3 + PADDING * 2 + 1;
        }
    };

    private static String stripColour(String text) {
        return text.replaceAll("§.", "");
    }


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
