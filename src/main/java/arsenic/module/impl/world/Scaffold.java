package arsenic.module.impl.world;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.*;
import arsenic.injection.accessor.IMixinEntity;
import arsenic.injection.accessor.IMixinEntityPlayerSP;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.minecraft.MoveUtil;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.minecraft.ScaffoldUtil;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.rotations.RotationUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.*;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import static arsenic.utils.minecraft.ScaffoldUtil.getPredictedBoundingBox;
import static arsenic.utils.minecraft.ScaffoldUtil.willFallNextTick;
import static arsenic.utils.rotations.RotationUtils.patchGCD;

@ModuleInfo(name = "Scaffold", category = ModuleCategory.PLAYER)
public class Scaffold extends Module {

    private BlockData blockData;
    private float[] rots = new float[2];
    private boolean solvedRots;

    @Override
    protected void onEnable() {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
        blockData = null;
        super.onEnable();
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> eventSilentRotationListener = event -> {
        boolean wilLFall = ScaffoldUtil.willFallNextTick() && mc.thePlayer.onGround;
        blockData = findBestPlacement();
        event.setSpeed(360f);

        //if the player can place a block without moving rots
        event.setYaw(mc.thePlayer.rotationYaw + 180f);
        event.setPitch(rots[1]);
        MovingObjectPosition movingObjectPosition = rayTrace(event);
        if (movingObjectPosition.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && (movingObjectPosition.sideHit != EnumFacing.UP && movingObjectPosition.sideHit != EnumFacing.DOWN)) {
            place(event);
            return;
        }

        if(wilLFall) {
            if (blockData != null) {
                float[] solved = getRotationsForFace(blockData.getPosition(), blockData.getFacing());
                if (solved != null) {
                    rots = solved;
                    solvedRots = true;
                } else {
                    solvedRots = false;
                    //PlayerUtils.addWaterMarkedMessageToChat("Unsolved rots");
                    rots = getFreeRotationsForFace(blockData.getPosition(), blockData.getFacing());
                }
            }
            event.setYaw(solvedRots ? mc.thePlayer.rotationYaw + 180f : rots[0]);
            event.setPitch(rots[1]);
            place(event);
        } else {
            event.setYaw(mc.thePlayer.rotationYaw + 180f);
            event.setPitch(rots[1]);
        }
    };

    @EventLink
    public final Listener<EventRenderWorldLast> renderWorldLast = event -> {
        if(blockData == null) {
            return;
        }
        RenderUtils.renderBlock(blockData.getPosition(), Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor(),
                true, false);
        RenderUtils.renderBlockFace(blockData.getPosition(), blockData.facing, Arsenic.getArsenic().getThemeManager().getCurrentTheme().getBlack(),
                true, true);
    };

    public static BlockData findBestPlacement() {
        EntityPlayerSP player = mc.thePlayer;
        BlockPos playerPos = new BlockPos(player);

        // Scan 3x3 radius, 1 block below player
        BlockPos scanY = playerPos.down();

        BlockData best = null;
        double bestDist = Double.MAX_VALUE;

        Vec3 eyes = player.getPositionEyes(1.0f);

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos pos = scanY.add(x, 0, z);
                IBlockState state = mc.theWorld.getBlockState(pos);

                // Skip air and non-solid blocks
                if (state.getBlock() == Blocks.air) continue;
                if (!state.getBlock().isFullCube()) continue;

                // Check all 6 faces
                for (EnumFacing facing : EnumFacing.HORIZONTALS) {
                    BlockPos neighbor = pos.offset(facing);
                    IBlockState neighborState = mc.theWorld.getBlockState(neighbor);

                    // Face must be exposed (neighbor must be air/passable)
                    if (neighborState.getBlock() != Blocks.air) continue;

                    // The clickable point on this face (center of the face)
                    double faceX = pos.getX() + 0.5 + facing.getFrontOffsetX() * 0.5;
                    double faceY = pos.getY() + 0.5 + facing.getFrontOffsetY() * 0.5;
                    double faceZ = pos.getZ() + 0.5 + facing.getFrontOffsetZ() * 0.5;

                    double dist = eyes.distanceTo(new Vec3(faceX, faceY, faceZ));

                    if (dist < bestDist) {
                        bestDist = dist;
                        best = new BlockData(pos, facing);
                    }
                }
            }
        }

