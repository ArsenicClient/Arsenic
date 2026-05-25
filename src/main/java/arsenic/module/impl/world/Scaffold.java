package arsenic.module.impl.world;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.*;
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
import net.minecraft.block.BlockAir;
import net.minecraft.block.material.Material;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static arsenic.utils.rotations.RotationUtils.clamp;
import static arsenic.utils.rotations.RotationUtils.patchGCD;

@ModuleInfo(name = "Scaffold", category = ModuleCategory.PLAYER)
public class Scaffold extends Module {

    // scaffold variables
    private BlockData blockData;
    private BlockData lastBlockData;
    private float[] rots;

    // debug toggle
    private final BooleanProperty debug = new BooleanProperty("Debug", true);

    // --- debug state populated during rotation event, consumed during render ---
    private static class DebugState {
        // All sampled candidate points on the block face (shown as red dots)
        final List<Vec3> candidatePoints = new ArrayList<>();
        // The winning point that produced bestPitch (shown as green dot)
        Vec3 winnerPoint = null;
        // Eye position at time of calculation
        Vec3 eyePos = null;
        // The block being targeted
        BlockPos targetBlock = null;
        EnumFacing targetFacing = null;
        // Pitches
        float currentPitch = 0f;
        float targetPitch = 0f;
        // Flags
        boolean hadBlockData = false;
        boolean mouseOverMatched = false;
        boolean hasValidItem = false;
        boolean placeAttempted = false;
        // objectMouseOver info
        MovingObjectPosition.MovingObjectType mouseOverType = null;
        EnumFacing mouseOverFace = null;
    }

    private final DebugState debugState = new DebugState();

    @Override
    protected void onEnable() {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
        blockData = null;
        lastBlockData = null;
        super.onEnable();
    }

