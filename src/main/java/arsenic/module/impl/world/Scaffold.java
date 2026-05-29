package arsenic.module.impl.world;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.*;
import arsenic.injection.accessor.IMixinEntity;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.minecraft.ScaffoldUtil;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderUtils;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.*;
import org.lwjgl.opengl.GL11;

import static arsenic.utils.minecraft.ScaffoldUtil.willFallNextTick;
import static arsenic.utils.rotations.RotationUtils.patchGCD;

@ModuleInfo(name = "Scaffold", category = ModuleCategory.PLAYER)
public class Scaffold extends Module {


    public BooleanProperty sprint = new BooleanProperty("Sprint", false);
    public static BooleanProperty keepY = new BooleanProperty("KeepY", false);
    public BooleanProperty telly = new  BooleanProperty("Telly", false);
    private BlockData blockData;
    private float[] rots = new float[2];
    private boolean solvedRots;
    private float animatedScale;
    public static int blockCounterX = -1;
    public static int blockCounterY = -1;
    public boolean jumpFlag;
    private static ItemBlock placeholderBlock = new ItemBlock(Blocks.tnt);


    @Override
    protected void onEnable() {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
        blockData = null;
        animatedScale = 0f;
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        animatedScale = 0f;
        super.onDisable();
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> eventSilentRotationListener = event -> {
        boolean wilLFall = ScaffoldUtil.willFallNextTick() && mc.thePlayer.motionY < 0.3;
        blockData = findBestPlacement();
        Item item = keyBlock();
        event.setSpeed(360f);
        event.setPreventDuplicateLook(true);

        if(telly.getValue()){
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), mc.gameSettings.keyBindJump.isKeyDown());
            jumpFlag = false;
        }

        //if the player can place a block without moving rots
        float delta = sprint.getValue() ? 0f : 180f;
        event.setYaw(mc.thePlayer.rotationYaw + delta);
        event.setPitch(rots[1]);

        MovingObjectPosition movingObjectPosition = rayTrace(event);
        if(item instanceof ItemBlock) {
            ItemBlock itemBlock = (ItemBlock) item;
            if (movingObjectPosition.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                    && (movingObjectPosition.sideHit != EnumFacing.DOWN)
                    && (!keepY.getValue() || movingObjectPosition.sideHit != EnumFacing.UP)
                    && itemBlock.canPlaceBlockOnSide(mc.theWorld, movingObjectPosition.getBlockPos(), movingObjectPosition.sideHit, mc.thePlayer, mc.thePlayer.getHeldItem())) {
                blockData = new BlockData(movingObjectPosition.getBlockPos(), movingObjectPosition.sideHit);
                place(event);
                return;
            }
        }

