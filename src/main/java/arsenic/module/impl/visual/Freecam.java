package arsenic.module.impl.visual;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.Priorities;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventMovementInput;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.rotations.SilentRotationManager;
import net.minecraft.world.phys.Vec3;

/**
 * A camera that flies free of your body. Your character stands still and keeps facing the way it did (held as a silent
 * rotation, like FreeLook); WASD, jump and sneak move the camera and the mouse turns it. Clicks are ignored while it is
 * on, so nothing you do reaches the server.
 */
@ModuleInfo(name = "Freecam", description = "Fly the camera around while your body stays put", category = ModuleCategory.RENDER, tier = ModuleTier.QOL)
public class Freecam extends Module {

    public final DoubleProperty speed = new DoubleProperty("Speed", new DoubleValue(0.2, 3, 1, 0.1));

    private Vec3 prev, pos;
    private float heldYaw, heldPitch;
    private boolean captured;

    @Override
    protected void onEnable() {
        captured = false;
        if (mc.player == null)
            return;
        pos = prev = mc.player.getEyePosition(1f);
    }

    @Override
    protected void onDisable() {
        if (captured && mc.player != null) {
            // camera only: the server already has this rotation
            mc.player.setYRot(heldYaw);
            mc.player.yRotO = heldYaw;
            mc.player.setXRot(heldPitch);
            mc.player.xRotO = heldPitch;
        }
        captured = false;
        prev = pos = null;
    }

    /** Whether the camera should be drawn at {@link #cameraPos(float)} instead of at the player. */
    public boolean isActive() {
        return isEnabled() && captured && pos != null;
    }

    public Vec3 cameraPos(float partialTicks) {
        return prev.lerp(pos, partialTicks);
    }

    @RequiresPlayer
    @EventLink(Priorities.HIGH)
    public final Listener<EventMovementInput> onInput = event -> {
        event.setSpeed(0);
        event.setStrafe(0);
        event.setJump(false);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        if (!captured) {
            heldYaw = mc.player.getYRot();
            heldPitch = mc.player.getXRot();
            captured = true;
        }
        event.setYaw(heldYaw);
        event.setPitch(heldPitch);
        event.setSpeed(180f);
        event.setSmoothing(false);
        event.setBlockUserInput(true);
        event.setMovementFix(SilentRotationManager.MovementFix.OFF);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (!captured || pos == null)
            return;
        prev = pos;
        if (mc.gui.screen() != null)
            return;
        float forward = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
        float strafe = (mc.options.keyLeft.isDown() ? 1 : 0) - (mc.options.keyRight.isDown() ? 1 : 0);
        float up = (mc.options.keyJump.isDown() ? 1 : 0) - (mc.options.keyShift.isDown() ? 1 : 0);
        Vec3 look = mc.player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
        Vec3 right = new Vec3(-flat.z, 0, flat.x);
        Vec3 move = look.scale(forward).add(right.scale(-strafe)).add(0, up, 0);
        if (move.lengthSqr() > 0)
            pos = pos.add(move.normalize().scale(speed.getValue().getInput()));
    };
}
