package arsenic.module.impl.visual;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventShader;
import arsenic.gui.hud.HudElement;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.utils.render.DrawUtils;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@ModuleInfo(name = "Radar", category = ModuleCategory.RENDER, hidden = true)
public class Radar extends Module {


    private final HudElement hud = hudElement("Radar", 4, 4, 124, 124);

    @EventLink
    public final Listener<EventRender2D> renderListener = event -> {
        if (mc.currentScreen != null) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        int s = (int) 80;
        int cx = hud.x + s / 2;
        int cy = hud.y + s / 2;
        double scale = (s / 2.0) / 30;
        int theme = Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor();

        int bgColor = new Color(0, 0, 0, 80).getRGB();
        int borderColor = new Color(255, 255, 255, 30).getRGB();

        DrawUtils.drawRoundedRect(hud.x, hud.y, hud.x + s, hud.y + s, 4f, bgColor);
        DrawUtils.drawRoundedOutline(hud.x, hud.y, hud.x + s, hud.y + s, 4f, 1f, borderColor);

        int lineColor = new Color(255, 255, 255, 40).getRGB();
        DrawUtils.drawRect(cx, hud.y + 2, cx + 1, hud.y + s - 2, lineColor);
        DrawUtils.drawRect(hud.x + 2, cy, hud.x + s - 2, cy + 1, lineColor);

        float yaw = mc.thePlayer.rotationYaw;
        double yawRad = Math.toRadians(yaw);

        List<EntityPlayer> players = new CopyOnWriteArrayList<>(mc.theWorld.playerEntities);
        for (EntityPlayer player : players) {
            if (player.isInvisible() && !false) continue;
            if (player == mc.thePlayer && !true) continue;

            double dx = player.posX - mc.thePlayer.posX;
            double dz = player.posZ - mc.thePlayer.posZ;

            double right = 0, forward = 0;

            float px = (float) (cx + right * scale);
            float py = (float) (cy - forward * scale);

            if (px < hud.x + 2 || px > hud.x + s - 2 || py < hud.y + 2 || py > hud.y + s - 2)
                continue;

            if (player == mc.thePlayer) {
            } else {
                DrawUtils.drawCircle(px, py, 2, new Color(theme).getRGB());
            }
        }
    };

    @EventLink
    public final Listener<EventShader.Blur> blurListener = event -> {
        if (mc.currentScreen != null) return;
        if (mc.theWorld == null) return;

        int s = (int) 80;
        Gui.drawRect(hud.x, hud.y, hud.x + s, hud.y + s, -1);
    };
}
