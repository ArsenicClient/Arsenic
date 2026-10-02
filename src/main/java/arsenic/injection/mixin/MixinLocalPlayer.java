package arsenic.injection.mixin;

import arsenic.event.impl.EventLiving;
import arsenic.event.impl.EventMouse;
import arsenic.event.impl.EventTick;
import arsenic.event.impl.EventUpdate;
import arsenic.main.Arsenic;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static arsenic.main.MinecraftAPI.mouseDownLastTick;

@Mixin(value = LocalPlayer.class, priority = 1111)
public abstract class MixinLocalPlayer extends AbstractClientPlayer {

    @Unique private double arsenic$cachedX, arsenic$cachedY, arsenic$cachedZ;
    @Unique private boolean arsenic$cachedOnGround;
    @Unique private float arsenic$cachedYaw, arsenic$cachedPitch;
    @Unique private boolean arsenic$updateApplied;

    public MixinLocalPlayer(ClientLevel level, GameProfile gameProfile) {
        super(level, gameProfile);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void arsenic$tickHead(CallbackInfo ci) {
        Arsenic.getInstance().getEventManager().post(new EventTick());

        for (int i = 0; i < 3; i++) {
            boolean down = arsenic.utils.io.Keys.isMouseDown(i);
            if (down && !mouseDownLastTick[i]) {
                mouseDownLastTick[i] = true;
                Arsenic.getArsenic().getEventManager().post(new EventMouse.Down(i));
            } else if (!down && mouseDownLastTick[i]) {
                mouseDownLastTick[i] = false;
                Arsenic.getArsenic().getEventManager().post(new EventMouse.Up(i));
            }
        }
    }

    @Inject(method = "raycastHitResult", at = @At("HEAD"))
    private void arsenic$pickStart(float a, net.minecraft.world.entity.Entity cameraEntity, CallbackInfoReturnable<net.minecraft.world.phys.HitResult> cir) {
        arsenic.main.MinecraftAPI.picking = true;
    }

    @Inject(method = "raycastHitResult", at = @At("RETURN"))
    private void arsenic$pickEnd(float a, net.minecraft.world.entity.Entity cameraEntity, CallbackInfoReturnable<net.minecraft.world.phys.HitResult> cir) {
        arsenic.main.MinecraftAPI.picking = false;
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void arsenic$tickReturn(CallbackInfo ci) {
        Arsenic.getInstance().getEventManager().post(new EventTick.Post());
    }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void arsenic$aiStep(CallbackInfo ci) {
        Arsenic.getInstance().getEventManager().post(new EventLiving());
    }

    /**
     * 1.8's onUpdateWalkingPlayer. Listeners may rewrite what gets reported to the server; the real
     * state is put back on RETURN so only the outgoing movement packet sees the change.
     */
    @Inject(method = "sendPosition", at = @At("HEAD"), cancellable = true)
    private void arsenic$sendPositionHead(CallbackInfo ci) {
        arsenic$cachedX = getX();
        arsenic$cachedY = getY();
        arsenic$cachedZ = getZ();
        arsenic$cachedOnGround = onGround();
        arsenic$cachedYaw = getYRot();
        arsenic$cachedPitch = getXRot();

        EventUpdate event = new EventUpdate.Pre(getX(), getY(), getZ(), getYRot(), getXRot(), onGround());
        Arsenic.getInstance().getEventManager().post(event);
        if (event.isCancelled()) {
            arsenic$updateApplied = false;
            ci.cancel();
            return;
        }

        arsenic$updateApplied = true;
        setPosRaw(event.getX(), event.getY(), event.getZ());
        setOnGround(event.isOnGround());
        setYRot(event.getYaw());
        setXRot(event.getPitch());
    }

    @Inject(method = "sendPosition", at = @At("RETURN"))
    private void arsenic$sendPositionReturn(CallbackInfo ci) {
        if (!arsenic$updateApplied)
            return;
        arsenic$updateApplied = false;
        setPosRaw(arsenic$cachedX, arsenic$cachedY, arsenic$cachedZ);
        setOnGround(arsenic$cachedOnGround);
        setYRot(arsenic$cachedYaw);
        setXRot(arsenic$cachedPitch);
        Arsenic.getInstance().getEventManager()
                .post(new EventUpdate.Post(getX(), getY(), getZ(), getYRot(), getXRot(), onGround()));
    }
}
