package arsenic.injection.mixin;

import arsenic.event.impl.*;
import arsenic.module.impl.blatant.NoSlow;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemSword;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.World;
import com.mojang.authlib.GameProfile;

import arsenic.main.Arsenic;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;

import static arsenic.main.MinecraftAPI.mouseDownLastTick;

@Mixin(priority = 1111, value = EntityPlayerSP.class)
public abstract class MixinEntityPlayerSP extends AbstractClientPlayer {

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
    @Inject(method = "onLivingUpdate", at = @At("HEAD"))
    public void onLivingUpdate(CallbackInfo ci) {
        Arsenic.getInstance().getEventManager().post(new EventLiving());
    }
    @Redirect(method = "onLivingUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/EntityPlayerSP;isUsingItem()Z"))
    private boolean noSlowMixin(EntityPlayerSP instance) {
        NoSlow noSlow = Arsenic.getInstance().getModuleManager().getModuleByClass(NoSlow.class);

        if (noSlow.isEnabled() && Minecraft.getMinecraft().thePlayer.getCurrentEquippedItem() != null) {
            if (Minecraft.getMinecraft().thePlayer.getCurrentEquippedItem().getItem() instanceof ItemSword && noSlow.swordNoSlow.getValue()) {
                return false;
            }
            if (Minecraft.getMinecraft().thePlayer.getCurrentEquippedItem().getItem() instanceof ItemFood && noSlow.foodNoSlow.getValue()) {
                return false;
            }
            if (Minecraft.getMinecraft().thePlayer.getCurrentEquippedItem().getItem() instanceof ItemPotion && noSlow.potionNoSlow.getValue()) {
                return false;
            }
            if (Minecraft.getMinecraft().thePlayer.getCurrentEquippedItem().getItem() instanceof ItemBow && noSlow.bowNoSlow.getValue()) {
                return false;
            }
        }

        return instance.isUsingItem();
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

}
