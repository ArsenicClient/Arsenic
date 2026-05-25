package arsenic.module.impl.world;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.event.impl.EventUpdate;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.MoveUtil;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.minecraft.ScaffoldUtil;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.rotations.RotationUtils;
import ibxm.Player;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.input.Keyboard;

import static arsenic.utils.rotations.RotationUtils.clamp;
import static arsenic.utils.rotations.RotationUtils.patchGCD;

@ModuleInfo(name = "Scaffold", category = ModuleCategory.PLAYER)
public class Scaffold extends Module {

    //scaffold variables
    private BlockData blockData;
    private BlockData lastBlockData;
    private float[] rots;


    @Override
    protected void onEnable() {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
        blockData = null;
        lastBlockData = null;
        super.onEnable();
    }


    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> eventSilentRotationListener = event -> {
        blockData = ScaffoldUtil.getBlockData();

        if(blockData != null && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK && mc.objectMouseOver.sideHit == blockData.facing) {
            if (mc.thePlayer.inventory.getCurrentItem() == null || !(mc.thePlayer.inventory.getCurrentItem().getItem() instanceof ItemBlock)) {
                mc.thePlayer.inventory.currentItem = ScaffoldUtil.getBlockSlot();
            } else {
                place();
            }
            event.setYaw(mc.thePlayer.rotationYaw + 180f);
            event.setPitch(rots[1]);
            return;
        }

        if (blockData != null) {
            lastBlockData = blockData;
            rots = getRotationsForFace(blockData.getPosition(), blockData.getFacing());
        }

        if (lastBlockData == null) {
            rots = new float[]{mc.thePlayer.rotationYaw + 180, 75};
        }

        event.setYaw(mc.thePlayer.rotationYaw + 180f);
        event.setPitch(rots[1]);
        event.setSpeed(360f);
        if (mc.thePlayer.inventory.getCurrentItem() == null || !(mc.thePlayer.inventory.getCurrentItem().getItem() instanceof ItemBlock)) {
            mc.thePlayer.inventory.currentItem = ScaffoldUtil.getBlockSlot();
        } else {
            place();
        }
    };

    @EventLink
    public final Listener<EventRenderWorldLast> renderWorldLast = event -> {
        double offsetX = -0.1 * Math.sin(Math.toRadians(MoveUtil.getDirection()));
        double offsetZ = -0.1 * Math.cos(Math.toRadians(MoveUtil.getDirection()));

        BlockPos renderPos = blockData != null ? blockData.getPosition() : new BlockPos(
                mc.thePlayer.posX + offsetX,
                mc.thePlayer.posY - 1,
                mc.thePlayer.posZ + offsetZ
        );

        RenderUtils.renderBlock(renderPos, Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor(), true, false);
    };

    private void place() {
        blockData = ScaffoldUtil.getBlockData();
        if (blockData == null) {
            return;
        }
        MovingObjectPosition objectOver = mc.objectMouseOver;
        BlockPos blockpos = mc.objectMouseOver.getBlockPos();
        if (objectOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || mc.theWorld.getBlockState(blockpos).getBlock().getMaterial() == Material.air) {
            return;
        }

        mc.playerController.onPlayerRightClick(
                mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getCurrentItem(),
                blockData.position, blockData.facing, ScaffoldUtil.getNewVector(blockData)
        );

        mc.thePlayer.swingItem();
    }

    public boolean willFallNextTick() {
        EntityPlayerSP player = mc.thePlayer;
        if (!player.onGround)
            return true;

        double nextX = player.posX + player.motionX * 1.2;
        double nextZ = player.posZ + player.motionZ * 1.2;
        double nextY = player.posY + player.motionY * 1.2;

        double halfWidth = 0.29;

        double[][] checkPoints = {
                {nextX,             nextZ},
                {nextX + halfWidth, nextZ + halfWidth},
                {nextX - halfWidth, nextZ + halfWidth},
                {nextX + halfWidth, nextZ - halfWidth},
                {nextX - halfWidth, nextZ - halfWidth},
        };

        for (double[] point : checkPoints) {
            BlockPos groundPos = new BlockPos(
                    MathHelper.floor_double(point[0]),
                    MathHelper.floor_double(nextY) - 1, // block directly beneath feet
                    MathHelper.floor_double(point[1])
            );

            Block block = mc.theWorld.getBlockState(groundPos).getBlock();

            // If any corner has a solid block beneath it, player won't fall
            if (block.getMaterial() != Material.air) {
                return false;
            }
        }

        return true;
    }

