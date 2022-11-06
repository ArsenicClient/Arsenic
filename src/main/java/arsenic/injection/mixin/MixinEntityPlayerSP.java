package arsenic.injection.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.authlib.GameProfile;

import arsenic.event.impl.EventTick;
import arsenic.event.impl.EventUpdate;
import arsenic.main.Arsenic;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.world.World;

@Mixin(priority = 1111, value = EntityPlayerSP.class)
public class MixinEntityPlayerSP extends AbstractClientPlayer {

    private double cachedX;
    private double cachedY;
    private double cachedZ;

    private boolean cachedOnGround;

    private float cachedRotationPitch;
    private float cachedRotationYaw;

    public MixinEntityPlayerSP(World p_i45074_1_, GameProfile p_i45074_2_) {
        super(p_i45074_1_, p_i45074_2_);
    }

    @Inject(method = "onUpdateWalkingPlayer", at = @At("HEAD"))
    private void onUpdateWalkingPlayerPre(CallbackInfo ci) {
        cachedX = posX;
        cachedY = posY;
        cachedZ = posZ;

        cachedOnGround = onGround;

        cachedRotationYaw = rotationYaw;
        cachedRotationPitch = rotationPitch;

        EventUpdate event = new EventUpdate.Pre(posX, posY, posZ, rotationYaw, rotationPitch, onGround);
        Arsenic.getInstance().getEventManager().post(event);

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
    }

    @Inject(method = "onUpdateWalkingPlayer", at = @At("RETURN"))
    private void onUpdateWalkingPlayerPost(CallbackInfo ci) {
        posX = cachedX;
        posY = cachedY;
        posZ = cachedZ;

        onGround = cachedOnGround;

        rotationYaw = cachedRotationYaw;
        rotationPitch = cachedRotationPitch;

        Arsenic.getInstance().getEventManager().post(new EventUpdate.Post(posX, posY, posZ, rotationYaw, rotationPitch, onGround));
    }

}
