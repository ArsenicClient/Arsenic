package arsenic.module.impl.world;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.*;
import arsenic.injection.accessor.IMixinMovementInputFromOptions;
import arsenic.injection.mixin.MixinEntityPlayer;
import arsenic.injection.mixin.MixinEntityPlayerSP;
import arsenic.injection.mixin.MixinMovementInputFromOptions;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Keyboard;
import arsenic.module.property.PropertyInfo;

import java.util.List;

@ModuleInfo(name = "SafeWalk", category = ModuleCategory.MOVEMENT)
public class SafeWalk extends Module {
    public final BooleanProperty onlySPressed = new BooleanProperty("Only S pressed", false);
    public final BooleanProperty onlySneak = new BooleanProperty("Only sneak", false);
    public final BooleanProperty pitchCheck = new BooleanProperty("Pitch Check", false);
    @PropertyInfo(reliesOn = "Pitch Check",value = "true")
    public final DoubleProperty pitch = new DoubleProperty("Pitch", new DoubleValue(0, 90, 45, 5));
    public final DoubleProperty precision = new DoubleProperty("Safety", new DoubleValue(1, 3, 0, 0.1));

    @RequiresPlayer
    @EventLink
    public final Listener<EventLiving> tickEvent = tickEvent -> {
        // Early exits
        if (onlySPressed.getValue() && !mc.gameSettings.keyBindBack.isKeyDown()) {
            setShift(Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode()));
            return;
        }
        if (pitchCheck.getValue() && mc.thePlayer.rotationPitch < pitch.getValue().getInput()) {
            setShift(false);
            return;
        }
        if (onlySneak.getValue() && !Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())) {
            setShift(false);
            return;
        }

        setShift(willFallNextTick());
    };

    public boolean willFallNextTick() {
        EntityPlayerSP player = mc.thePlayer;
        double motionX = player.motionX;
        double motionZ = player.motionZ;

        float moveForward = 0;
        float moveStrafe = 0;
        GameSettings gameSettings = ((IMixinMovementInputFromOptions) player.movementInput).getGameSettings();

        if (gameSettings.keyBindForward.isKeyDown()) {
            ++moveForward;
        }

        if (gameSettings.keyBindBack.isKeyDown()) {
            --moveForward;
        }

        if (gameSettings.keyBindLeft.isKeyDown()) {
            ++moveStrafe;
        }

        if (gameSettings.keyBindRight.isKeyDown()) {
            --moveStrafe;
        }

        EventMovementInput event = new EventMovementInput(moveForward, moveStrafe, gameSettings.keyBindJump.isKeyDown());
        Arsenic.getArsenic().getEventManager().post(event);
        if(event.isCancelled()) {
            moveStrafe = 0.0F;
            moveForward = 0.0F;
        } else {
            moveForward = event.getSpeed();
            moveStrafe = event.getStrafe();
        }

        motionX *= 0.98;
        motionZ *= 0.98;

        if (Math.abs(motionX) < 0.005) {
            motionX = (double)0.0F;
        }

        if (Math.abs(motionZ) < 0.005) {
            motionZ = (double)0.0F;
        }

        moveStrafe *= 0.98F;
        moveForward *= 0.98F;

        float f4 = 0.91F;
        if (player.onGround) {
            f4 = player.worldObj.getBlockState(new BlockPos(MathHelper.floor_double(player.posX), MathHelper.floor_double(player.getEntityBoundingBox().minY) - 1, MathHelper.floor_double(player.posZ))).getBlock().slipperiness * 0.91F;
        }

        float f = 0.16277136F / (f4 * f4 * f4);
        float f5;
        if (player.onGround) {
            f5 = player.getAIMoveSpeed() * f;
        } else {
            f5 = player.jumpMovementFactor;
        }

        {
            f = moveStrafe * moveStrafe + moveForward * moveForward;
            if (f >= 1.0E-4F) {
                f = MathHelper.sqrt_float(f);
                if (f < 1.0F) {
                    f = 1.0F;
                }

                f = f5 / f;
                moveStrafe *= f;
                moveForward *= f;
                float f1 = MathHelper.sin(player.rotationYaw * (float) Math.PI / 180.0F);
                float f2 = MathHelper.cos(player.rotationYaw * (float) Math.PI / 180.0F);
                motionX += (double) (moveStrafe * f2 - moveForward * f1);
                motionZ += (double) (moveForward * f2 + moveStrafe * f1);
            }
        }

        f4 = 0.91F;
        if (player.onGround) {
            f4 = player.worldObj.getBlockState(new BlockPos(MathHelper.floor_double(player.posX), MathHelper.floor_double(player.getEntityBoundingBox().minY) - 1, MathHelper.floor_double(player.posZ))).getBlock().slipperiness * 0.91F;
        }

        AxisAlignedBB predictedBB = player.getEntityBoundingBox().offset(motionX * precision.getValue().getInput(), -0.05, motionZ * precision.getValue().getInput());

        return mc.theWorld.getCollidingBoundingBoxes(player, predictedBB).isEmpty();
    }

    @Override
    public void onDisable() {
        setShift(false);
    }

    private void setShift(boolean sh) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), sh);
    }
}
