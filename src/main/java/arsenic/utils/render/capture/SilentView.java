package arsenic.utils.render.capture;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.runtime.Platform;
import arsenic.utils.rotations.SilentRotationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.boss.BossStatus;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

/**
 * Renders the world a second time from the silent (server-side) rotation into its own framebuffer.
 * Shared by RotationView and the Recorder so the pass runs at most once per frame. Costs roughly a second
 * world render per frame while anything requests it.
 */
public final class SilentView {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private static Framebuffer framebuffer;
    private static boolean rendering, renderingHud, hasFrame, silent;
    private static float viewYaw, viewPitch;
    // held-item sway towards the silent rotation, stepped per tick like EntityPlayerSP#renderArmYaw
    private static float armYaw, armPitch, prevArmYaw, prevArmPitch;

    private SilentView() {
    }

    /**
     * Starts following ticks (and, on Forge, the camera). Forge posts its own events; elsewhere the tick comes from the
     * client's event bus and the camera follows the player's rotation fields, which the pass sets.
     */
    private static Object listener;

    public static void register() {
        if (Platform.isForge()) {
            listener = new ForgeListener();
            MinecraftForge.EVENT_BUS.register(listener);
        } else {
            listener = new TickListener();
            Arsenic.getArsenic().getEventManager().subscribe(listener);
        }
    }

    /** Stops following ticks again, for uninject. */
    public static void unregister() {
        if (listener == null)
            return;
        if (Platform.isForge())
            MinecraftForge.EVENT_BUS.unregister(listener);
        else
            Arsenic.getArsenic().getEventManager().unsubscribe(listener);
        listener = null;
    }

    /** Forge's events. Only loaded on Forge. */
    public static final class ForgeListener {
        // Forge (and OptiFine) apply this as the final camera rotation; overriding it here wins over anything that
        // reads or rewrites the player's rotation fields during the pass
        @SubscribeEvent
        public void onCameraSetup(EntityViewRenderEvent.CameraSetup event) {
            if (!rendering)
                return;
            event.yaw = viewYaw + 180.0F;
            event.pitch = viewPitch;
        }

