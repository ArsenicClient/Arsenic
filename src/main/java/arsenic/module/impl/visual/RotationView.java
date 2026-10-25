package arsenic.module.impl.visual;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.gui.design.M3;
import arsenic.gui.hud.HudElement;
import arsenic.gui.hud.HudStyle;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.capture.RenderTargets;
import arsenic.utils.render.capture.SilentView;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;

/**
 * Debug picture-in-picture of the world from the silent (server-side) rotation, for tuning Scaffold / KillAura
 * rotations. The extra world pass itself lives in {@link SilentView}.
 */
@ModuleInfo(name = "RotationView", category = ModuleCategory.RENDER, description = "Shows the world from your silent rotation", tier = ModuleTier.QOL)
public class RotationView extends Module {

    public final DoubleProperty size = new DoubleProperty("Size", new DoubleValue(15, 50, 25, 1));
    public final BooleanProperty onlyWhenRotating = new BooleanProperty("Only When Rotating", false);
    public final BooleanProperty crosshair = new BooleanProperty("Crosshair", true);

    private final HudElement hud = hudElement("Rotation View", 6, 140, 160, 108);

    /** Whether the silent pass should run this frame for the picture-in-picture. */
    public boolean wantsFrame() {
        return !onlyWhenRotating.getValue() || Arsenic.getArsenic().getSilentRotationManager().isModified();
    }

    @Override
    protected void onDisable() {
        // the recorder may still be filming through it
        if (!RenderTargets.recordsSilentView())
            SilentView.release();
    }

    @EventLink
    public final Listener<EventRender2D> onRender2D = event -> {
        if (!wantsFrame() || !SilentView.hasFrame())
            return;

        FontRendererExtension<?> labelFr = HudStyle.label();
        FontRendererExtension<?> bodyFr = HudStyle.body();
        if (labelFr == null || bodyFr == null)
            return;

        int viewW = (int) (event.getSr().getScaledWidth() * size.getValue().getInput() / 100);
        int viewH = viewW * mc.displayHeight / mc.displayWidth;
        float pad = 3.5f;
        float footerH = 16f;
        int totalW = Math.round(viewW + pad * 2f);
        int totalH = Math.round(viewH + pad * 2f + footerH);
        hud.setSize(totalW, totalH);

        float x = HudStyle.snap(hud.x);
        float y = HudStyle.snap(hud.y);
        HudStyle.card(x, y, x + totalW, y + totalH, HudStyle.RADIUS_CARD, 225);

        float vx = HudStyle.snap(x + pad);
        float vy = HudStyle.snap(y + pad);

        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.bindTexture(SilentView.getFramebuffer().framebufferTexture);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        wr.pos(vx, vy + viewH, 0).tex(0, 0).endVertex();
        wr.pos(vx + viewW, vy + viewH, 0).tex(1, 0).endVertex();
        wr.pos(vx + viewW, vy, 0).tex(1, 1).endVertex();
        wr.pos(vx, vy, 0).tex(0, 1).endVertex();
        tessellator.draw();
        GlStateManager.bindTexture(0);
        if (blend)
            GlStateManager.enableBlend();

        DrawUtils.drawRoundedOutline(vx, vy, vx + viewW, vy + viewH, 3.5f, 1f, M3.alpha(M3.outlineVariant(), 150));

        if (crosshair.getValue()) {
            float cx = vx + viewW / 2f;
            float cy = vy + viewH / 2f;
            int shadow = M3.alpha(M3.surfaceContainerLowest(), 190);
            int arm = M3.alpha(M3.onSurface(), 220);
            DrawUtils.drawRect(cx - 4.5f, cy - 1f, cx + 4.5f, cy + 1f, shadow);
            DrawUtils.drawRect(cx - 1f, cy - 4.5f, cx + 1f, cy + 4.5f, shadow);
            DrawUtils.drawRect(cx - 3.5f, cy - 0.5f, cx + 3.5f, cy + 0.5f, arm);
            DrawUtils.drawRect(cx - 0.5f, cy - 3.5f, cx + 0.5f, cy + 3.5f, arm);
            DrawUtils.drawCircle(cx, cy, 1.0f, M3.primary());
        }

        float footerCy = vy + viewH + (footerH + pad) / 2f;
        boolean silent = SilentView.isSilent();
        String modeTag = silent ? "SILENT" : "CAMERA";
        int chipBg = silent ? M3.primaryContainer() : M3.alpha(M3.surfaceContainerHigh(), 215);
        int chipFg = silent ? M3.onPrimaryContainer() : M3.onSurfaceVariant();
        float chipW = HudStyle.chip(labelFr, modeTag, vx + 1f, footerCy, 11f, 4f, chipBg, chipFg);

        String angles = String.format("%.1f° / %.1f°",
                MathHelper.wrapAngleTo180_float(SilentView.getViewYaw()), SilentView.getViewPitch());
        bodyFr.setScale(0.86f);
        bodyFr.drawString(angles, vx + viewW - 1f - bodyFr.getWidth(angles), footerCy, M3.onSurfaceVariant(), bodyFr.CENTREY);
        bodyFr.resetScale();

        HudStyle.resetGl();
    };
}
