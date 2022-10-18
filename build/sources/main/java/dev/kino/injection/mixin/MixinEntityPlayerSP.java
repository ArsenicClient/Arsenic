package dev.kino.injection.mixin;

import com.mojang.authlib.GameProfile;
import dev.kino.event.impl.EventTick;
import dev.kino.event.impl.EventUpdate;
import dev.kino.main.Kino;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
        Kino.getInstance().getEventManager().post(event);

        posX = event.getX();
        posY = event.getY();
        posZ = event.getZ();

        onGround = event.isOnGround();

        rotationYaw = event.getYaw();
        rotationPitch = event.getPitch();
    }

    @Inject(method = "onUpdate", at = @At("HEAD"))
    private void onUpdate(CallbackInfo ci) {
        Kino.getInstance().getEventManager().post(new EventTick());
    }

    @Inject(method = "onUpdateWalkingPlayer", at = @At("RETURN"))
    private void onUpdateWalkingPlayerPost(CallbackInfo ci) {
        posX = cachedX;
        posY = cachedY;
        posZ = cachedZ;

        onGround = cachedOnGround;

        rotationYaw = cachedRotationYaw;
        rotationPitch = cachedRotationPitch;

        Kino.getInstance().getEventManager().post(new EventUpdate.Post(posX, posY, posZ, rotationYaw, rotationPitch, onGround));
    }

}
