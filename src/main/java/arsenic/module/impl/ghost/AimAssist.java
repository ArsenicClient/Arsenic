package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventSilentRotation;
import arsenic.injection.accessor.IMixinRenderManager;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.TargetManager;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.rotations.RotationUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.*;

import static net.minecraft.util.MathHelper.wrapAngleTo180_float;

@ModuleInfo(name = "AimAssist", category = ModuleCategory.GHOST)
public class AimAssist extends Module {

    public final DoubleProperty speed = new DoubleProperty("Speed", new DoubleValue(1, 50, 10, 1));
    public final EnumProperty<aMode> mode = new EnumProperty<>("Mode:", aMode.Silent);

    // Customisation options
    public final DoubleProperty fov = new DoubleProperty("FOV", new DoubleValue(10, 360, 90, 1));
    public final DoubleProperty distance = new DoubleProperty("Distance", new DoubleValue(1, 10, 4.5, 0.1));
    public final BooleanProperty clickAim = new BooleanProperty("Click Aim", false);
    public final BooleanProperty weaponOnly = new BooleanProperty("Weapon Only", false);
    public final BooleanProperty breakBlocks = new BooleanProperty("Break Blocks", true);
    public final BooleanProperty aimPitch = new BooleanProperty("Aim Pitch", false);
    public final DoubleProperty pitchOffset = new DoubleProperty("Pitch Offset", new DoubleValue(-2, 2, 4, 0.05));

    private float prevPartialTicks, yawDelta, pitchDelta;
    private EntityLivingBase target;

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> eventTickListener = event -> {
        if (clickAim.getValue() && !mc.gameSettings.keyBindAttack.isKeyDown()) {
            setNullRots();
            return;
        }

        if (weaponOnly.getValue() && !PlayerUtils.isPlayerHoldingWeapon()) {
            setNullRots();
            return;
        }

        if (breakBlocks.getValue() && mc.objectMouseOver != null) {
            BlockPos p = mc.objectMouseOver.getBlockPos();
            if (p != null) {
                Block bl = mc.theWorld.getBlockState(p).getBlock();
                if (bl != Blocks.air && !(bl instanceof BlockLiquid)) {
                    setNullRots();
                    return;
                }
            }
        }

        target = getTarget();
        if (target == null) {
            setNullRots();
            return;
        }
        float[] rotationsToTarget = RotationUtils.getRotationsToEntity(target);
        rotationsToTarget[1] += (float) pitchOffset.getValue().getInput();

        if (mode.getValue() == aMode.Silent) {
            event.setSpeed((float) speed.getValue().getInput());
            event.setYaw((float) (rotationsToTarget[0] + Math.random() - Math.random()));
            if (aimPitch.getValue()) {
                event.setPitch((float) (rotationsToTarget[1] + Math.random() - Math.random()));
            }
            return;
        }
        yawDelta = getYawDelta((float) (rotationsToTarget[0] + Math.random() - Math.random()));
        if (aimPitch.getValue()) {
            pitchDelta = getPitchDelta((float) (rotationsToTarget[1] + Math.random() - Math.random()));
        }
        prevPartialTicks = 0;
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> eventGameLoopListener = event -> {
        if (target != null)
            drawTargetShader(event);
        if (mode.getValue() == aMode.Silent || (yawDelta == 0 && pitchDelta == 0))
            return;

        float partialTicksElapsed = event.partialTicks - prevPartialTicks;

        float newYaw = mc.thePlayer.rotationYaw + (yawDelta * partialTicksElapsed);
        float newPitch = mc.thePlayer.rotationPitch + (pitchDelta * partialTicksElapsed);

        newPitch = MathHelper.clamp_float(newPitch, -90.0f, 90.0f);

        float[] rotations = RotationUtils.patchGCD(
                new float[]{mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch},
                new float[]{newYaw, newPitch}
        );

        mc.thePlayer.rotationYaw = rotations[0];
        mc.thePlayer.rotationPitch = rotations[1];

        prevPartialTicks = event.partialTicks;
    };

    private EntityLivingBase getTarget() {
        EntityPlayer target = TargetManager.getTarget();
        if (target == null) return null;
        if (mc.thePlayer.getDistanceToEntity(target) > distance.getValue().getInput()) return null;
        if (!PlayerUtils.withinFov(target, (float) fov.getValue().getInput())) return null;
        return target;
    }

    private float getYawDelta(float targetYaw) {
        float delta = wrapAngleTo180_float(wrapAngleTo180_float(targetYaw) - wrapAngleTo180_float(mc.thePlayer.rotationYaw));
        float speedValue = (float) (speed.getValue().getInput() * ((Math.sin(Math.toRadians(Math.abs(delta))) / 2.0f) + 0.5f));
        return Math.min(speedValue, Math.abs(delta)) * Math.signum(delta);
    }

    private float getPitchDelta(float targetPitch) {
        float delta = targetPitch - mc.thePlayer.rotationPitch;
        float speedValue = (float) (speed.getValue().getInput() * ((Math.sin(Math.toRadians(Math.abs(delta))) / 2.0f) + 0.5f));
        return Math.min(speedValue, Math.abs(delta)) * Math.signum(delta);
    }

    private void setNullRots() {
        this.yawDelta = 0;
        this.pitchDelta = 0;
    }

    public enum aMode {
        Silent,
        Normal;
    }

    private void drawTargetShader(EventRenderWorldLast event) {
        IMixinRenderManager renderManager = (IMixinRenderManager) mc.getRenderManager();
        double x = (target.lastTickPosX + (target.posX - target.lastTickPosX) * event.partialTicks) - renderManager.getRenderPosX();
        double y = (target.lastTickPosY + (target.posY - target.lastTickPosY) * event.partialTicks) - renderManager.getRenderPosY();
        double z = (target.lastTickPosZ + (target.posZ - target.lastTickPosZ) * event.partialTicks) - renderManager.getRenderPosZ();
        AxisAlignedBB axisalignedbb = target.getEntityBoundingBox();
        AxisAlignedBB axisalignedbb1 = new AxisAlignedBB(axisalignedbb.minX - target.posX + x, axisalignedbb.minY - target.posY + y, axisalignedbb.minZ - target.posZ + z, axisalignedbb.maxX - target.posX + x, axisalignedbb.maxY - target.posY + y, axisalignedbb.maxZ - target.posZ + z);
        Color color = target.hurtTime > 0 ? new Color(210, 43, 43, 100) : new Color(249, 246, 238);
        GlStateManager.pushMatrix();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDepthMask(false);
        GL11.glLineWidth(2.0F);
        RenderUtils.drawShadedBoundingBox(axisalignedbb1, color.getRed(), color.getGreen(), color.getBlue(), 63);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDepthMask(true);
        GL11.glLineWidth(1.0F);
        GlStateManager.popMatrix();
    }

}