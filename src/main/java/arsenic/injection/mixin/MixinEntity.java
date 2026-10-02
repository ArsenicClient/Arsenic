package arsenic.injection.mixin;

import arsenic.event.impl.EventLook;
import arsenic.event.impl.EventMove;
import arsenic.main.Arsenic;
import arsenic.main.MinecraftAPI;
import arsenic.module.impl.ghost.AimAssist;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class MixinEntity {

    @Shadow
    public abstract float getYRot();

    @Shadow
    public abstract float getXRot();

    @Shadow
    public abstract Vec3 getDeltaMovement();

    @Shadow
    public abstract void setDeltaMovement(Vec3 deltaMovement);

    @Shadow
    protected static Vec3 getInputVector(Vec3 input, float speed, float yRot) {
        throw new AssertionError();
    }

    /**
     * 1.8's moveFlying. Lets the movement fix apply the player's input relative to the silent yaw
     * instead of the camera yaw, so server-side prediction matches the rotation that was sent.
     */
    @Inject(method = "moveRelative", at = @At("HEAD"), cancellable = true)
    private void arsenic$moveRelative(float speed, Vec3 input, CallbackInfo ci) {
        if ((Object) this != Minecraft.getInstance().player)
            return;
        EventMove event = new EventMove((float) input.x, (float) input.z, speed, getYRot());
        Arsenic.getArsenic().getEventManager().post(event);
        Vec3 delta = getInputVector(new Vec3(event.getStrafe(), input.y, event.getForward()), event.getFriction(), event.getYaw());
        setDeltaMovement(getDeltaMovement().add(delta));
        ci.cancel();
    }

    // AimAssist hooks the actual mouse-look deltas here, where Minecraft turns them into a rotation
    // change, and swallows them while it has a target so the mouse can't fight its turn.
    @ModifyVariable(method = "turn", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double arsenic$aimAssistYaw(double yaw) {
        if ((Object) this != Minecraft.getInstance().player)
            return yaw;
        AimAssist aimAssist = Arsenic.getArsenic().getModuleManager().getModuleByClass(AimAssist.class);
        if (aimAssist == null || !aimAssist.isEnabled())
            return yaw;
        return aimAssist.modifyYaw((float) yaw);
    }

    @ModifyVariable(method = "turn", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private double arsenic$aimAssistPitch(double pitch) {
        if ((Object) this != Minecraft.getInstance().player)
            return pitch;
        AimAssist aimAssist = Arsenic.getArsenic().getModuleManager().getModuleByClass(AimAssist.class);
        if (aimAssist == null || !aimAssist.isEnabled())
            return pitch;
        return aimAssist.modifyPitch((float) pitch);
    }

    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void arsenic$espColour(CallbackInfoReturnable<Integer> cir) {
        arsenic.module.impl.visual.ESP esp = Arsenic.getArsenic().getModuleManager().getModuleByClass(arsenic.module.impl.visual.ESP.class);
        if (esp != null && esp.shouldGlow((Entity) (Object) this))
            cir.setReturnValue(esp.getGlowColour((Entity) (Object) this));
    }

    /**
     * The crosshair raycast reads the camera entity's view vector. While a silent rotation is
     * active the client should pick along the rotation the server sees, not the camera's. The flag
     * keeps every other caller of getViewVector (rendering, sounds, ...) on the real rotation.
     */
    @Inject(method = "getViewVector", at = @At("HEAD"), cancellable = true)
    private void arsenic$getViewVector(float partialTicks, CallbackInfoReturnable<Vec3> cir) {
        if (!MinecraftAPI.picking || (Object) this != Minecraft.getInstance().getCameraEntity())
            return;
        EventLook eventLook = new EventLook(getYRot(), getXRot());
        Arsenic.getArsenic().getEventManager().post(eventLook);
        if (eventLook.hasBeenModified())
            cir.setReturnValue(Entity.calculateViewVector(eventLook.getPitch(), eventLook.getYaw()));
    }
}
