package arsenic.runtime.hooks;

import arsenic.event.impl.EventLook;
import arsenic.event.impl.EventMove;
import arsenic.main.Arsenic;
import arsenic.module.impl.ghost.AimAssist;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

/** {@link Entity} hooks. */
public final class EntityHooks {

    // moveFlying calls itself once with the event's values; only ever true for the local player
    private static boolean secondCall;

    private EntityHooks() {}

    /** moveFlying HEAD. @return true to cancel */
    public static boolean moveFlyingHead(Entity self, float strafe, float forward, float friction) {
        if (self != Minecraft.getMinecraft().thePlayer)
            return false;
        if (secondCall) {
            secondCall = false;
            return false;
        }
        EventMove e = new EventMove(strafe, forward, friction, self.rotationYaw);
        Arsenic.getArsenic().getEventManager().post(e);
        float cachedYawM = self.rotationYaw;
        self.rotationYaw = e.getYaw();
        secondCall = true;
        self.moveFlying(e.getStrafe(), e.getForward(), e.getFriction());
        self.rotationYaw = cachedYawM;
        return true;
    }

    /** setAngles HEAD: replaces the yaw argument. */
    public static float setAnglesYaw(Entity self, float yaw, float pitch) {
        if (self != Minecraft.getMinecraft().thePlayer)
            return yaw;
        AimAssist aimAssist = Arsenic.getArsenic().getModuleManager().getModuleByClass(AimAssist.class);
        if (!aimAssist.isEnabled())
            return yaw;
        return aimAssist.modifyYaw(yaw);
    }

    /** setAngles HEAD: replaces the pitch argument. */
    public static float setAnglesPitch(Entity self, float yaw, float pitch) {
        if (self != Minecraft.getMinecraft().thePlayer)
            return pitch;
        AimAssist aimAssist = Arsenic.getArsenic().getModuleManager().getModuleByClass(AimAssist.class);
        if (!aimAssist.isEnabled())
            return pitch;
        return aimAssist.modifyPitch(pitch);
    }

    /** rayTrace: the look vector it traces along (the second Vec3 it stores). */
    public static Vec3 rayTraceLook(Entity self, Vec3 look) {
        if (self != Minecraft.getMinecraft().getRenderViewEntity())
            return look;
        EventLook eventLook = new EventLook(self.rotationYaw, self.rotationPitch);
        Arsenic.getArsenic().getEventManager().post(eventLook);
        if (!eventLook.hasBeenModified())
            return look;
        return vectorForRotation(eventLook.getPitch(), eventLook.getYaw());
    }

    /** Same as Entity.getVectorForRotation. */
    public static Vec3 vectorForRotation(float pitch, float yaw) {
        float f = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float f1 = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float f2 = -MathHelper.cos(-pitch * 0.017453292F);
        float f3 = MathHelper.sin(-pitch * 0.017453292F);
        return new Vec3(f1 * f2, f3, f * f2);
    }
}
