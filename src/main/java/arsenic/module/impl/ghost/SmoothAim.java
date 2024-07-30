package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventSilentRotation;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.TargetManager;
import arsenic.module.property.PropertyInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.rotations.RotationUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;

@ModuleInfo(name = "SmoothAim", category = ModuleCategory.GHOST)
public class SmoothAim extends Module {

    public final EnumProperty<aaMode> mode = new EnumProperty<>("Mode: ", aaMode.Silent);
    @PropertyInfo(reliesOn = "Mode: ", value = "Silent")
    public final BooleanProperty movementFix = new BooleanProperty("MovementFix", true);
    public final BooleanProperty clickOnly = new BooleanProperty("ClickOnly",true);
    public final BooleanProperty fovBased = new BooleanProperty("FovBased",true);
    public final BooleanProperty pitchAssist = new BooleanProperty("PitchAssist",true);
    public final BooleanProperty breakBlocks = new BooleanProperty("Break Blocks",false);
    public final RangeProperty speed = new RangeProperty("speed", new RangeValue(1, 100, 20, 50,1));

    @RequiresPlayer
    @EventLink
    public Listener<EventSilentRotation> eventSilentRotationListener = event -> {
        if(mc.currentScreen != null) return;
        if (clickOnly.getValue() && !mc.gameSettings.keyBindAttack.isKeyDown()) return;
        if (breakBlocks.getValue() && (mc.objectMouseOver != null)) {
            BlockPos blockPos = mc.objectMouseOver.getBlockPos();
            if (blockPos != null) {
                Block block = mc.theWorld.getBlockState(blockPos).getBlock();
                if ((block != Blocks.air) && !(block instanceof BlockLiquid) && (block != null))
                    return;
            }
        }
        EntityAndRots target = getTargetAndRotations();
        if(target == null) return;
        double fov = RotationUtils.fovFromEntity(target.entity);
        double rotSpeed = speed.getValue().getRandomInRange() * (fovBased.getValue() ? (Math.abs(fov) * 2 / 180) : 1);

        if (mode.getValue().equals(aaMode.Silent)) {
            event.setDoMovementFix(movementFix.getValue());
            event.setJumpFix(movementFix.getValue());
            event.setSpeed((float) rotSpeed);
            event.setYaw(target.yaw);
            event.setPitch(target.pitch);
        } else {
            float[] rots = RotationUtils.getPatchedAndCappedRots(
                    new float[]{mc.thePlayer.prevRotationYaw,mc.thePlayer.prevRotationPitch},
                    new float[]{target.yaw,target.pitch},
                    (float) rotSpeed
            );
            mc.thePlayer.rotationYaw = rots[0];
            mc.thePlayer.rotationPitch = rots[1];
        }
    };

    private EntityAndRots getTargetAndRotations() {
        EntityAndRots target = new EntityAndRots();
        target.entity = TargetManager.getTarget();
        if (target.entity == null) return null;
        float[] rotationsToTarget = RotationUtils.getRotationsToEntity((EntityLivingBase) target.entity);
        target.yaw = rotationsToTarget[0];
        target.pitch = (float) ((pitchAssist.getValue() ? rotationsToTarget[1] : mc.thePlayer.rotationPitch) + Math.random() - Math.random());
        return target;
    }

    private static class EntityAndRots {
        public Entity entity;
        public float yaw, pitch;
    }

    public enum aaMode {
        Silent,
        Normal
    }
}
