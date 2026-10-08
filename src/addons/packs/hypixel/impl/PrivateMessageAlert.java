import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventTick;
import arsenic.gui.hud.HudElement;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.timer.MSTimer;
import net.minecraft.network.play.server.S02PacketChat;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Plays a sound and flashes the HUD when someone sends you a private message. Hypixel writes received messages as
 * "From Name: text", which is what is matched.
 */
@ModuleInfo(name = "PrivateMessageAlert", description = "Sound and HUD flash when someone sends you a private message", category = ModuleCategory.PLAYER)
public class PrivateMessageAlert extends Module {

    public final BooleanProperty sound = new BooleanProperty("Sound", true);
    public final BooleanProperty hud = new BooleanProperty("HUD", true);

    private final HudElement panel = hudElement("PrivateMessage", 4, 380, 130, 16);
    private final ConcurrentLinkedQueue<String> lines = new ConcurrentLinkedQueue<>();
    private final MSTimer flash = MSTimer.expired();

    @EventLink
    public final Listener<EventPacket.Incoming.Post> onPacket = event -> {
        if (event.getPacket() instanceof S02PacketChat) {
            lines.add(((S02PacketChat) event.getPacket()).getChatComponent().getUnformattedText());
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        String line;
        while ((line = lines.poll()) != null) {
            if (!line.trim().startsWith("From ")) continue;
            flash.reset();
            if (sound.getValue()) mc.thePlayer.playSound("random.orb", 1f, 1.2f);
        }
    };

    @EventLink
    public final Listener<EventRender2D> onRender = event -> {
        if (!hud.getValue() || flash.getTime() > 4000) return;
        String text = "Private message";
        panel.setSize(Math.max(130, mc.fontRendererObj.getStringWidth(text) + 12), 16);
        DrawUtils.drawRoundedRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 5, 0xC01E6BD0);
        mc.fontRendererObj.drawStringWithShadow(text, panel.x + 6, panel.y + 4, 0xFFFFFFFF);
    };
}
