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
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.timer.MSTimer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;

/**
 * Alerts when the server moves you back to a position far from where you are (a lagback or a rubber-band). The distance
 * is measured when the position packet arrives, before the game applies it. The first seconds after joining are ignored,
 * since the server always sends a position then. Chat, a HUD line and a short red flash.
 */
@ModuleInfo(name = "LagbackAlert", description = "Alerts when the server pulls you back to another position", category = ModuleCategory.RENDER)
public class LagbackAlert extends Module {

    public final DoubleProperty threshold = new DoubleProperty("Threshold (blocks)", new DoubleValue(0.5, 10, 1.5, 0.5));
    public final BooleanProperty chat = new BooleanProperty("Chat Alert", true);
    public final BooleanProperty hud = new BooleanProperty("HUD", true);

    private final HudElement panel = hudElement("LagbackAlert", 4, 180, 150, 16);
    private final MSTimer shown = MSTimer.expired();
    private volatile double pendingDistance = -1;
    private volatile double lastDistance = -1;

    @EventLink
    public final Listener<EventPacket.Incoming.Pre> onPacket = event -> {
        if (!(event.getPacket() instanceof S08PacketPlayerPosLook)) return;
        if (mc.thePlayer == null || mc.thePlayer.ticksExisted < 60) return;
        S08PacketPlayerPosLook p = (S08PacketPlayerPosLook) event.getPacket();
        double dx = p.getX() - mc.thePlayer.posX, dy = p.getY() - mc.thePlayer.posY, dz = p.getZ() - mc.thePlayer.posZ;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d >= threshold.getValue().getInput()) pendingDistance = d;
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        double d = pendingDistance;
        if (d < 0) return;
        pendingDistance = -1;
        lastDistance = d;
        shown.reset();
        if (chat.getValue()) PlayerUtils.addWaterMarkedMessageToChat(String.format("Lagback: pulled back %.1f blocks", d));
    };

    @EventLink
    public final Listener<EventRender2D> onRender = event -> {
        if (!hud.getValue() || lastDistance < 0 || shown.getTime() > 4000) return;
        String text = String.format("Lagback %.1f blocks", lastDistance);
        panel.setSize(Math.max(150, mc.fontRendererObj.getStringWidth(text) + 12), 16);
        DrawUtils.drawRoundedRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 5, 0xC0AA1010);
        mc.fontRendererObj.drawStringWithShadow(text, panel.x + 6, panel.y + 4, 0xFFFFFFFF);
    };
}