    public static Scaffold.BlockData getBlockData() {
        EntityPlayerSP player = mc.thePlayer;

        // Where the player is about to step (slightly ahead based on motion)
        double nextX = player.posX + player.motionX;
        double nextZ = player.posZ + player.motionZ;

        // Check a small footprint around the next position and current position
        double[][] checkOffsets = {
                {0,           0          },  // directly below current pos - always check first
                {player.motionX * 1.5, player.motionZ * 1.5},  // ahead
                {player.motionX,       player.motionZ      },   // half ahead
                {player.motionX * 2.0, player.motionZ * 2.0},   // further ahead
                // diagonals
                {player.motionX + 0.3,  player.motionZ      },
                {player.motionX - 0.3,  player.motionZ      },
                {player.motionX,        player.motionZ + 0.3},
                {player.motionX,        player.motionZ - 0.3},
        };

        for (double[] offset : checkOffsets) {
            for (int dy = 0; dy >= -2; dy--) {  // check current Y and up to 2 below
                BlockPos targetPos = new BlockPos(
                        MathHelper.floor_double(player.posX + offset[0]),
                        MathHelper.floor_double(player.posY) + dy - 1,  // -1 = foot level
                        MathHelper.floor_double(player.posZ + offset[1])
                );

                // This position must be air (where we want to place)
                if (!(mc.theWorld.getBlockState(targetPos).getBlock() instanceof BlockAir)) {
                    continue;
                }

                // Find a solid neighbour to place against
                // Prioritise DOWN (place on top of block below), then sides
                EnumFacing[] prioritised = {
                        EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST
                };

                for (EnumFacing direction : prioritised) {
                    BlockPos neighbour = targetPos.offset(direction);
                    Block neighbourBlock = mc.theWorld.getBlockState(neighbour).getBlock();
                    Material material = neighbourBlock.getMaterial();

                    if (material.isSolid() && !material.isLiquid()) {
                        // Make sure the neighbour isn't further than reach distance
                        double distSq = player.getDistanceSqToCenter(neighbour);
                        if (distSq > 16.0) continue;  // ~4 block reach

                        return new Scaffold.BlockData(neighbour, direction.getOpposite());
                    }
                }
            }
        }

        return null;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> eventSilentRotationListener = event -> {
        blockData = getBlockData();

        // --- populate debug state ---
        if (debug.getValue()) {
            debugState.candidatePoints.clear();
            debugState.winnerPoint = null;
            debugState.hadBlockData = blockData != null;
            debugState.hasValidItem = mc.thePlayer.inventory.getCurrentItem() != null
                    && mc.thePlayer.inventory.getCurrentItem().getItem() instanceof ItemBlock;
            debugState.currentPitch = Arsenic.getArsenic().getSilentRotationManager().pitch;
            debugState.mouseOverType = mc.objectMouseOver.typeOfHit;
            debugState.mouseOverFace = mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                    ? mc.objectMouseOver.sideHit : null;
            debugState.eyePos = new Vec3(mc.thePlayer.posX,
                    mc.thePlayer.posY + mc.thePlayer.getEyeHeight(),
                    mc.thePlayer.posZ);
            if (blockData != null) {
                debugState.targetBlock = blockData.getPosition();
                debugState.targetFacing = blockData.getFacing();
                debugState.mouseOverMatched =
                        mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                                && mc.objectMouseOver.sideHit == blockData.facing;
            } else {
                debugState.targetBlock = null;
                debugState.targetFacing = null;
                debugState.mouseOverMatched = false;
            }
        }

        if (blockData != null && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && mc.objectMouseOver.sideHit == blockData.facing) {
            if (mc.thePlayer.inventory.getCurrentItem() == null
                    || !(mc.thePlayer.inventory.getCurrentItem().getItem() instanceof ItemBlock)) {
                mc.thePlayer.inventory.currentItem = ScaffoldUtil.getBlockSlot();
            } else {
                if (debug.getValue()) debugState.placeAttempted = true;
                place();
            }
            event.setYaw(mc.thePlayer.rotationYaw + 180f);
            event.setPitch(rots[1]);
            return;
        }

        // Only recalculate rotations when the player is about to fall
        if (blockData != null && willFallNextTick()) {
            lastBlockData = blockData;
            rots = getRotationsForFace(blockData.getPosition(), blockData.getFacing(), debug.getValue() ? debugState : null);
        }
        // else: rots stays as the last computed value, keeping the silent rotation frozen

        if (rots == null) {
            rots = new float[]{mc.thePlayer.rotationYaw + 180, 75};
        }

        if (debug.getValue()) {
            debugState.targetPitch = rots[1];
        }

        event.setYaw(mc.thePlayer.rotationYaw + 180f);
        event.setPitch(rots[1]);
        event.setSpeed(360f);
        if (mc.thePlayer.inventory.getCurrentItem() == null
                || !(mc.thePlayer.inventory.getCurrentItem().getItem() instanceof ItemBlock)) {
            mc.thePlayer.inventory.currentItem = ScaffoldUtil.getBlockSlot();
        } else {
            if (debug.getValue()) debugState.placeAttempted = true;
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

        RenderUtils.renderBlock(renderPos,
                Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor(),
                true, false);

        if (debug.getValue()) {
            renderDebug(event);
        }
    };

    private void renderDebug(EventRenderWorldLast event) {
        if (debugState.eyePos == null) return;

        double ix = mc.getRenderManager().viewerPosX;
        double iy = mc.getRenderManager().viewerPosY;
        double iz = mc.getRenderManager().viewerPosZ;

        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(2.0f);

        // 1. Draw red dots for each sampled candidate point
        for (Vec3 pt : debugState.candidatePoints) {
            drawDot(pt.xCoord - ix, pt.yCoord - iy, pt.zCoord - iz,
                    1.0f, 0.0f, 0.0f, 0.85f, 0.05f);
        }

        // 2. Draw green dot for the winning point
        if (debugState.winnerPoint != null) {
            Vec3 w = debugState.winnerPoint;
            drawDot(w.xCoord - ix, w.yCoord - iy, w.zCoord - iz,
                    0.0f, 1.0f, 0.0f, 1.0f, 0.07f);

            // 3. Draw yellow line from eye to winner
            Vec3 eye = debugState.eyePos;
            GL11.glBegin(GL11.GL_LINES);
            GL11.glColor4f(1.0f, 1.0f, 0.0f, 0.8f);
            GL11.glVertex3d(eye.xCoord - ix, eye.yCoord - iy, eye.zCoord - iz);
            GL11.glVertex3d(w.xCoord - ix, w.yCoord - iy, w.zCoord - iz);
            GL11.glEnd();
        }

        // 4. Draw the actual aim ray using current silent pitch (cyan line, 5 blocks long)
        {
            Vec3 eye = debugState.eyePos;
            float yawRad   = (float) Math.toRadians(mc.thePlayer.rotationYaw + 180f);
            float pitchRad = (float) Math.toRadians(debugState.currentPitch);
            double dx = -Math.sin(yawRad) * Math.cos(pitchRad) * 5.0;
            double dy = -Math.sin(pitchRad) * 5.0;
            double dz =  Math.cos(yawRad)  * Math.cos(pitchRad) * 5.0;
            GL11.glBegin(GL11.GL_LINES);
            GL11.glColor4f(0.0f, 1.0f, 1.0f, 0.9f);
            GL11.glVertex3d(eye.xCoord - ix, eye.yCoord - iy, eye.zCoord - iz);
            GL11.glVertex3d(eye.xCoord - ix + dx, eye.yCoord - iy + dy, eye.zCoord - iz + dz);
            GL11.glEnd();
        }

        // 5. Draw orange outline on the target block face (if we have one)
        if (debugState.targetBlock != null && debugState.targetFacing != null) {
            drawFaceOutline(debugState.targetBlock, debugState.targetFacing, ix, iy, iz,
                    debugState.mouseOverMatched ? new float[]{0f, 1f, 0f, 1f}   // green if matched
                            : new float[]{1f, 0.5f, 0f, 1f}); // orange if not
        }

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventRender2D> onRender2D = event -> {
        renderDebugHud();
    };

    /** Draws a small filled square "dot" in world space at the given offset coords. */
    private void drawDot(double x, double y, double z, float r, float g, float b, float a, double size) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(r, g, b, a);
        GL11.glVertex3d(x - size, y - size, z);
        GL11.glVertex3d(x + size, y - size, z);
        GL11.glVertex3d(x + size, y + size, z);
        GL11.glVertex3d(x - size, y + size, z);
        GL11.glEnd();
        // Also draw in XZ plane for visibility
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(r, g, b, a);
        GL11.glVertex3d(x - size, y, z - size);
        GL11.glVertex3d(x + size, y, z - size);
        GL11.glVertex3d(x + size, y, z + size);
        GL11.glVertex3d(x - size, y, z + size);
        GL11.glEnd();
    }

    /** Draws a wireframe outline on one face of a block. */
    private void drawFaceOutline(BlockPos pos, EnumFacing face, double ix, double iy, double iz, float[] color) {
        double x0 = pos.getX() - ix;
        double y0 = pos.getY() - iy;
        double z0 = pos.getZ() - iz;
        double x1 = x0 + 1, y1 = y0 + 1, z1 = z0 + 1;
        double e = 0.002; // slight expansion to avoid z-fight

        GL11.glColor4f(color[0], color[1], color[2], color[3]);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        switch (face) {
            case UP:
                GL11.glVertex3d(x0-e, y1+e, z0-e); GL11.glVertex3d(x1+e, y1+e, z0-e);
                GL11.glVertex3d(x1+e, y1+e, z1+e); GL11.glVertex3d(x0-e, y1+e, z1+e);
                break;
            case DOWN:
                GL11.glVertex3d(x0-e, y0-e, z0-e); GL11.glVertex3d(x1+e, y0-e, z0-e);
                GL11.glVertex3d(x1+e, y0-e, z1+e); GL11.glVertex3d(x0-e, y0-e, z1+e);
                break;
            case NORTH:
                GL11.glVertex3d(x0-e, y0-e, z0-e); GL11.glVertex3d(x1+e, y0-e, z0-e);
                GL11.glVertex3d(x1+e, y1+e, z0-e); GL11.glVertex3d(x0-e, y1+e, z0-e);
                break;
            case SOUTH:
                GL11.glVertex3d(x0-e, y0-e, z1+e); GL11.glVertex3d(x1+e, y0-e, z1+e);
                GL11.glVertex3d(x1+e, y1+e, z1+e); GL11.glVertex3d(x0-e, y1+e, z1+e);
                break;
            case WEST:
                GL11.glVertex3d(x0-e, y0-e, z0-e); GL11.glVertex3d(x0-e, y0-e, z1+e);
                GL11.glVertex3d(x0-e, y1+e, z1+e); GL11.glVertex3d(x0-e, y1+e, z0-e);
                break;
            case EAST:
                GL11.glVertex3d(x1+e, y0-e, z0-e); GL11.glVertex3d(x1+e, y0-e, z1+e);
                GL11.glVertex3d(x1+e, y1+e, z1+e); GL11.glVertex3d(x1+e, y1+e, z0-e);
                break;
        }
        GL11.glEnd();
    }

    /** Renders a 2D debug HUD in the top-left corner showing key state. */
    private void renderDebugHud() {
        // Use Minecraft's font renderer via ScaledResolution
        net.minecraft.client.gui.ScaledResolution sr =
                new net.minecraft.client.gui.ScaledResolution(mc);

        int x = 4, y = 4;
        int lineH = 10;

        String[] lines = {
                "§e[Scaffold Debug]",
                "§7blockData: "    + (debugState.hadBlockData   ? "§aYES" : "§cNO"),
                "§7hasBlock item: " + (debugState.hasValidItem  ? "§aYES" : "§cNO"),
                "§7placeAttempted: "+ (debugState.placeAttempted? "§aYES" : "§cNO"),
                "§7mouseOver type: §f" + (debugState.mouseOverType != null ? debugState.mouseOverType.name() : "null"),
                "§7mouseOver face: §f" + (debugState.mouseOverFace != null ? debugState.mouseOverFace.name() : "null"),
                "§7target facing:  §f" + (debugState.targetFacing != null ? debugState.targetFacing.name() : "null"),
                "§7matched: "      + (debugState.mouseOverMatched ? "§aYES" : "§cNO"),
                "§7candidates: §f" + debugState.candidatePoints.size(),
                "§7currentPitch: §f" + String.format("%.2f", debugState.currentPitch),
                "§7targetPitch:  §f" + String.format("%.2f", debugState.targetPitch),
                "§7winner: "       + (debugState.winnerPoint != null ? "§aFOUND" : "§cNONE"),
                "§b--- Legend ---",
                "§c● Red = sampled pts",
                "§a● Green = winning pt",
                "§eYellow = aim→winner",
                "§bCyan = actual ray",
        };

        // Draw dark background
        mc.ingameGUI.drawRect(x - 2, y - 2,
                x + 130, y + lines.length * lineH + 2,
                0xAA000000);

        for (String line : lines) {
            mc.fontRendererObj.drawStringWithShadow(line, x, y, 0xFFFFFF);
            y += lineH;
        }
    }

    private void place() {
        blockData = ScaffoldUtil.getBlockData();
        if (blockData == null) {
            return;
        }
        MovingObjectPosition objectOver = mc.objectMouseOver;
        BlockPos blockpos = mc.objectMouseOver.getBlockPos();
        if (objectOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || mc.theWorld.getBlockState(blockpos).getBlock().getMaterial() == Material.air) {
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
                    MathHelper.floor_double(nextY) - 1,
                    MathHelper.floor_double(point[1])
            );

            Block block = mc.theWorld.getBlockState(groundPos).getBlock();
            if (block.getMaterial() != Material.air) {
                return false;
            }
        }

        return true;
    }

    /**
     * Same as before but now accepts a nullable DebugState to populate candidate/winner points.
     */
    public static float[] getRotationsForFace(BlockPos blockPos, EnumFacing facing) {
        return getRotationsForFace(blockPos, facing, null);
    }

    public static float[] getRotationsForFace(BlockPos blockPos, EnumFacing facing, DebugState ds) {
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
        Vec3  bestPoint = null;

        switch (facing) {
            case UP: {
                float pitch = pitchToHitPoint(eyeX, eyeY, eyeZ, hx, hz, bx0 + 0.5, by1, bz0 + 0.5);
                if (!Float.isNaN(pitch)) {
                    Vec3 pt = new Vec3(bx0 + 0.5, by1, bz0 + 0.5);
                    if (ds != null) ds.candidatePoints.add(pt);
                    float diff = Math.abs(MathHelper.wrapAngleTo180_float(pitch - currentPitch));
                    if (diff < bestDiff) { bestDiff = diff; bestPitch = pitch; bestPoint = pt; }
                }
                for (double cx : new double[]{bx0 + 0.1, bx1 - 0.1}) {
                    for (double cz : new double[]{bz0 + 0.1, bz1 - 0.1}) {
                        float p = pitchToHitPoint(eyeX, eyeY, eyeZ, hx, hz, cx, by1, cz);
                        if (!Float.isNaN(p)) {
                            Vec3 pt = new Vec3(cx, by1, cz);
                            if (ds != null) ds.candidatePoints.add(pt);
                            float diff = Math.abs(MathHelper.wrapAngleTo180_float(p - currentPitch));
                            if (diff < bestDiff) { bestDiff = diff; bestPitch = p; bestPoint = pt; }
                        }
                    }
                }
                break;
            }
            case DOWN: {
                float pitch = pitchToHitPoint(eyeX, eyeY, eyeZ, hx, hz, bx0 + 0.5, by0, bz0 + 0.5);
                if (!Float.isNaN(pitch)) {
                    Vec3 pt = new Vec3(bx0 + 0.5, by0, bz0 + 0.5);
                    if (ds != null) ds.candidatePoints.add(pt);
                    float diff = Math.abs(MathHelper.wrapAngleTo180_float(pitch - currentPitch));
                    if (diff < bestDiff) { bestDiff = diff; bestPitch = pitch; bestPoint = pt; }
                }
                break;
            }
            case NORTH: {
                float[] candidates = pitchesToHitZPlane(eyeX, eyeY, eyeZ, hx, hz,
                        bz0, bx0, bx1, by0, by1, ds, bz0);
                for (int i = 0; i < candidates.length; i++) {
                    float p = candidates[i];
                    if (!Float.isNaN(p)) {
                        float diff = Math.abs(MathHelper.wrapAngleTo180_float(p - currentPitch));
                        if (diff < bestDiff) {
                            bestDiff = diff; bestPitch = p;
                            if (ds != null && i < ds.candidatePoints.size())
                                bestPoint = ds.candidatePoints.get(ds.candidatePoints.size() - candidates.length + i);
                        }
                    }
                }
                break;
            }
            case SOUTH: {
                float[] candidates = pitchesToHitZPlane(eyeX, eyeY, eyeZ, hx, hz,
                        bz1, bx0, bx1, by0, by1, ds, bz1);
                for (int i = 0; i < candidates.length; i++) {
                    float p = candidates[i];
                    if (!Float.isNaN(p)) {
                        float diff = Math.abs(MathHelper.wrapAngleTo180_float(p - currentPitch));
                        if (diff < bestDiff) {
                            bestDiff = diff; bestPitch = p;
                            if (ds != null && i < ds.candidatePoints.size())
                                bestPoint = ds.candidatePoints.get(ds.candidatePoints.size() - candidates.length + i);
                        }
                    }
                }
                break;
            }
            case WEST: {
                float[] candidates = pitchesToHitXPlane(eyeX, eyeY, eyeZ, hx, hz,
                        bx0, by0, by1, bz0, bz1, ds, bx0);
                for (int i = 0; i < candidates.length; i++) {
                    float p = candidates[i];
                    if (!Float.isNaN(p)) {
                        float diff = Math.abs(MathHelper.wrapAngleTo180_float(p - currentPitch));
                        if (diff < bestDiff) {
                            bestDiff = diff; bestPitch = p;
                            if (ds != null && i < ds.candidatePoints.size())
                                bestPoint = ds.candidatePoints.get(ds.candidatePoints.size() - candidates.length + i);
                        }
                    }
                }
                break;
            }
            case EAST: {
                float[] candidates = pitchesToHitXPlane(eyeX, eyeY, eyeZ, hx, hz,
                        bx1, by0, by1, bz0, bz1, ds, bx1);
                for (int i = 0; i < candidates.length; i++) {
                    float p = candidates[i];
                    if (!Float.isNaN(p)) {
                        float diff = Math.abs(MathHelper.wrapAngleTo180_float(p - currentPitch));
                        if (diff < bestDiff) {
                            bestDiff = diff; bestPitch = p;
                            if (ds != null && i < ds.candidatePoints.size())
                                bestPoint = ds.candidatePoints.get(ds.candidatePoints.size() - candidates.length + i);
                        }
                    }
                }
                break;
            }
        }

        if (bestPitch == Float.MAX_VALUE) {
            double faceCX = blockPos.getX() + 0.5 + facing.getFrontOffsetX() * 0.5;
            double faceCY = blockPos.getY() + 0.5 + facing.getFrontOffsetY() * 0.5;
            double faceCZ = blockPos.getZ() + 0.5 + facing.getFrontOffsetZ() * 0.5;
            bestPitch = pitchToHitPoint(eyeX, eyeY, eyeZ, hx, hz, faceCX, faceCY, faceCZ);
            bestPoint = new Vec3(faceCX, faceCY, faceCZ);
            if (Float.isNaN(bestPitch)) bestPitch = 75f;
        }

        if (ds != null) ds.winnerPoint = bestPoint;

        bestPitch = MathHelper.clamp_float(bestPitch, -90f, 90f);

        float[] lastRots = new float[]{lockedYaw, currentPitch};
        float[] targetRots = new float[]{lockedYaw, bestPitch};
        float[] fixedRots = patchGCD(lastRots, targetRots);
        fixedRots[0] = lockedYaw;
        return fixedRots;
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
        float pitch = (float) Math.toDegrees(Math.atan(tanPitch));
        return pitch;
    }

    // --- Updated plane samplers that also record candidate points into DebugState ---

    private static float[] pitchesToHitZPlane(double eyeX, double eyeY, double eyeZ,
                                              double hx, double hz,
                                              double faceZ,
                                              double xMin, double xMax,
                                              double yMin, double yMax,
                                              DebugState ds, double recordZ) {
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

            if (ds != null) ds.candidatePoints.add(new Vec3(hitX, sy, recordZ));
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
                                              double zMin, double zMax,
                                              DebugState ds, double recordX) {
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

            if (ds != null) ds.candidatePoints.add(new Vec3(recordX, sy, hitZ));
            double tanPitch = -dy / tCosp;
            results[count++] = (float) Math.toDegrees(Math.atan(tanPitch));
        }

        float[] trimmed = new float[count];
        System.arraycopy(results, 0, trimmed, 0, count);
        return trimmed;
    }

    // Keep the old no-debug overloads for the non-debug path
    private static float[] pitchesToHitZPlane(double eyeX, double eyeY, double eyeZ,
                                              double hx, double hz, double faceZ,
                                              double xMin, double xMax, double yMin, double yMax) {
        return pitchesToHitZPlane(eyeX, eyeY, eyeZ, hx, hz, faceZ, xMin, xMax, yMin, yMax, null, faceZ);
    }

    private static float[] pitchesToHitXPlane(double eyeX, double eyeY, double eyeZ,
                                              double hx, double hz, double faceX,
                                              double yMin, double yMax, double zMin, double zMax) {
        return pitchesToHitXPlane(eyeX, eyeY, eyeZ, hx, hz, faceX, yMin, yMax, zMin, zMax, null, faceX);
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
}