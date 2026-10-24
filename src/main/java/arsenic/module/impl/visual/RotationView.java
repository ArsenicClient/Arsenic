package arsenic.module.impl.visual;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
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
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.capture.RenderTargets;
import arsenic.utils.render.capture.SilentView;
import net.minecraft.client.gui.GuiChat;
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

    private final HudElement hud = hudElement("Rotation View", 4, 140, 160, 90);

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
        if (!shouldRender() || !wantsFrame() || !SilentView.hasFrame())
            return;

        int cardW = (int) (event.getSr().getScaledWidth() * size.getValue().getInput() / 100);
        int previewW = Math.max(80, cardW - 10);
        int previewH = Math.max(45, previewW * mc.displayHeight / mc.displayWidth);
        int headerH = 30;
        int footerH = 26;
        int cardH = headerH + previewH + footerH + 6;
        hud.setSize(cardW, cardH);
        HudStyle.pushScale(hud.x, hud.y, hud.scale);

        float x = hud.x, y = hud.y;
        float w = cardW, h = cardH;

        HudStyle.card(x, y, x + w, y + h, 16f, M3.alpha(M3.surfaceContainerHigh(), 238));

        float headerMid = y + headerH / 2f + 1f;
        M3Icons.drawCentered(M3Icons.CAMERA, x + 14f, headerMid, M3.primary(), 0.52f);
        FontRendererExtension<?> title = HudStyle.title();
        title.drawString("Rotation", x + 25f, headerMid, M3.onSurface(), title.CENTREY);

        FontRendererExtension<?> label = HudStyle.label();
        String state = SilentView.isSilent() ? "SILENT" : "CAMERA";
        int stateColor = SilentView.isSilent() ? M3.primary() : M3.onSurfaceVariant();
        label.drawString(state, x + w - 10f - label.getWidth(state), headerMid, stateColor, label.CENTREY);
        HudStyle.divider(x + 8f, y + headerH - 1f, x + w - 8f);

        float px = x + 5f, py = y + headerH;
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.bindTexture(SilentView.getFramebuffer().framebufferTexture);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        wr.pos(px, py + previewH, 0).tex(0, 0).endVertex();
        wr.pos(px + previewW, py + previewH, 0).tex(1, 0).endVertex();
        wr.pos(px + previewW, py, 0).tex(1, 1).endVertex();
        wr.pos(px, py, 0).tex(0, 1).endVertex();
        tessellator.draw();
        GlStateManager.bindTexture(0);
        if (blend)
            GlStateManager.enableBlend();

        if (crosshair.getValue()) {
            float cx = px + previewW / 2f, cy = py + previewH / 2f;
            int c = M3.alpha(M3.onSurface(), 190);
            DrawUtils.drawRect(cx - 3f, cy - 0.5f, cx + 3f, cy + 0.5f, c);
            DrawUtils.drawRect(cx - 0.5f, cy - 3f, cx + 0.5f, cy + 3f, c);
        }
        DrawUtils.drawRoundedOutline(px, py, px + previewW, py + previewH, 9f, 1f,
                M3.alpha(M3.outlineVariant(), 130));

        String yawPitch = String.format("%.1f / %.1f",
                MathHelper.wrapAngleTo180_float(SilentView.getViewYaw()), SilentView.getViewPitch());
        String cam = String.format("%.1f / %.1f",
                MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw), mc.thePlayer.rotationPitch);
        float footerMid = py + previewH + 13f;
        label.drawString("view", x + 10f, footerMid, M3.onSurfaceVariant(), label.CENTREY);
        label.drawString(yawPitch, x + 36f, footerMid, M3.onSurface(), label.CENTREY);
        label.drawString("cam", x + w - 76f, footerMid, M3.onSurfaceVariant(), label.CENTREY);
        label.drawString(cam, x + w - 10f - label.getWidth(cam), footerMid, M3.onSurface(), label.CENTREY);

        HudStyle.resetColor();
        HudStyle.popScale();
    };

    private boolean shouldRender() {
        return mc.currentScreen == null
                || mc.currentScreen instanceof GuiChat
                || mc.currentScreen instanceof HudEditorScreen;
    }
}
