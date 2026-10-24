package arsenic.module.impl.visual;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventShader;
import arsenic.gui.design.M3;
import arsenic.gui.design.M3Icons;
import arsenic.gui.hud.HudEditorScreen;
import arsenic.gui.hud.HudElement;
import arsenic.gui.hud.HudStyle;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.render.DrawUtils;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.entity.player.EntityPlayer;

import java.util.ArrayList;
import java.util.List;

@ModuleInfo(name = "Radar", category = ModuleCategory.RENDER, hidden = true, tier = ModuleTier.VISUAL,
        description = "Relative player radar")
public class Radar extends Module {

    private static final float WIDTH = 124f, HEIGHT = 140f;
    private final HudElement hud = hudElement("Radar", 4, 4, (int) WIDTH, (int) HEIGHT);

    @EventLink
    public final Listener<EventRender2D> renderListener = event -> {
        if (!shouldRender() || mc.thePlayer == null || mc.theWorld == null)
            return;

        hud.setSize((int) WIDTH, (int) HEIGHT);
        HudStyle.pushScale(hud.x, hud.y, hud.scale);
        float x = hud.x, y = hud.y;
        float w = WIDTH, h = HEIGHT;
        float pad = 8f, headerH = 24f;

        HudStyle.card(x, y, x + w, y + h, 16f, M3.alpha(M3.surfaceContainerHigh(), 232));

        float headerMid = y + pad + headerH / 2f;
        M3Icons.drawCentered(M3Icons.RADAR, x + pad + 7f, headerMid, M3.primary(), 0.46f);
        FontRendererExtension<?> titleFont = HudStyle.title();
        titleFont.drawString("Radar", x + pad + 19f, headerMid, M3.onSurface(), titleFont.CENTREY);

        List<EntityPlayer> players = new ArrayList<>();
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == mc.thePlayer || player.isDead || player.isInvisible())
                continue;
            players.add(player);
        }
        FontRendererExtension<?> label = HudStyle.label();
        String count = String.valueOf(players.size());
        label.drawString(count, x + w - pad - label.getWidth(count), headerMid,
                M3.onSurfaceVariant(), label.CENTREY);
        HudStyle.divider(x + pad, y + pad + headerH, x + w - pad);

        float cx = x + w / 2f;
        float cy = y + pad + headerH + (h - pad - headerH - pad) / 2f;
        float outer = Math.min((w - pad * 2f) / 2f, (h - pad * 2f - headerH) / 2f) - 2f;
        int ring = M3.alpha(M3.outlineVariant(), 95);
        int cross = M3.alpha(M3.outlineVariant(), 65);

        DrawUtils.drawCircle(cx, cy, outer + 1.5f, M3.alpha(M3.surfaceContainerHighest(), 110));
        DrawUtils.drawCircle(cx, cy, outer, M3.alpha(M3.surfaceContainer(), 190));
        DrawUtils.drawCircleOutline(cx, cy, outer, 1f, ring);
        DrawUtils.drawCircleOutline(cx, cy, outer * 0.66f, 1f, M3.alpha(ring, 70));
        DrawUtils.drawCircleOutline(cx, cy, outer * 0.33f, 1f, M3.alpha(ring, 55));
        DrawUtils.drawRect(cx - outer, cy - 0.5f, cx + outer, cy + 0.5f, cross);
        DrawUtils.drawRect(cx - 0.5f, cy - outer, cx + 0.5f, cy + outer, cross);

        float yaw = (float) Math.toRadians(mc.thePlayer.rotationYaw);
        float forwardX = (float) -Math.sin(yaw), forwardZ = (float) Math.cos(yaw);
        float rightX = (float) -Math.cos(yaw), rightZ = (float) -Math.sin(yaw);
        float range = 36f;
        float scale = (outer - 4f) / range;

        for (EntityPlayer player : players) {
            double dx = player.posX - mc.thePlayer.posX;
            double dz = player.posZ - mc.thePlayer.posZ;

            float forward = (float) (dx * forwardX + dz * forwardZ);
            float right = (float) (dx * rightX + dz * rightZ);
            float distance = (float) Math.sqrt(forward * forward + right * right);
            boolean outside = distance > range;
            if (outside) {
                float factor = range / Math.max(0.001f, distance);
                forward *= factor;
                right *= factor;
            }

            float px = cx + right * scale;
            float py = cy - forward * scale;
            int color = Arsenic.getArsenic().getFriendManager().isFriend(player)
                    ? M3.secondary() : M3.primary();
            int dot = M3.alpha(color, outside ? 130 : 245);

            DrawUtils.drawCircle(px, py, 3.4f, M3.alpha(M3.scrim(), 120));
            DrawUtils.drawCircle(px, py, 2.3f, dot);
            if (!outside && distance < range * 0.34f)
                DrawUtils.drawCircleOutline(px, py, 4.2f, 1f, M3.alpha(color, 110));
        }

        DrawUtils.drawCircle(cx, cy, 2.6f, M3.onSurface());
        DrawUtils.drawCircle(cx, cy, 1.4f, M3.primary());
        DrawUtils.drawRect(cx - 0.6f, cy - 7f, cx + 0.6f, cy - 3f, M3.alpha(M3.primary(), 210));

        HudStyle.resetColor();
        HudStyle.popScale();
    };

    @EventLink
    public final Listener<EventShader.Blur> blurListener = event -> {
        if (!shouldRender() || mc.theWorld == null)
            return;
        HudStyle.pushScale(hud.x, hud.y, hud.scale);
        HudStyle.card(hud.x, hud.y, hud.x + WIDTH, hud.y + HEIGHT, 16f, 0xFFFFFFFF);
        HudStyle.resetColor();
        HudStyle.popScale();
    };

    private boolean shouldRender() {
        return mc.currentScreen == null
                || mc.currentScreen instanceof GuiChat
                || mc.currentScreen instanceof HudEditorScreen;
    }
}
