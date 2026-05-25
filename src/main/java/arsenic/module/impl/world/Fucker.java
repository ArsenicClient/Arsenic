package arsenic.module.impl.world;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventLiving;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventSilentRotation;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.main.Arsenic;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.timer.MSTimer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.BlockBed;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;



@ModuleInfo(name = "Fucker", category = ModuleCategory.WORLD, dev = true)
public class Fucker extends Module {

    public enum Mode {
        Legit,
        ThroughWall
    }

    public final EnumProperty<Mode> mode = new EnumProperty<>("Mode", Mode.ThroughWall);
    public final DoubleProperty range = new DoubleProperty("Range", new DoubleValue(1, 8, 5, 0.5));
    public final DoubleProperty delay = new DoubleProperty("Delay", new DoubleValue(0, 1000, 100, 10));
    public final DoubleProperty rotationSpeed = new DoubleProperty("Rotation Speed", new DoubleValue(1, 180, 180, 5));
    public final BooleanProperty autoTool = new BooleanProperty("Auto Tool", true);
    public final BooleanProperty switchBack = new BooleanProperty("Switch Back", true);
    public final BooleanProperty swing = new BooleanProperty("Swing", true);

    private BlockPos currentTarget;
    private int previousSlot = -1;
    private final MSTimer breakTimer = new MSTimer();
    private boolean breaking;
    private float[] rotations;

    @Override
    protected void onEnable() {
        currentTarget = null;
        previousSlot = -1;
        breaking = false;
        rotations = null;
        breakTimer.reset();
    }

    @Override
    protected void onDisable() {
        resetSlot();
        resetBreaking();
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventLiving> onLiving = event -> {
        update();
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onSilentRotation = event -> {
        if (rotations == null) return;
        event.setYaw(rotations[0]);
        event.setPitch(rotations[1]);
        event.setSpeed((float) rotationSpeed.getValue().getInput());
    };

    @EventLink
    public final Listener<EventRenderWorldLast> onRenderWorld = event -> {
        if (currentTarget != null && breaking) {
            RenderUtils.renderBlock(currentTarget, Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor(), true, false);
        }
    };

    private void update() {
        if (mc.thePlayer == null || mc.theWorld == null) return;

        BlockPos nearestBed = findNearestBed();

        if (nearestBed == null) {
            resetBreaking();
            resetSlot();
            return;
        }

        BlockPos breakTarget = getBlockToBreak(nearestBed);
        if (breakTarget == null) {
            resetBreaking();
            resetSlot();
            return;
        }

        rotations = RotationUtils.getRotations(breakTarget);

        if (autoTool.getValue()) {
            int slot = PlayerUtils.getTool(mc.theWorld.getBlockState(breakTarget).getBlock());
            if (slot != -1) {
                if (previousSlot == -1) {
                    previousSlot = mc.thePlayer.inventory.currentItem;
                }
                mc.thePlayer.inventory.currentItem = slot;
            }
        }

        if (breakTimer.hasTimeElapsed((long) delay.getValue().getInput())) {
            EnumFacing facing = getFacing(breakTarget);

            if (!breaking || !breakTarget.equals(currentTarget)) {
                mc.playerController.clickBlock(breakTarget, facing);
                breaking = true;
            } else {
                mc.playerController.onPlayerDamageBlock(breakTarget, facing);
            }

            if (swing.getValue()) {
                mc.thePlayer.swingItem();
            }

            currentTarget = breakTarget;
            breakTimer.reset();
        }

        Block blockAtBed = mc.theWorld.getBlockState(nearestBed).getBlock();
        if (!(blockAtBed instanceof BlockBed)) {
            toggle();
        }
    }

    private BlockPos findNearestBed() {
        BlockPos closest = null;
        double closestDistSq = Double.MAX_VALUE;
        int r = (int) Math.ceil(range.getValue().getInput());

        int px = MathHelper.floor_double(mc.thePlayer.posX);
        int py = MathHelper.floor_double(mc.thePlayer.posY);
        int pz = MathHelper.floor_double(mc.thePlayer.posZ);

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = new BlockPos(px + x, py + y, pz + z);
                    Block block = mc.theWorld.getBlockState(pos).getBlock();
                    if (block instanceof BlockBed) {
                        double distSq = pos.distanceSq(px, py, pz);
                        if (distSq < closestDistSq) {
                            closestDistSq = distSq;
                            closest = pos;
                        }
                    }
                }
            }
        }
        return closest;
    }

    private BlockPos getBlockToBreak(BlockPos bed) {
        switch (mode.getValue()) {
            case Legit:
                return getLegitTarget(bed);
            case ThroughWall:
                return getThroughWallTarget(bed);
        }
        return bed;
    }

    private BlockPos getLegitTarget(BlockPos bed) {
        double px = mc.thePlayer.posX;
        double py = mc.thePlayer.posY + mc.thePlayer.getEyeHeight();
        double pz = mc.thePlayer.posZ;

        double bx = bed.getX() + 0.5;
        double by = bed.getY() + 0.5;
        double bz = bed.getZ() + 0.5;

        double dx = bx - px;
        double dy = by - py;
        double dz = bz - pz;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (dist < 0.1) return bed;

        dx /= dist;
        dy /= dist;
        dz /= dist;

        double step = 0.5;
        for (double d = 0; d < dist; d += step) {
            BlockPos checkPos = new BlockPos(
                    MathHelper.floor_double(px + dx * d),
                    MathHelper.floor_double(py + dy * d),
                    MathHelper.floor_double(pz + dz * d)
            );
            Block block = mc.theWorld.getBlockState(checkPos).getBlock();
            if (!(block instanceof BlockAir) && !(block instanceof BlockBed)) {
                return checkPos;
            }
        }

        return bed;
    }

    private BlockPos getThroughWallTarget(BlockPos bed) {
        return bed;
    }

    private EnumFacing getFacing(BlockPos pos) {
        double dx = mc.thePlayer.posX - (pos.getX() + 0.5);
        double dy = mc.thePlayer.posY + mc.thePlayer.getEyeHeight() - (pos.getY() + 0.5);
        double dz = mc.thePlayer.posZ - (pos.getZ() + 0.5);

        double adx = Math.abs(dx);
        double ady = Math.abs(dy);
        double adz = Math.abs(dz);

        if (adx >= ady && adx >= adz) {
            return dx > 0 ? EnumFacing.WEST : EnumFacing.EAST;
        }
        if (ady >= adx && ady >= adz) {
            return dy > 0 ? EnumFacing.DOWN : EnumFacing.UP;
        }
        return dz > 0 ? EnumFacing.NORTH : EnumFacing.SOUTH;
    }

    private void resetSlot() {
        if (previousSlot != -1 && switchBack.getValue()) {
            mc.thePlayer.inventory.currentItem = previousSlot;
            previousSlot = -1;
        }
    }

    private void resetBreaking() {
        if (breaking) {
            mc.playerController.resetBlockRemoving();
            breaking = false;
        }
        currentTarget = null;
        rotations = null;
    }
}