        if(wilLFall && (!telly.getValue() || !mc.thePlayer.onGround)) {
            if (blockData != null) {
                float[] solved = getRotationsForFace(blockData.getPosition(), blockData.getFacing());
                if (solved != null) {
                    rots = solved;
                    solvedRots = true;
                } else {
                    solvedRots = false;
                    rots = getFreeRotationsForFace(blockData.getPosition(), blockData.getFacing());
                }
            }
            event.setYaw(solvedRots ? mc.thePlayer.rotationYaw + 180f : rots[0]);
            event.setPitch(rots[1]);
            place(event);
        } else if (wilLFall && telly.getValue() && mc.thePlayer.onGround) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), true);
        } else {
            event.setYaw(mc.thePlayer.rotationYaw + delta);
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

    @EventLink
    public final Listener<EventRender2D> onRender2D = event -> {
        int blockCount = getBlockCount();
        if (isEnabled() && blockCount > 0) {
            animatedScale = interpolate(animatedScale, 1.0f, 0.1f);
        } else if (!isEnabled() || blockCount == 0) {
            animatedScale = interpolate(animatedScale, 0.0f, 0.15f);
        }

        if (animatedScale <= 0.01f) return;

        drawBlockCounter();
    };

    @EventLink
    public final Listener<EventShader.Blur> blurListener = event -> {
        if (animatedScale <= 0.01f) return;

        FontRendererExtension<?> fr = Arsenic.getArsenic().getClickGuiScreen().getFontRenderer();
        if (fr == null) return;

        int blockCount = getBlockCount();
        String text = String.valueOf(blockCount);
        int iconSize = 16;
        int padding = 4;
        int textWidth = (int) fr.getWidth(text);
        int bw = iconSize + padding + textWidth + padding * 2;
        int bh = iconSize + padding * 2;

        ScaledResolution sr = new ScaledResolution(mc);
        int x = blockCounterX;
        int y = blockCounterY;
        if (x == -1) x = (sr.getScaledWidth() - bw) / 2;
        if (y == -1) y = sr.getScaledHeight() - 40 - bh;

        GL11.glPushMatrix();
        GL11.glTranslated(x + bw / 2.0, y + bh / 2.0, 0);
        GL11.glScalef(animatedScale, animatedScale, 1.0f);
        GL11.glTranslated(-(x + bw / 2.0), -(y + bh / 2.0), 0);

        Gui.drawRect(x, y, x + bw, y + bh, -1);

        GL11.glPopMatrix();
    };

    private int getBlockCount() {
        if (mc.thePlayer == null) return 0;
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.thePlayer.inventory.mainInventory[i];
            if (stack != null && stack.getItem() instanceof ItemBlock && stack.stackSize > 0) {
                count += stack.stackSize;
            }
        }
        return count;
    }

    private void drawBlockCounter() {
        int blockCount = getBlockCount();
        if (blockCount == 0 && animatedScale < 0.01f) return;

        float alpha = Math.min(1f, animatedScale);
        FontRendererExtension<?> fr = Arsenic.getArsenic().getClickGuiScreen().getFontRenderer();
        if (fr == null) return;

        String text = String.valueOf(blockCount);
        int iconSize = 16;
        int padding = 4;
        int textWidth = (int) fr.getWidth(text);
        int bw = iconSize + padding + textWidth + padding * 2;
        int bh = iconSize + padding * 2;

        ScaledResolution sr = new ScaledResolution(mc);
        int x = blockCounterX;
        int y = blockCounterY;
        if (x == -1) x = (sr.getScaledWidth() - bw) / 2;
        if (y == -1) y = sr.getScaledHeight() - 40 - bh;

        GL11.glPushMatrix();
        GL11.glTranslated(x + bw / 2.0, y + bh / 2.0, 0);
        GL11.glScalef(animatedScale, animatedScale, 1.0f);
        GL11.glTranslated(-(x + bw / 2.0), -(y + bh / 2.0), 0);

        int bgColor = new Color(26, 26, 26, (int)(alpha * 128)).getRGB();
        DrawUtils.drawRoundedRect(x, y, x + bw, y + bh, 6, bgColor);

        int borderColor = (int)(alpha * 0xFF) << 24 | getThemeColor();
        DrawUtils.drawBorderedRoundedRect(x, y, x + bw, y + bh, 6, 1.5f, borderColor, 0x00000000);

        GL11.glColor4f(1, 1, 1, alpha);
        ItemStack stack = mc.thePlayer.inventory.getCurrentItem();
        if (stack != null && stack.getItem() instanceof ItemBlock) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(0, 0, 1);
            RenderHelper.enableGUIStandardItemLighting();
            mc.getRenderItem().renderItemIntoGUI(stack, x + padding, y + padding);
            RenderHelper.disableStandardItemLighting();
            GlStateManager.popMatrix();
        }

        fr.drawStringWithShadow(text, x + padding + iconSize + padding, y + (bh - fr.getHeight(text)) / 2f, (int)(alpha * 0xFF) << 24 | 0xFFFFFF);

        GL11.glPopMatrix();
    }

    private int getThemeColor() {
        return Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor();
    }

    private float interpolate(float current, float target, float speed) {
        return current + (target - current) * speed;
    }

    public static BlockData findBestPlacement() {
        EntityPlayerSP player = mc.thePlayer;
        BlockPos playerPos = new BlockPos(player);
        BlockPos scanY = playerPos.down();

        BlockData best = null;
        double bestDist = Double.MAX_VALUE;

        Vec3 eyes = player.getPositionEyes(1.0f);

        // Player AABB center for proximity checks
        double playerCX = player.posX;
        double playerCY = player.posY + player.height / 2.0;
        double playerCZ = player.posZ;

        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                BlockPos pos = scanY.add(x, 0, z);
                IBlockState state = mc.theWorld.getBlockState(pos);

                if (state.getBlock() == Blocks.air) continue;
                if (!state.getBlock().isFullCube()) continue;

                List<EnumFacing> facings = new ArrayList<>(Arrays.asList(EnumFacing.HORIZONTALS));
                if (!player.onGround && mc.thePlayer.motionY < 0 && !keepY.getValue()) {
                    facings.add(EnumFacing.UP);
                }

                for (EnumFacing facing : facings) {
                    if (!placeholderBlock.canPlaceBlockOnSide(mc.theWorld, pos, facing, mc.thePlayer, mc.thePlayer.getHeldItem()))
                        continue;

                    BlockPos neighbor = pos.offset(facing);
                    IBlockState neighborState = mc.theWorld.getBlockState(neighbor);

                    if (neighborState.getBlock() != Blocks.air) continue;

                    // --- FILTER 1: skip if placing here is redundant ---
                    // "redundant" = there's already a solid block between the player and this placement
                    // i.e. the neighbor (new block pos) is farther from the player than an existing block
                    // that already covers the same column. Simpler check: if there's already a solid block
                    // at playerPos (directly below player feet), this placement isn't the closest useful one.
                    // We measure distance from player AABB center to the center of the neighbor block.
                    double neighborCX = neighbor.getX() + 0.5;
                    double neighborCY = neighbor.getY() + 0.5;
                    double neighborCZ = neighbor.getZ() + 0.5;

                    double distToPlayer = Math.sqrt(
                            (neighborCX - playerCX) * (neighborCX - playerCX) +
                                    (neighborCY - playerCY) * (neighborCY - playerCY) +
                                    (neighborCZ - playerCZ) * (neighborCZ - playerCZ)
                    );

                    // Check if any already-solid block is closer to the player than this neighbor
                    // (meaning this placement would be behind an existing block relative to player)
                    boolean redundant = false;
                    for (int rx = -1; rx <= 1 && !redundant; rx++) {
                        for (int rz = -1; rz <= 1 && !redundant; rz++) {
                            BlockPos existing = scanY.add(rx, 1, rz); // one above scan level = at player feet
                            IBlockState existingState = mc.theWorld.getBlockState(existing);
                            if (existingState.getBlock() == Blocks.air) continue;
                            if (!existingState.getBlock().isFullCube()) continue;

                            double exCX = existing.getX() + 0.5;
                            double exCY = existing.getY() + 0.5;
                            double exCZ = existing.getZ() + 0.5;
                            double distExisting = Math.sqrt(
                                    (exCX - playerCX) * (exCX - playerCX) +
                                            (exCY - playerCY) * (exCY - playerCY) +
                                            (exCZ - playerCZ) * (exCZ - playerCZ)
                            );

                            // If there's an existing solid block closer to the player in the same
                            // horizontal column as the proposed neighbor, this placement is redundant
                            if (existing.getX() == neighbor.getX() && existing.getZ() == neighbor.getZ()
                                    && distExisting < distToPlayer) {
                                redundant = true;
                            }
                        }
                    }
                    if (redundant) continue;

                    // Face center for distance ranking
                    double faceX = pos.getX() + 0.5 + facing.getFrontOffsetX() * 0.5;
                    double faceY = pos.getY() + 0.5 + facing.getFrontOffsetY() * 0.5;
                    double faceZ = pos.getZ() + 0.5 + facing.getFrontOffsetZ() * 0.5;

                    double dist = eyes.distanceTo(new Vec3(faceX, faceY, faceZ));
                    if (dist >= bestDist) continue;

                    // --- FILTER 2: raytrace validation ---
                    // Compute the rotations we'd use for this placement and raytrace to confirm it hits
                    float[] rots = getRotationsForFace(pos, facing);
                    if (rots == null) {
                        rots = getFreeRotationsForFace(pos, facing);
                    }

                    Vec3 eyeVec = player.getPositionEyes(1.0f);
                    Vec3 lookDir = ((IMixinEntity) player).invokeGetVectorForRotation(rots[1], rots[0]);
                    Vec3 traceEnd = eyeVec.addVector(
                            lookDir.xCoord * 4.5,
                            lookDir.yCoord * 4.5,
                            lookDir.zCoord * 4.5
                    );
                    MovingObjectPosition hit = player.worldObj.rayTraceBlocks(eyeVec, traceEnd, false, false, true);

                    if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) continue;
                    if (!hit.getBlockPos().equals(pos)) continue;
                    if (hit.sideHit != facing) continue;

                    bestDist = dist;
                    best = new BlockData(pos, facing);
                }
            }
        }

        return best;
    }


    public MovingObjectPosition rayTrace(EventSilentRotation event) {
        Vec3 vec3 = mc.thePlayer.getPositionEyes(1);
        Vec3 vec31 = ((IMixinEntity) mc.thePlayer).invokeGetVectorForRotation(event.getPitch(), event.getYaw());
        Vec3 vec32 = vec3.addVector(vec31.xCoord * 4.5, vec31.yCoord * 4.5, vec31.zCoord * 4.5);
        return mc.thePlayer.worldObj.rayTraceBlocks(vec3, vec32, false, false, true);
    }


    private Item keyBlock() {
        if (mc.thePlayer.inventory.getCurrentItem() == null
                || !(mc.thePlayer.inventory.getCurrentItem().getItem() instanceof ItemBlock)) {
            mc.thePlayer.inventory.currentItem = ScaffoldUtil.getBlockSlot();
        }
        return mc.thePlayer.inventory.getCurrentItem().getItem();
    }



    private void place(EventSilentRotation event) {
        if (blockData == null) {
            return;
        }

        MovingObjectPosition objectOver = rayTrace(event);
        BlockPos blockpos = objectOver.getBlockPos();
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
}