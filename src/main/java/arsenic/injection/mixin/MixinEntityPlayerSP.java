package arsenic.injection.mixin;

import arsenic.event.impl.*;
import arsenic.injection.accessor.IMixinEntityPlayerSP;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.World;
import com.mojang.authlib.GameProfile;

import arsenic.main.Arsenic;
import arsenic.module.impl.ghost.HitSelect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MovingObjectPosition;

import static arsenic.main.MinecraftAPI.mouseDownLastTick;

@Mixin(priority = 1111, value = EntityPlayerSP.class)
public abstract class MixinEntityPlayerSP extends AbstractClientPlayer implements IMixinEntityPlayerSP {

    private double cachedX;
    private double cachedY;
    private double cachedZ;

    private boolean cachedOnGround;

    private float cachedRotationPitch;
    private float cachedRotationYaw;

    public MixinEntityPlayerSP(World p_i45074_1_, GameProfile p_i45074_2_) {
        super(p_i45074_1_, p_i45074_2_);
    }

    @Inject(method = "onUpdateWalkingPlayer", at = @At("HEAD"), cancellable = true)
    private void onUpdateWalkingPlayerPre(CallbackInfo ci) {
        cachedX = posX;
        cachedY = posY;
        cachedZ = posZ;

        cachedOnGround = onGround;

        cachedRotationYaw = rotationYaw;
        cachedRotationPitch = rotationPitch;

        EventUpdate event = new EventUpdate.Pre(posX, posY, posZ, rotationYaw, rotationPitch, onGround);
        Arsenic.getInstance().getEventManager().post(event);
        if(event.isCancelled()) {
            ci.cancel();
            return;
        }

        posX = event.getX();
        posY = event.getY();
        posZ = event.getZ();

        onGround = event.isOnGround();

        rotationYaw = event.getYaw();
        rotationPitch = event.getPitch();
    }

    @Inject(method = "onUpdate", at = @At("HEAD"))
    private void onUpdate(CallbackInfo ci) {
        Arsenic.getInstance().getEventManager().post(new EventTick());
        for(int i = 0; i < 3; i++) {
            if (Mouse.isButtonDown(i) && !mouseDownLastTick[i]) {
                mouseDownLastTick[i] = true;
                Arsenic.getArsenic().getEventManager().post(new EventMouse.Down(i));
            } else if (!Mouse.isButtonDown(i) && mouseDownLastTick[i]) {
                mouseDownLastTick[i] = false;
                Arsenic.getArsenic().getEventManager().post(new EventMouse.Up(i));
            }
        }
    }

    @Inject(method = "onUpdate", at = @At("RETURN"))
    private void onUpdatePost(CallbackInfo ci) {
        Arsenic.getInstance().getEventManager().post(new EventTick.Post());
    }

    @Inject(method = "onLivingUpdate", at = @At("HEAD"))
    public void onLivingUpdate(CallbackInfo ci) {
        Arsenic.getInstance().getEventManager().post(new EventLiving());
    }

    // When HitSelect holds a hit, suppress the swing too - EntityPlayerSP#swingItem plays the
    // animation AND sends C0APacketAnimation, so cancelling here means a held hit produces no
    // arm swing and no swing packet, keeping it invisible to the server. The swing fires before
    // the attack in clickMouse, so we decide from the pointed entity and let the attack hook
    // reuse the same (idempotent) decision.
    @Inject(method = "swingItem", at = @At("HEAD"), cancellable = true)
    private void arsenic$hitSelectSwing(CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.objectMouseOver == null
                || mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY)
            return;

        HitSelect hitSelect = Arsenic.getArsenic().getModuleManager().getModuleByClass(HitSelect.class);
        if (hitSelect != null && hitSelect.shouldBlock(mc.objectMouseOver.entityHit))
            ci.cancel();
    }


    @Inject(method = "onUpdateWalkingPlayer", at = @At("RETURN"))
    private void onUpdateWalkingPlayerPost(CallbackInfo ci) {
        posX = cachedX;
        posY = cachedY;
        posZ = cachedZ;

        onGround = cachedOnGround;

        rotationYaw = cachedRotationYaw;
        rotationPitch = cachedRotationPitch;

        Arsenic.getInstance().getEventManager()
                .post(new EventUpdate.Post(posX, posY, posZ, rotationYaw, rotationPitch, onGround));
    }

    /*@Inject(method = "isSneaking", at = @At("RETURN"), cancellable = true)
    private void isSneaking(CallbackInfoReturnable<Boolean> cir) {
        SafeWalk safeWalk = Arsenic.getInstance().getModuleManager().getModuleByClass(SafeWalk.class);
        if(!safeWalk.isEnabled()) {
            return;
        }
        cir.setReturnValue(safeWalk.isSneaking() || cir.getReturnValue());
    }

    @ModifyVariable(method = "onLivingUpdate", at = @At("STORE"), ordinal = 0)
    private boolean flag1(boolean flag1) {
        SafeWalk safeWalk = Arsenic.getInstance().getModuleManager().getModuleByClass(SafeWalk.class);
        if(!safeWalk.isEnabled()) {
            return flag1;
        }
        return safeWalk.isSneaking() || flag1;
    } */

}
