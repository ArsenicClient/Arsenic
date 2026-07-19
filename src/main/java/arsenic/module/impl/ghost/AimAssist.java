package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventSilentRotation;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.TargetManager;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.rotations.RotationUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;

import static net.minecraft.util.MathHelper.wrapAngleTo180_float;

@ModuleInfo(name = "AimAssist", category = ModuleCategory.GHOST)
public class AimAssist extends Module {

    public final DoubleProperty speed = new DoubleProperty("Speed", new DoubleValue(1, 50, 10, 1));
    public final EnumProperty<aMode> mode = new EnumProperty<>("Mode:", aMode.Additive);
    private float yawDelta, pitchDelta;
    private boolean active;
    private EntityLivingBase target;

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> eventTickListener = event -> {
        if (!mc.gameSettings.keyBindAttack.isKeyDown()) {
            clearTarget();
            return;
        }

        target = TargetManager.getTarget();
        if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            BlockPos p = mc.objectMouseOver.getBlockPos();
            if (p != null) {
                Block bl = mc.theWorld.getBlockState(p).getBlock();
                if (!(bl instanceof BlockLiquid)) {
                    clearTarget();
                    return;
                }
            }
        }

        if (target == null) {
            clearTarget();
            return;
        }

        float[] rotationsToTarget = RotationUtils.getRotationsToEntity(target);
        if (mode.getValue() == aMode.Silent) {
            event.setSpeed((float) speed.getValue().getInput());
            event.setYaw((float) (rotationsToTarget[0] + Math.random() - Math.random()));
            event.setPitch((float) (rotationsToTarget[1] + Math.random() - Math.random()));
            active = false;
            return;
        }

        yawDelta = getYawDelta((float) (rotationsToTarget[0] + Math.random() - Math.random()));
        pitchDelta = -getPitchDelta((float) (rotationsToTarget[1] + Math.random() - Math.random()));
        active = true;
    };


    private float getYawDelta(float targetYaw) {
        float delta = wrapAngleTo180_float(wrapAngleTo180_float(targetYaw) - wrapAngleTo180_float(mc.thePlayer.rotationYaw));
        float speedValue = (float) (speed.getValue().getInput() * ((Math.sin(Math.toRadians(Math.abs(delta)))/2.0f) + 0.5f));
        return Math.min(speedValue, Math.abs(delta)) * Math.signum(delta);
    }

    private float getPitchDelta(float targetPitch) {
        float delta = targetPitch - mc.thePlayer.rotationPitch;
        float speedValue = (float) (speed.getValue().getInput() * ((Math.sin(Math.toRadians(Math.abs(delta)))/2.0f) + 0.5f));
        return Math.min(speedValue, Math.abs(delta)) * Math.signum(delta);
    }

    private void clearTarget() {
        pitchDelta = 0;
        yawDelta = 0;
        this.active = false;
        this.target = null;
    }

    public float modifyYaw(float yaw) {
        if (mode.getValue() == aMode.Silent || target == null) {
            return yaw;
        } if(mode.getValue() == aMode.Normal) {
            return yawDelta;
        } else if(mode.getValue() == aMode.Additive) {
            return yawDelta + yaw;
        } else if (mode.getValue() == aMode.Adaptive) {
            float correctDir = Math.signum(yawDelta);
            if (correctDir == 0 || yaw == 0) {
                return yaw;
            }
            float strength = (float) (speed.getValue().getInput() / 10.0f);
            if (Math.signum(yaw) == correctDir) {
                return yaw * (1.0f + strength);
            }
            return yaw * (1.0f - Math.min(strength, 1.0f));
        }
        return yaw;
    }

    public float modifyPitch(float pitch) {
        if (mode.getValue() == aMode.Silent || target == null) {
            return pitch;
        } else if(mode.getValue() == aMode.Normal) {
            return pitchDelta;
        }  else if(mode.getValue() == aMode.Additive) {
            return pitchDelta + pitch;
        } else if (mode.getValue() == aMode.Adaptive) {
            float correctDir = Math.signum(pitchDelta);
            if (correctDir == 0 || pitch == 0) {
                return pitch;
            }
            float strength = (float) (speed.getValue().getInput() / 10.0f);
            if (Math.signum(pitch) == correctDir) {
                return pitch * (1.0f + strength);
            }
            return pitch * (1.0f - Math.min(strength, 1.0f));
        }
        return pitch;
    }

    public enum aMode {
        Silent,
        Normal,
        Additive,
        Adaptive;
    }

}
