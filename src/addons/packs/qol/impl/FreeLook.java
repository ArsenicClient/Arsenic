import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventSilentRotation;
import arsenic.gui.hud.HudElement;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.rotations.SilentRotationManager;
import org.lwjgl.opengl.GL11;

/**
 * Free camera: the rotation at the moment the module is enabled is held as a silent rotation, so the server (and your
 * body) keeps facing that way while the camera looks around. With Movement Fix SILENT you keep walking where the camera
 * points. Clicks are ignored while the camera and the held rotation differ, so a click never goes out the wrong way.
 */
@ModuleInfo(name = "FreeLook", description = "Free camera, your body keeps its direction", category = ModuleCategory.PLAYER)
public class FreeLook extends Module {

    public final EnumProperty<SilentRotationManager.MovementFix> fix =
            new EnumProperty<>("Movement Fix", SilentRotationManager.MovementFix.SILENT);
    public final BooleanProperty blockClicks = new BooleanProperty("Block Clicks", true);
    public final BooleanProperty thirdPerson = new BooleanProperty("Third Person", true);
    public final BooleanProperty snapBack = new BooleanProperty("Snap Back", true);
    public final BooleanProperty render = new BooleanProperty("Render", true);
    public final BooleanProperty hud = new BooleanProperty("HUD", true);

    private final HudElement panel = hudElement("FreeLook", 4, 160, 90, 16);

    private boolean active;            // held rotation captured
    private float frozenYaw, frozenPitch;
    private int prevView = -1;         // camera view before enabling

    @Override
    protected void onEnable() {
        active = false;                // captured on the first tick, from the real view
        prevView = -1;
        if (thirdPerson.getValue() && mc.gameSettings != null && mc.thePlayer != null) {
            prevView = mc.gameSettings.thirdPersonView;
            mc.gameSettings.thirdPersonView = 1;
        }
    }

    @Override
    protected void onDisable() {
        if (active && snapBack.getValue() && mc.thePlayer != null) {
            // camera only: the server already has this rotation, nothing new is sent
            mc.thePlayer.rotationYaw = frozenYaw;
            mc.thePlayer.prevRotationYaw = frozenYaw;
            mc.thePlayer.rotationPitch = frozenPitch;
            mc.thePlayer.prevRotationPitch = frozenPitch;
        }
        if (prevView != -1 && mc.gameSettings != null) mc.gameSettings.thirdPersonView = prevView;
        active = false;
        prevView = -1;
    }

    @Override
    public String getHudInfo() { return null; }

    // ---- request the held rotation every tick ----
    @RequiresPlayer @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        if (!active) {                                       // event.getYaw/Pitch = real view
            frozenYaw = event.getYaw();
            frozenPitch = event.getPitch();
            active = true;
        }
        boolean differs = Math.abs(RotationUtils.getYawDifference(event.getYaw(), frozenYaw)) > 0.5f
                || Math.abs(event.getPitch() - frozenPitch) > 0.5f;

        event.setYaw(frozenYaw);
        event.setPitch(frozenPitch);
        event.setSpeed(180f);
        event.setSmoothing(false);                           // hold the value, no easing
        event.setBlockUserInput(blockClicks.getValue() && differs);   // resets to false every tick
        event.setMovementFix(fix.getValue());
    };

    // ---- world visual: arrow on the ground in the held direction ----
    @RequiresPlayer @EventLink
    public final Listener<EventRenderWorldLast> onWorld = event -> {
        if (!render.getValue() || !active || mc.theWorld == null || mc.thePlayer == null) return;
        double px = mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * event.partialTicks - mc.getRenderManager().viewerPosX;
        double py = mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * event.partialTicks - mc.getRenderManager().viewerPosY + 0.05;
        double pz = mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * event.partialTicks - mc.getRenderManager().viewerPosZ;

        double yawRad = Math.toRadians(frozenYaw);
        double dx = -Math.sin(yawRad), dz = Math.cos(yawRad);   // horizontal direction of the yaw
        double len = 2.5;
        double ex = px + dx * len, ez = pz + dz * len;
        double tipYawL = yawRad + Math.toRadians(150), tipYawR = yawRad - Math.toRadians(150);

        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glLineWidth(2.5f);
        GL11.glColor4f(0.35f, 0.7f, 1f, 0.9f);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(px, py, pz);
        GL11.glVertex3d(ex, py, ez);
        GL11.glVertex3d(ex, py, ez);
        GL11.glVertex3d(ex - Math.sin(tipYawL) * 0.5, py, ez + Math.cos(tipYawL) * 0.5);
        GL11.glVertex3d(ex, py, ez);
        GL11.glVertex3d(ex - Math.sin(tipYawR) * 0.5, py, ez + Math.cos(tipYawR) * 0.5);
        GL11.glEnd();
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    };

    // ---- HUD: angle between the camera and the body ----
    @EventLink
    public final Listener<EventRender2D> onHud = event -> {
        if (!hud.getValue() || mc.thePlayer == null) return;
        String text;
        if (active) {
            int diff = (int) Math.abs(RotationUtils.getYawDifference(mc.thePlayer.rotationYaw, frozenYaw));
            text = "FreeLook  " + diff + "°";
        } else {
            text = "FreeLook  Idle";
        }
        panel.setSize(Math.max(60, mc.fontRendererObj.getStringWidth(text) + 12), 16);
        DrawUtils.drawRoundedRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 5, 0x96121212);
        mc.fontRendererObj.drawStringWithShadow(text, panel.x + 6, panel.y + 4, 0xFFFFFFFF);
    };
}