        return best; // null if no valid placement found
    }


    public MovingObjectPosition rayTrace(EventSilentRotation event) {
        Vec3 vec3 = mc.thePlayer.getPositionEyes(1);
        Vec3 vec31 = ((IMixinEntity) mc.thePlayer).invokeGetVectorForRotation(event.getPitch(), event.getYaw());
        Vec3 vec32 = vec3.addVector(vec31.xCoord * 4.5, vec31.yCoord * 4.5, vec31.zCoord * 4.5);
        return mc.thePlayer.worldObj.rayTraceBlocks(vec3, vec32, false, false, true);
    }



    private void place(EventSilentRotation event) {
        if (mc.thePlayer.inventory.getCurrentItem() == null
                || !(mc.thePlayer.inventory.getCurrentItem().getItem() instanceof ItemBlock)) {
            mc.thePlayer.inventory.currentItem = ScaffoldUtil.getBlockSlot();
        }
        if (blockData == null) {
            return;
        }
        MovingObjectPosition objectOver = rayTrace(event);
        BlockPos blockpos = objectOver.getBlockPos();
        if (objectOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || mc.theWorld.getBlockState(blockpos).getBlock().getMaterial() == Material.air) {
            //PlayerUtils.addWaterMarkedMessageToChat("Raytrace fail");
            return;
        }

        mc.playerController.onPlayerRightClick(
                mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getCurrentItem(),
                blockData.position, blockData.facing, ScaffoldUtil.getNewVector(blockData)
        );

        mc.thePlayer.swingItem();
    }

    public static float[] getRotationsForFace(BlockPos blockPos, EnumFacing facing) {
        EntityPlayerSP player = mc.thePlayer;

        double eyeX = player.posX;
        double eyeY = player.posY + player.getEyeHeight();
        double eyeZ = player.posZ;

        float lockedYaw = player.rotationYaw + 180f;
        float yawRad = (float) Math.toRadians(lockedYaw);

        double hx = -Math.sin(yawRad);
        double hz =  Math.cos(yawRad);

        double bx0 = blockPos.getX(), bx1 = bx0 + 1.0;
        double by0 = blockPos.getY(), by1 = by0 + 1.0;
        double bz0 = blockPos.getZ(), bz1 = bz0 + 1.0;

        float currentPitch = Arsenic.getArsenic().getSilentRotationManager().pitch;

        float bestPitch = Float.MAX_VALUE;
        float bestDiff  = Float.MAX_VALUE;

        switch (facing) {
            case UP: {
                float pitch = pitchToHitPoint(eyeX, eyeY, eyeZ, hx, hz, bx0 + 0.5, by1, bz0 + 0.5);
                if (!Float.isNaN(pitch)) {
                    float diff = Math.abs(MathHelper.wrapAngleTo180_float(pitch - currentPitch));
                    if (diff < bestDiff) { bestDiff = diff; bestPitch = pitch; }
                }
                for (double cx : new double[]{bx0 + 0.1, bx1 - 0.1}) {
                    for (double cz : new double[]{bz0 + 0.1, bz1 - 0.1}) {
                        float p = pitchToHitPoint(eyeX, eyeY, eyeZ, hx, hz, cx, by1, cz);
                        if (!Float.isNaN(p)) {
                            float diff = Math.abs(MathHelper.wrapAngleTo180_float(p - currentPitch));
                            if (diff < bestDiff) { bestDiff = diff; bestPitch = p; }
                        }
                    }
                }
                break;
            }
            case DOWN: {
                float pitch = pitchToHitPoint(eyeX, eyeY, eyeZ, hx, hz, bx0 + 0.5, by0, bz0 + 0.5);
                if (!Float.isNaN(pitch)) {
                    float diff = Math.abs(MathHelper.wrapAngleTo180_float(pitch - currentPitch));
                    if (diff < bestDiff) { bestDiff = diff; bestPitch = pitch; }
                }
                break;
            }
            case NORTH: {
                float[] candidates = pitchesToHitZPlane(eyeX, eyeY, eyeZ, hx, hz,
                        bz0, bx0, bx1, by0, by1);
                for (float p : candidates) {
                    if (!Float.isNaN(p)) {
                        float diff = Math.abs(MathHelper.wrapAngleTo180_float(p - currentPitch));
                        if (diff < bestDiff) { bestDiff = diff; bestPitch = p; }
                    }
                }
                break;
            }
            case SOUTH: {
                float[] candidates = pitchesToHitZPlane(eyeX, eyeY, eyeZ, hx, hz,
                        bz1, bx0, bx1, by0, by1);
                for (float p : candidates) {
                    if (!Float.isNaN(p)) {
                        float diff = Math.abs(MathHelper.wrapAngleTo180_float(p - currentPitch));
                        if (diff < bestDiff) { bestDiff = diff; bestPitch = p; }
                    }
                }
                break;
            }
            case WEST: {
                float[] candidates = pitchesToHitXPlane(eyeX, eyeY, eyeZ, hx, hz,
                        bx0, by0, by1, bz0, bz1);
                for (float p : candidates) {
                    if (!Float.isNaN(p)) {
                        float diff = Math.abs(MathHelper.wrapAngleTo180_float(p - currentPitch));
                        if (diff < bestDiff) { bestDiff = diff; bestPitch = p; }
                    }
                }
                break;
            }
            case EAST: {
                float[] candidates = pitchesToHitXPlane(eyeX, eyeY, eyeZ, hx, hz,
                        bx1, by0, by1, bz0, bz1);
                for (float p : candidates) {
                    if (!Float.isNaN(p)) {
                        float diff = Math.abs(MathHelper.wrapAngleTo180_float(p - currentPitch));
                        if (diff < bestDiff) { bestDiff = diff; bestPitch = p; }
                    }
                }
                break;
            }
        }

        if (bestPitch == Float.MAX_VALUE) {
            return null;  // locked yaw can't hit this face
        }

        bestPitch = MathHelper.clamp_float(bestPitch, -90f, 90f);

        float[] lastRots = new float[]{lockedYaw, currentPitch};
        float[] targetRots = new float[]{lockedYaw, bestPitch};
        float[] fixedRots = patchGCD(lastRots, targetRots);
        fixedRots[0] = lockedYaw;
        return fixedRots;
    }

    public static float[] getFreeRotationsForFace(BlockPos blockPos, EnumFacing facing) {
        EntityPlayerSP player = mc.thePlayer;

        double eyeX = player.posX;
        double eyeY = player.posY + player.getEyeHeight();
        double eyeZ = player.posZ;

        double faceCX = blockPos.getX() + 0.5 + facing.getFrontOffsetX() * 0.5;
        double faceCY = blockPos.getY() + 0.5 + facing.getFrontOffsetY() * 0.5;
        double faceCZ = blockPos.getZ() + 0.5 + facing.getFrontOffsetZ() * 0.5;

        double dx = faceCX - eyeX;
        double dy = faceCY - eyeY;
        double dz = faceCZ - eyeZ;

        float yaw   = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));

        pitch = MathHelper.clamp_float(pitch, -90f, 90f);

        float currentYaw   = Arsenic.getArsenic().getSilentRotationManager().yaw;
        float currentPitch = Arsenic.getArsenic().getSilentRotationManager().pitch;

        float[] lastRots   = new float[]{currentYaw, currentPitch};
        float[] targetRots = new float[]{yaw, pitch};
        return patchGCD(lastRots, targetRots);
    }


    private static float pitchToHitPoint(double eyeX, double eyeY, double eyeZ,
                                         double hx, double hz,
                                         double targetX, double targetY, double targetZ) {
        double dx = targetX - eyeX;
        double dy = targetY - eyeY;
        double dz = targetZ - eyeZ;

        double tCosp;
        if (Math.abs(hx) > Math.abs(hz)) {
            if (Math.abs(hx) < 1e-6) return Float.NaN;
            tCosp = dx / hx;
        } else {
            if (Math.abs(hz) < 1e-6) return Float.NaN;
            tCosp = dz / hz;
        }

        if (tCosp <= 0) return Float.NaN;

        double tanPitch = -dy / tCosp;
        return (float) Math.toDegrees(Math.atan(tanPitch));
    }

    private static float[] pitchesToHitZPlane(double eyeX, double eyeY, double eyeZ,
                                              double hx, double hz,
                                              double faceZ,
                                              double xMin, double xMax,
                                              double yMin, double yMax) {
        if (Math.abs(hz) < 1e-6) return new float[0];

        double tCosp = (faceZ - eyeZ) / hz;
        if (tCosp <= 0) return new float[0];

        double[] sampleY = {
                yMin + 0.1,
                yMin + (yMax - yMin) * 0.2,
                yMin + (yMax - yMin) * 0.3,
                yMin + (yMax - yMin) * 0.4,
                (yMin + yMax) * 0.5,
                yMin + (yMax - yMin) * 0.6,
                yMin + (yMax - yMin) * 0.7,
                yMin + (yMax - yMin) * 0.8,
                yMin + (yMax - yMin) * 0.9,
                yMax - 0.1
        };

        float[] results = new float[sampleY.length];
        int count = 0;
        for (double sy : sampleY) {
            double dy = sy - eyeY;
            double hitX = eyeX + hx * tCosp;
            if (hitX < xMin || hitX > xMax) continue;

            double tanPitch = -dy / tCosp;
            results[count++] = (float) Math.toDegrees(Math.atan(tanPitch));
        }

        float[] trimmed = new float[count];
        System.arraycopy(results, 0, trimmed, 0, count);
        return trimmed;
    }

    private static float[] pitchesToHitXPlane(double eyeX, double eyeY, double eyeZ,
                                              double hx, double hz,
                                              double faceX,
                                              double yMin, double yMax,
                                              double zMin, double zMax) {
        if (Math.abs(hx) < 1e-6) return new float[0];

        double tCosp = (faceX - eyeX) / hx;
        if (tCosp <= 0) return new float[0];

        double[] sampleY = {
                yMin + 0.1,
                yMin + (yMax - yMin) * 0.2,
                yMin + (yMax - yMin) * 0.3,
                yMin + (yMax - yMin) * 0.4,
                (yMin + yMax) * 0.5,
                yMin + (yMax - yMin) * 0.6,
                yMin + (yMax - yMin) * 0.7,
                yMin + (yMax - yMin) * 0.8,
                yMin + (yMax - yMin) * 0.9,
                yMax - 0.1
        };

        float[] results = new float[sampleY.length];
        int count = 0;
        for (double sy : sampleY) {
            double dy = sy - eyeY;
            double hitZ = eyeZ + hz * tCosp;
            if (hitZ < zMin || hitZ > zMax) continue;

            double tanPitch = -dy / tCosp;
            results[count++] = (float) Math.toDegrees(Math.atan(tanPitch));
        }

        float[] trimmed = new float[count];
        System.arraycopy(results, 0, trimmed, 0, count);
        return trimmed;
    }

    public static class BlockData {
        private BlockPos position;
        private EnumFacing facing;

        public BlockData(final BlockPos position, final EnumFacing facing) {
            this.position = position;
            this.facing = facing;
        }

        public EnumFacing getFacing() { return facing; }
        public BlockPos getPosition() { return position; }
    }

    public static BlockData getBlockData() {
        EntityPlayerSP player = mc.thePlayer;

        double[][] checkOffsets = {
                {0,           0          },
                {player.motionX, player.motionZ},
                {player.motionX * 1.5, player.motionZ * 1.5},
                {player.motionX * 2.0, player.motionZ * 2.0},
                {Math.signum(player.motionX) * 0.3, Math.signum(player.motionZ) * 0.3},
                {Math.signum(player.motionX), Math.signum(player.motionZ) * 0.3},
                {Math.signum(player.motionX), Math.signum(player.motionZ)},
                {Math.signum(player.motionX) * 0.3, Math.signum(player.motionZ)},
        };

        for (double[] offset : checkOffsets) {
            for (int dy = 0; dy >= -2; dy--) {
                BlockPos targetPos = new BlockPos(
                        MathHelper.floor_double(player.posX + offset[0]),
                        MathHelper.floor_double(player.posY) + dy - 1,
                        MathHelper.floor_double(player.posZ + offset[1])
                );

                if (!(mc.theWorld.getBlockState(targetPos).getBlock() instanceof BlockAir)) {
                    continue;
                }

                EnumFacing[] prioritised = {
                        EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST
                };

                for (EnumFacing direction : prioritised) {
                    BlockPos neighbour = targetPos.offset(direction);
                    Block neighbourBlock = mc.theWorld.getBlockState(neighbour).getBlock();
                    Material material = neighbourBlock.getMaterial();

                    if (material.isSolid() && !material.isLiquid()) {
                        double distSq = player.getDistanceSqToCenter(neighbour);
                        if (distSq > 16.0) continue;

                        return new Scaffold.BlockData(neighbour, direction.getOpposite());
                    }
                }
            }
        }
        return null;
    }
}