        @SubscribeEvent
        public void onTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END)
                tick();
        }
    }

    /** The client's own tick, after the player updates, for games without Forge. */
    public static final class TickListener {
        @EventLink
        public final Listener<EventTick.Post> onTick = event -> tick();
    }

    private static void tick() {
        EntityPlayerSP player = mc.thePlayer;
        if (player == null)
            return;
        SilentRotationManager srm = Arsenic.getArsenic().getSilentRotationManager();
        float yaw = srm.isModified() ? srm.yaw : player.rotationYaw;
        float pitch = srm.isModified() ? srm.pitch : player.rotationPitch;
        prevArmYaw = armYaw;
        prevArmPitch = armPitch;
        armYaw += MathHelper.wrapAngleTo180_float(yaw - armYaw) * 0.5F;
        armPitch += (pitch - armPitch) * 0.5F;
    }

    /** True during the extra world pass; world-render events are not posted then. */
    public static boolean isRendering() {
        return rendering;
    }

    /** True while the vanilla HUD is drawn over the silent view; client HUD events are not posted then. */
    public static boolean isRenderingHud() {
        return renderingHud;
    }

    /** Whether this frame has a silent view to show. */
    public static boolean hasFrame() {
        return hasFrame && framebuffer != null;
    }

    public static Framebuffer getFramebuffer() {
        return framebuffer;
    }

    /** Whether the last pass used the silent rotation (false: nothing was rotating, so it followed the camera). */
    public static boolean isSilent() {
        return silent;
    }

    public static float getViewYaw() {
        return viewYaw;
    }

    public static float getViewPitch() {
        return viewPitch;
    }

    public static void onFrameStart() {
        hasFrame = false;
    }

    /** Called at the start of EntityRenderer#renderWorld (re-entered from here), before the main pass, so the main pass leaves render state as vanilla expects. */
    public static void render(float partialTicks, long finishTimeNano) {
        EntityPlayerSP player = mc.thePlayer;
        if (hasFrame || rendering || player == null || mc.theWorld == null || mc.getRenderViewEntity() != player || !OpenGlHelper.isFramebufferEnabled())
            return;
        SilentRotationManager srm = Arsenic.getArsenic().getSilentRotationManager();

        Framebuffer scene = RenderTargets.getScene();
        if (framebuffer == null || framebuffer.framebufferWidth != scene.framebufferWidth || framebuffer.framebufferHeight != scene.framebufferHeight) {
            if (framebuffer != null)
                framebuffer.deleteFramebuffer();
            framebuffer = new Framebuffer(scene.framebufferWidth, scene.framebufferHeight, true);
            framebuffer.setFramebufferFilter(GL11.GL_LINEAR);
        }

        float yaw = player.rotationYaw, prevYaw = player.prevRotationYaw;
        float pitch = player.rotationPitch, prevPitch = player.prevRotationPitch;
        float renderArmYaw = player.renderArmYaw, prevRenderArmYaw = player.prevRenderArmYaw;
        float renderArmPitch = player.renderArmPitch, prevRenderArmPitch = player.prevRenderArmPitch;
        int view = mc.gameSettings.thirdPersonView;
        rendering = true;
        Framebuffer previous = RenderTargets.push(framebuffer);
        try {
            // when nothing is rotating silently the server sees the real camera, which moves per frame not per tick.
            // Like third person, stay on the silent rotation for the tick after it is released so it eases from the
            // last silent yaw back to the camera instead of jumping
            silent = srm.isModified() || srm.isPrevModified();
            if (silent) {
                viewYaw = srm.getPrevYaw() + MathHelper.wrapAngleTo180_float(srm.yaw - srm.getPrevYaw()) * partialTicks;
                viewPitch = srm.getPrevPitch() + (srm.pitch - srm.getPrevPitch()) * partialTicks;
            } else {
                viewYaw = prevYaw + (yaw - prevYaw) * partialTicks;
                viewPitch = prevPitch + (pitch - prevPitch) * partialTicks;
            }
            player.rotationYaw = player.prevRotationYaw = viewYaw;
            player.rotationPitch = player.prevRotationPitch = viewPitch;
            // the held item sways by (rotation - arm rotation), so the arm has to follow the silent rotation too
            player.renderArmYaw = near(viewYaw, armYaw);
            player.prevRenderArmYaw = near(player.renderArmYaw, prevArmYaw);
            player.renderArmPitch = armPitch;
            player.prevRenderArmPitch = prevArmPitch;
            mc.gameSettings.thirdPersonView = 0;
            GlStateManager.enableDepth();
            GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.5F);
            // the whole renderWorld, not just renderWorldPass: OptiFine shaders set up and composite around the pass there
            mc.entityRenderer.renderWorld(partialTicks, finishTimeNano);
            hasFrame = true;
        } finally {
            player.rotationYaw = yaw;
            player.prevRotationYaw = prevYaw;
            player.rotationPitch = pitch;
            player.prevRotationPitch = prevPitch;
            player.renderArmYaw = renderArmYaw;
            player.prevRenderArmYaw = prevRenderArmYaw;
            player.renderArmPitch = renderArmPitch;
            player.prevRenderArmPitch = prevRenderArmPitch;
            mc.gameSettings.thirdPersonView = view;
            RenderTargets.pop(previous);
            rendering = false;
        }
    }

    /**
     * Draws the vanilla HUD (hotbar, crosshair, chat...) over this frame's silent view. Called after the main HUD pass;
     * Arsenic's own HUD is left out.
     */
    public static void renderHud(float partialTicks) {
        if (!hasFrame() || mc.thePlayer == null)
            return;
        EntityPlayerSP player = mc.thePlayer;
        GuiIngame gui = mc.ingameGUI;
        // state the HUD steps every time it is drawn rather than per tick
        float vignette = gui.prevVignetteBrightness;
        int bossTime = BossStatus.statusBarTime;
        float yaw = player.rotationYaw, prevYaw = player.prevRotationYaw;
        float pitch = player.rotationPitch, prevPitch = player.prevRotationPitch;
        int view = mc.gameSettings.thirdPersonView;
        renderingHud = true;
        Framebuffer previous = RenderTargets.push(framebuffer);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        try {
            player.rotationYaw = player.prevRotationYaw = viewYaw;
            player.rotationPitch = player.prevRotationPitch = viewPitch;
            mc.gameSettings.thirdPersonView = 0;
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
            gui.renderGameOverlay(partialTicks);
        } finally {
            gui.prevVignetteBrightness = vignette;
            BossStatus.statusBarTime = bossTime;
            player.rotationYaw = yaw;
            player.prevRotationYaw = prevYaw;
            player.rotationPitch = pitch;
            player.prevRotationPitch = prevPitch;
            mc.gameSettings.thirdPersonView = view;
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.popMatrix();
            GlStateManager.color(1, 1, 1, 1);
            RenderTargets.pop(previous);
            renderingHud = false;
        }
    }

    /** {@code angle} moved by whole turns to within 180 degrees of {@code reference}. */
    private static float near(float reference, float angle) {
        return reference + MathHelper.wrapAngleTo180_float(angle - reference);
    }

    /** Frees the framebuffer; the next {@link #render} recreates it. */
    public static void release() {
        hasFrame = false;
        if (framebuffer != null) {
            framebuffer.deleteFramebuffer();
            framebuffer = null;
        }
    }
}
