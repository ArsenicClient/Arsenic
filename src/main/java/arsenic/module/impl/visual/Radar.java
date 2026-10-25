package arsenic.module.impl.visual;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventShader;
import arsenic.gui.design.M3;
import arsenic.gui.hud.HudEditorScreen;
import arsenic.gui.hud.HudElement;
import arsenic.gui.hud.HudStyle;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.render.DrawUtils;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.entity.player.EntityPlayer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@ModuleInfo(name = "Radar", category = ModuleCategory.RENDER, hidden = true, tier = ModuleTier.VISUAL)
public class Radar extends Module {

    private static final int SIZE = 90;
    private final HudElement hud = hudElement("Radar", 6, 234, SIZE, SIZE);

    private boolean shouldRender() {
        return mc.currentScreen == null
                || mc.currentScreen instanceof GuiChat
                || mc.currentScreen instanceof HudEditorScreen;
    }

    @EventLink
    public final Listener<EventRender2D> renderListener = event -> {
        if (!shouldRender()) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        hud.setSize(SIZE, SIZE);
        float x = HudStyle.snap(hud.x);
        float y = HudStyle.snap(hud.y);
        float s = SIZE;
        float cx = x + s / 2f;
        float cy = y + s / 2f;
        double scale = (s / 2.0 - 8.0) / 36.0;

        HudStyle.card(x, y, x + s, y + s, HudStyle.RADIUS_CARD, 195);

        // Concentric range rings
        int ringColor = M3.alpha(M3.outlineVariant(), 60);
        DrawUtils.drawCircleOutline(cx, cy, s * 0.22f, 1f, ringColor);
        DrawUtils.drawCircleOutline(cx, cy, s * 0.38f, 1f, ringColor);

        // Crisp 1px crosshair lines
        float onePx = 1f / Math.max(1f, HudStyle.scaleFactor());
        int axisColor = M3.alpha(M3.outlineVariant(), 80);
        float inset = 6f;
        DrawUtils.drawRect(HudStyle.snap(cx - onePx * 0.5f), y + inset,
                HudStyle.snap(cx + onePx * 0.5f), y + s - inset, axisColor);
        DrawUtils.drawRect(x + inset, HudStyle.snap(cy - onePx * 0.5f),
                x + s - inset, HudStyle.snap(cy + onePx * 0.5f), axisColor);

        float yaw = mc.thePlayer.rotationYaw;
        double yawRad = Math.toRadians(yaw);
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);

        float pad = 6f;
        List<EntityPlayer> players = new CopyOnWriteArrayList<>(mc.theWorld.playerEntities);
        for (EntityPlayer player : players) {
            if (player == mc.thePlayer || player.isInvisible()) continue;

            double dx = player.posX - mc.thePlayer.posX;
            double dz = player.posZ - mc.thePlayer.posZ;

            double right = -dx * cos - dz * sin;
            double forward = -dx * sin + dz * cos;

            float px = (float) (cx + right * scale);
            float py = (float) (cy - forward * scale);

            px = Math.max(x + pad, Math.min(x + s - pad, px));
            py = Math.max(y + pad, Math.min(y + s - pad, py));

            DrawUtils.drawCircle(px, py, 3.2f, M3.alpha(M3.primaryContainer(), 170));
            DrawUtils.drawCircle(px, py, 2.0f, M3.primary());
        }

        // True world North (-Z) indicator orbiting along the outer ring as the player turns
        float orbitR = s * 0.38f;
        float nx = (float) (cx + sin * orbitR);
        float ny = (float) (cy + cos * orbitR);
        DrawUtils.drawRoundedRect(nx - 5.2f, ny - 5.2f, nx + 5.2f, ny + 5.2f, 3.2f,
                M3.alpha(M3.surfaceContainerHighest(), 225));
        FontRendererExtension<?> labelFr = HudStyle.label();
        labelFr.setScale(0.75f);
        labelFr.drawString("N", nx, ny, M3.primary(), labelFr.CENTREX, labelFr.CENTREY);
        labelFr.resetScale();

        // Self marker centered at (cx, cy)
        DrawUtils.drawCircle(cx, cy, 3.0f, M3.alpha(M3.surfaceContainerHighest(), 220));
        DrawUtils.drawCircle(cx, cy, 2.0f, M3.onSurface());
        HudStyle.resetGl();
    };

    @EventLink
    public final Listener<EventShader.Blur> blurListener = event -> {
        if (!shouldRender()) return;
        if (mc.theWorld == null) return;

        float x = HudStyle.snap(hud.x);
        float y = HudStyle.snap(hud.y);
        DrawUtils.drawRoundedRect(x, y, x + SIZE, y + SIZE, HudStyle.RADIUS_CARD, 0xFFFFFFFF);
    };
}
