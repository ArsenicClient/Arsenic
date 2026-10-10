import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventKey;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.TextProperty;
import arsenic.utils.timer.MSTimer;
import org.lwjgl.input.Keyboard;

import java.util.HashMap;
import java.util.Map;

/**
 * Sends a chat line when a key is pressed. Macros are written as "KEY=message" pairs separated by semicolons, for example
 * "F6=/play bedwars_eight_one;F7=gg". KEY is an LWJGL key name (F6, Z, NUMPAD1, ...). Only fires on a deliberate key
 * press with no GUI open, and at most one macro per second.
 */
@ModuleInfo(name = "ChatMacros", description = "Sends a chosen chat line when a key is pressed", category = ModuleCategory.PLAYER)
public class ChatMacros extends Module {

    public final TextProperty macros = new TextProperty("Macros", "F6=/play bedwars_eight_one;F7=gg", 400);

    private final MSTimer cooldown = MSTimer.expired();
    private final Map<Integer, String> bound = new HashMap<>();
    private String parsed;

    @RequiresPlayer
    @EventLink
    public final Listener<EventKey> onKey = event -> {
        if (!macros.getValue().equals(parsed)) reparse();
        String message = bound.get(event.getKeycode());
        if (message == null || !cooldown.hasTimeElapsed(1000, true)) return;
        mc.thePlayer.sendChatMessage(message);
    };

    private void reparse() {
        parsed = macros.getValue();
        bound.clear();
        for (String pair : parsed.split(";")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            int code = Keyboard.getKeyIndex(pair.substring(0, eq).trim().toUpperCase());
            String message = pair.substring(eq + 1).trim();
            if (code != Keyboard.KEY_NONE && !message.isEmpty()) bound.put(code, message);
        }
    }
}
