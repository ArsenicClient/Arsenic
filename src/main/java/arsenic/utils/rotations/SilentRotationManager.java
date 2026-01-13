package arsenic.utils.rotations;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.*;
import arsenic.injection.accessor.IMixinRenderManager;
import arsenic.main.Arsenic;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.render.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;

import java.awt.*;

import static net.minecraft.util.MathHelper.wrapAngleTo180_float;

public class SilentRotationManager {


    private final Minecraft mc = Minecraft.getMinecraft();
    public float yaw;
    private float prevYaw;
    public float pitch ;
    private float prevPitch;
    private boolean modified;
    private boolean doMovementFix;
    private boolean doJumpFix;
    private float speed;

    @EventLink
    public final Listener<EventLiving> eventTickListener = event -> {
        EventSilentRotation rotation = new EventSilentRotation(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, speed);
        Arsenic.getArsenic().getEventManager().post(rotation);
        prevYaw = yaw;
        prevPitch = pitch;

        if(!modified && !rotation.hasBeenModified()) {
            yaw = mc.thePlayer.rotationYaw;
            pitch = mc.thePlayer.rotationPitch;
            return;
        }

        doMovementFix = rotation.doMovementFix();
        doJumpFix = rotation.doJumpFix();
        speed = rotation.getSpeed();

        float yawDelta = getYawDelta(rotation.getYaw());
        float pitchDelta = getPitchDelta(rotation.getPitch());
        yaw = prevYaw + yawDelta;
        pitch = prevPitch + pitchDelta;

        float[] rotations = RotationUtils.patchGCD(
                new float[]{prevYaw, prevPitch},
                new float[]{yaw, pitch}
        );

        yaw = rotations[0];
        pitch = rotations[1];

        modified = rotation.hasBeenModified() || (Math.abs(RotationUtils.getYawDifference(mc.thePlayer.rotationYaw, yaw)) > speed);
    };

    @EventLink
    public final Listener<EventUpdate.Pre> eventUpdateListener = event -> {
        if(!modified)
            return;
        event.setYaw(yaw);
        event.setPitch(pitch);
    };

    @EventLink
    public final Listener<EventLook> eventLookListener = event -> {
        if(!modified)
            return;
        event.setYaw(yaw);
        event.setPitch(pitch);
    };

    @EventLink
    public final Listener<EventRenderThirdPerson> eventRenderThirdPersonListener = event -> {
        if(!modified)
            return;
        event.setAccepted(true);
        event.setYaw(yaw);
        event.setPrevYaw(prevYaw);
        event.setPitch(pitch);
        event.setPrevPitch(prevPitch);
    };

    @EventLink
    public final Listener<EventMove> eventMoveListener = event -> {
        if(!modified || !doMovementFix)
            return;

        event.setYaw(yaw);
    };

    @EventLink
    public final Listener<EventMovementInput> eventMovementInputListener = event -> {
        if(!modified || !doMovementFix || (event.getSpeed() == 0 && event.getStrafe() == 0)) return;
        float moveAngle = wrapAngleToPi(normaliseYaw(mc.thePlayer.rotationYaw) + (float) Math.atan2(-event.getStrafe(), event.getSpeed()));
        float moveKeyAngle = wrapAngleToPi(moveAngle - (float) Math.toRadians(yaw));
        float speedValue = (moveKeyAngle >= -Math.PI * 3/8 && moveKeyAngle <= Math.PI * 3/8) ? 1 : (moveKeyAngle <= -Math.PI * 5/8 || moveKeyAngle >= Math.PI * 5/8) ? -1 : 0;
        float strafeValue = (moveKeyAngle >= Math.PI/8 && moveKeyAngle <= Math.PI * 7/8) ? -1 : (moveKeyAngle <= -Math.PI/8 && moveKeyAngle >= -Math.PI * 7/8) ? 1 : 0;
        if(mc.thePlayer.isSneaking()) {
            speedValue *= 0.3F;
            strafeValue *= 0.3F;
        }
        event.setSpeed(speedValue);
        event.setStrafe(strafeValue);
    };

    @EventLink
    public final Listener<EventJump> eventJumpListener = event -> {
        if(!modified || !doJumpFix)
            return;
        event.setYaw(yaw);
    };

    public float normaliseYaw(float yaw) {
        return (float) Math.toRadians(MathHelper.wrapAngleTo180_float(yaw));
    }

    public static float wrapAngleToPi(float value) {
        float twoPi = (float)(2.0 * Math.PI);
        value = (value + (float)Math.PI) % twoPi;
        if (value < 0) {
            value += twoPi;
        }
        return value - (float)Math.PI;
    }


    private float getYawDelta(float targetYaw) {
        float delta = wrapAngleTo180_float(wrapAngleTo180_float(targetYaw) - wrapAngleTo180_float(prevYaw));
        float speedValue = (float) (speed * ((Math.sin(Math.toRadians(Math.abs(delta)))/2.0f) + 0.5f));
        return Math.min(speedValue, Math.abs(delta)) * Math.signum(delta);
    }

    private float getPitchDelta(float targetPitch) {
        float delta = targetPitch - prevPitch;
        float speedValue = (float) (speed * ((Math.sin(Math.toRadians(Math.abs(delta)))/2.0f) + 0.5f));
        return Math.min(speedValue, Math.abs(delta)) * Math.signum(delta);
    }

}
