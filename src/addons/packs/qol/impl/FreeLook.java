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
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.rotations.SilentRotationManager;
import net.minecraft.client.CameraType;
import net.minecraft.world.phys.Vec3;

/**
 * Free camera: the rotation at the moment the module is enabled is held as a silent rotation, so the server (and your
 * body) keeps facing that way while the camera looks around. With Movement Fix SILENT you keep walking where the camera
 * points. Clicks are ignored while the camera and the held rotation differ, so a click never goes out the wrong way.
 */
@ModuleInfo(name = "FreeLook", description = "Free camera, your body keeps its direction", category = ModuleCategory.PLAYER, tier = ModuleTier.QOL)
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
    private CameraType prevView;       // camera view before enabling

    @Override
    protected void onEnable() {
        active = false;                // captured on the first tick, from the real view
        prevView = null;
        if (thirdPerson.getValue() && mc.player != null) {
            prevView = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
    }

    @Override
    protected void onDisable() {
        if (active && snapBack.getValue() && mc.player != null) {
            // camera only: the server already has this rotation, nothing new is sent
            mc.player.setYRot(frozenYaw);
            mc.player.yRotO = frozenYaw;
            mc.player.setXRot(frozenPitch);
            mc.player.xRotO = frozenPitch;
        }
        if (prevView != null) mc.options.setCameraType(prevView);
        active = false;
        prevView = null;
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
        if (!render.getValue() || !active || mc.level == null || mc.player == null) return;
        Vec3 from = RenderUtils.interpolatedPosition(mc.player).add(0, 0.05, 0);
        double yawRad = Math.toRadians(frozenYaw);
        double dx = -Math.sin(yawRad), dz = Math.cos(yawRad);   // horizontal direction of the yaw
        Vec3 tip = from.add(dx * 2.5, 0, dz * 2.5);
        double tipL = yawRad + Math.toRadians(150), tipR = yawRad - Math.toRadians(150);
        int colour = 0xE659B3FF;
        RenderUtils.drawLine(from, tip, colour, 2.5f);
        RenderUtils.drawLine(tip, tip.add(-Math.sin(tipL) * 0.5, 0, Math.cos(tipL) * 0.5), colour, 2.5f);
        RenderUtils.drawLine(tip, tip.add(-Math.sin(tipR) * 0.5, 0, Math.cos(tipR) * 0.5), colour, 2.5f);
    };

    // ---- HUD: angle between the camera and the body ----
    @EventLink
    public final Listener<EventRender2D> onHud = event -> {
        if (!hud.getValue() || mc.player == null) return;
        String text;
        if (active) {
            int diff = (int) Math.abs(RotationUtils.getYawDifference(mc.player.getYRot(), frozenYaw));
            text = "FreeLook  " + diff + "°";
        } else {
            text = "FreeLook  Idle";
        }
        panel.setSize(Math.max(60, mc.font.width(text) + 12), 16);
        DrawUtils.drawRoundedRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 5, 0x96121212);
        DrawUtils.drawStringWithShadow(text, panel.x + 6, panel.y + 4, 0xFFFFFFFF);
    };
}