    /**
     * Locks yaw to playerYaw+180 and finds the pitch(es) that cause the ray
     * to hit the given block face, returning the one closest to the current
     * silent rotation pitch.
     *
     * Ray origin: player eye position
     * Ray direction from yaw/pitch:
     *   dx = -sin(yaw) * cos(pitch)
     *   dy = -sin(pitch)
     *   dz =  cos(yaw) * cos(pitch)
     *
     * For each face we solve: origin + t*dir = point on face plane,
     * check the point is within face bounds, then recover pitch from dy/t.
     */
    public static float[] getRotationsForFace(BlockPos blockPos, EnumFacing facing) {
        EntityPlayerSP player = mc.thePlayer;

        // Eye position
        double eyeX = player.posX;
        double eyeY = player.posY + player.getEyeHeight();
        double eyeZ = player.posZ;

        // Locked yaw
        float lockedYaw = player.rotationYaw + 180f;
        float yawRad = (float) Math.toRadians(lockedYaw);

        // Horizontal direction components (constant regardless of pitch)
        // Minecraft uses: dx = -sin(yaw)*cos(pitch), dz = cos(yaw)*cos(pitch)
        // We factor out cos(pitch): hx = -sin(yaw), hz = cos(yaw)
        double hx = -Math.sin(yawRad);
        double hz =  Math.cos(yawRad);

        // Block bounds
        double bx0 = blockPos.getX(),      bx1 = bx0 + 1.0;
        double by0 = blockPos.getY(),      by1 = by0 + 1.0;
        double bz0 = blockPos.getZ(),      bz1 = bz0 + 1.0;

        // Current silent pitch (to pick the closest valid pitch)
        float currentPitch = Arsenic.getArsenic().getSilentRotationManager().pitch;

        float bestPitch = Float.MAX_VALUE;
        float bestDiff  = Float.MAX_VALUE;

        // Candidate pitches collected from face intersection
        // For a given face, the plane is axis-aligned. Parameterise the ray as:
        //   P(t) = eye + t * (hx*cos(p), -sin(p), hz*cos(p))
        // We solve for t from the fixed-axis plane, then recover pitch from
        // the y-component: -sin(p) = (hitY - eyeY) / t  =>  p = asin(...)

        switch (facing) {
            case UP: {
                // Face plane: y = by1 (top face, player places block below them)
                // t * (-sin(p)) = by1 - eyeY  — but we need a second equation.
                // Instead use horizontal distance: t * cos(p) from x or z plane.
                // Easier: solve t from x: t = (hitX - eyeX) / (hx * cos(p))
                // This is circular. Use the face center as a stable sample point.
                // For each candidate, check using the face center.
                float pitch = pitchToHitPoint(eyeX, eyeY, eyeZ,
                        hx, hz,
                        bx0 + 0.5, by1, bz0 + 0.5);
                if (!Float.isNaN(pitch)) {
                    float diff = Math.abs(MathHelper.wrapAngleTo180_float(pitch - currentPitch));
                    if (diff < bestDiff) { bestDiff = diff; bestPitch = pitch; }
                }
                // Also try corners to get multiple candidates
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
                // Face plane: z = bz0
                // Solve t: eyeZ + t * hz * cos(p) = bz0
                // Use face center x/y as targets
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
                // Face plane: x = bx0
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

        // Fallback: if we found no valid pitch, use face-center direct calculation
        if (bestPitch == Float.MAX_VALUE) {
            double faceCX = blockPos.getX() + 0.5 + facing.getFrontOffsetX() * 0.5;
            double faceCY = blockPos.getY() + 0.5 + facing.getFrontOffsetY() * 0.5;
            double faceCZ = blockPos.getZ() + 0.5 + facing.getFrontOffsetZ() * 0.5;
            bestPitch = pitchToHitPoint(eyeX, eyeY, eyeZ, hx, hz, faceCX, faceCY, faceCZ);
            if (Float.isNaN(bestPitch)) bestPitch = 75f;
        }

        bestPitch = MathHelper.clamp_float(bestPitch, -90f, 90f);

        float[] lastRots = new float[]{lockedYaw, currentPitch};
        float[] targetRots = new float[]{lockedYaw, bestPitch};
        float[] fixedRots = patchGCD(lastRots, targetRots);
        // Keep yaw locked
        fixedRots[0] = lockedYaw;
        return fixedRots;
    }

    /**
     * Given a target 3D point, compute the pitch needed to look at it
     * from the eye position, using the locked horizontal direction (hx, hz).
     *
     * The ray is: eye + t*(hx*cos(p), -sin(p), hz*cos(p))
     * We find t from horizontal displacement and then solve for pitch.
     *
     * Returns NaN if the point isn't reachable from this yaw direction.
     */
    private static float pitchToHitPoint(double eyeX, double eyeY, double eyeZ,
                                         double hx, double hz,
                                         double targetX, double targetY, double targetZ) {
        double dx = targetX - eyeX;
        double dy = targetY - eyeY;
        double dz = targetZ - eyeZ;

        // Horizontal distance along our locked direction
        // t * cos(p) = horizontal scalar, so:
        // dx = hx * t * cos(p)  =>  t * cos(p) = dx / hx  (if hx != 0)
        // dz = hz * t * cos(p)  =>  t * cos(p) = dz / hz  (if hz != 0)
        double tCosp;
        if (Math.abs(hx) > Math.abs(hz)) {
            if (Math.abs(hx) < 1e-6) return Float.NaN;
            tCosp = dx / hx;
        } else {
            if (Math.abs(hz) < 1e-6) return Float.NaN;
            tCosp = dz / hz;
        }

        if (tCosp <= 0) return Float.NaN; // target is behind us

        // dy = -sin(p) * t = -sin(p) * (tCosp / cos(p)) * cos(p) ... simplify:
        // Actually: t = tCosp / cos(p), and dy = -sin(p) * t
        // So: dy / tCosp = -sin(p) / cos(p) ... wait, let's be careful.
        //
        // tCosp = t * cos(p)
        // dy = -sin(p) * t = -tan(p) * (t * cos(p)) = -tan(p) * tCosp
        // => tan(p) = -dy / tCosp
        // => p = atan(-dy / tCosp)  [in Minecraft, positive pitch = looking down]

        double tanPitch = -dy / tCosp;
        float pitch = (float) Math.toDegrees(Math.atan(tanPitch));
        return pitch;
    }

    /**
     * Sample multiple points on a Z-plane face and return valid pitches.
     */
    private static float[] pitchesToHitZPlane(double eyeX, double eyeY, double eyeZ,
                                              double hx, double hz,
                                              double faceZ,
                                              double xMin, double xMax,
                                              double yMin, double yMax) {
        // Check reachability: ray must go in the right z direction
        if (Math.abs(hz) < 1e-6) return new float[0];

        // t * cos(p) such that z-hit = faceZ:
        // eyeZ + hz * t * cos(p) = faceZ  =>  t*cos(p) = (faceZ - eyeZ) / hz
        double tCosp = (faceZ - eyeZ) / hz;
        if (tCosp <= 0) return new float[0];

        // Sample the face at center and 4 corners (clamped inward slightly)
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
            // Check x is in range: hitX = eyeX + hx * tCosp
            double hitX = eyeX + hx * tCosp;
            if (hitX < xMin || hitX > xMax) continue;

            double tanPitch = -dy / tCosp;
            results[count++] = (float) Math.toDegrees(Math.atan(tanPitch));
        }

        float[] trimmed = new float[count];
        System.arraycopy(results, 0, trimmed, 0, count);
        return trimmed;
    }

    /**
     * Sample multiple points on an X-plane face and return valid pitches.
     */
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

        public EnumFacing getFacing() {
            return facing;
        }

        public BlockPos getPosition() {
            return position;
        }
    }
}