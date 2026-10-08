import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventRender2D;
import arsenic.gui.hud.HudElement;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.timer.MSTimer;
import net.minecraft.network.play.server.S03PacketTimeUpdate;

/**
 * Estimates server TPS from the world time the server sends (S03PacketTimeUpdate): game ticks advanced divided by the
 * real time between two updates, capped at 20. Shows red below 18. The estimate is only as steady as the server's
 * time updates.
 */
@ModuleInfo(name = "TpsMeter", description = "Shows the server TPS estimated from time update packets", category = ModuleCategory.RENDER)
public class TpsMeter extends Module {

    public final BooleanProperty hud = new BooleanProperty("HUD", true);

    private final HudElement panel = hudElement("TpsMeter", 4, 160, 90, 16);
    private final MSTimer sinceLast = new MSTimer();
    private volatile long lastWorldTime = -1;
    private volatile double tps = 20;

    @Override
    protected void onEnable() {
        lastWorldTime = -1;
        tps = 20;
    }

    @EventLink
    public final Listener<EventPacket.Incoming.Post> onPacket = event -> {
        if (!(event.getPacket() instanceof S03PacketTimeUpdate)) return;
        long now = ((S03PacketTimeUpdate) event.getPacket()).getTotalWorldTime();
        long elapsed = sinceLast.getTime();
        if (lastWorldTime >= 0 && elapsed > 0 && now > lastWorldTime) {
            double estimate = (now - lastWorldTime) * 1000.0 / elapsed;
            tps = Math.max(0, Math.min(20, estimate));
        }
        lastWorldTime = now;
        sinceLast.reset();
    };

    @EventLink
    public final Listener<EventRender2D> onRender = event -> {
        if (!hud.getValue() || lastWorldTime < 0) return;
        String text = String.format("TPS %.1f", tps);
        panel.setSize(Math.max(90, mc.fontRendererObj.getStringWidth(text) + 12), 16);
        DrawUtils.drawRoundedRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 5, 0x96121212);
        mc.fontRendererObj.drawStringWithShadow(text, panel.x + 6, panel.y + 4, tps < 18 ? 0xFFFF5555 : 0xFFFFFFFF);
    };
}
