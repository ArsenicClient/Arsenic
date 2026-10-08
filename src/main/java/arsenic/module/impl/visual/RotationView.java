package arsenic.module.impl.visual;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.gui.hud.HudElement;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.capture.RenderTargets;
import arsenic.utils.render.capture.SilentView;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;

import java.awt.*;

/**
 * Debug picture-in-picture of the world from the silent (server-side) rotation, for tuning Scaffold / KillAura
 * rotations. The extra world pass itself lives in {@link SilentView}.
 */
@ModuleInfo(name = "RotationView", category = ModuleCategory.RENDER, description = "Shows the world from your silent rotation")
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
        if (!wantsFrame() || !SilentView.hasFrame())
            return;
        int w = (int) (event.getSr().getScaledWidth() * size.getValue().getInput() / 100);
        int h = w * mc.displayHeight / mc.displayWidth;
        hud.setSize(w, h);
        int x = hud.x, y = hud.y;

        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.bindTexture(SilentView.getFramebuffer().framebufferTexture);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        wr.pos(x, y + h, 0).tex(0, 0).endVertex();
        wr.pos(x + w, y + h, 0).tex(1, 0).endVertex();
        wr.pos(x + w, y, 0).tex(1, 1).endVertex();
        wr.pos(x, y, 0).tex(0, 1).endVertex();
        tessellator.draw();
        GlStateManager.bindTexture(0);
        if (blend)
            GlStateManager.enableBlend();

        if (crosshair.getValue()) {
            float cx = x + w / 2f, cy = y + h / 2f;
            int c = new Color(255, 255, 255, 200).getRGB();
            DrawUtils.drawRect(cx - 3, cy - 0.5f, cx + 3, cy + 0.5f, c);
            DrawUtils.drawRect(cx - 0.5f, cy - 3, cx + 0.5f, cy + 3, c);
        }
        int theme = Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor();
        DrawUtils.drawRoundedOutline(x, y, x + w, y + h, 2f, 1f, theme);

        String info = String.format("%s %.1f / %.1f  cam %.1f / %.1f", SilentView.isSilent() ? "silent" : "camera",
                MathHelper.wrapAngleTo180_float(SilentView.getViewYaw()), SilentView.getViewPitch(),
                MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw), mc.thePlayer.rotationPitch);
        mc.fontRendererObj.drawStringWithShadow(info, x + 2, y + h + 2, -1);
    };
}
