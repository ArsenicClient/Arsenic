package arsenic.module.impl.world;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventSilentRotation;
import arsenic.injection.accessor.IMixinEntity;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.world.Scaffold.BlockData;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.ScaffoldUtil;
import arsenic.utils.render.RenderUtils;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

@ModuleInfo(name = "Clutch", category = ModuleCategory.WORLD)
public class Clutch extends Module {

    // Rotation catch-up speed (degrees/tick). High by default so the block lands in time.
    public final DoubleProperty rotationSpeed = new DoubleProperty("Rotation Speed", new DoubleValue(10, 360, 200, 1));
    // Minimum clear drop below the player (blocks) before Clutch arms.
    public final DoubleProperty fallDistance = new DoubleProperty("Fall Distance", new DoubleValue(1, 256, 20, 1));
    // How recently (ms) a knockback packet — or an in-progress clutch — must have happened for
    // Clutch to stay armed.
    public final DoubleProperty knockbackWindow = new DoubleProperty("Knockback Window", new DoubleValue(0, 2000, 500, 50));
    // How many block layers below the feet to also scan for a catch (0 = feet layer only). Higher
    // catches are still preferred; lower layers are a fallback when nothing is reachable up top.
    public final DoubleProperty searchDepth = new DoubleProperty("Search Depth", new DoubleValue(0, 16, 5, 1));

    private static final ItemBlock placeholderBlock = new ItemBlock(Blocks.tnt);
    private static final double REACH = 4.5;

    private BlockData blockData;      // placement found THIS tick (null on ticks with nothing to place)
    private boolean falling;          // in an active void-fall episode (rotations are being held)
    private boolean placing;          // a valid placement was found this tick -> Post should place
    // Last rotation Clutch calculated. Held for the whole void-fall episode so that on ticks where
    // no block is placed the view stays on the last aim (and a modified yaw stays modified) instead
    // of snapping back to the player's raw rotation.
    private boolean hasRots;
    private float lastYaw, lastPitch;
    // Give up rotating if we can't actually land a block for this long (measured from the episode
    // start, then from each successful placement).
    private static final long PLACE_TIMEOUT_MS = 200;
    private long lastPlaceTime;

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> eventSilentRotationListener = event -> {
        blockData = null;
        placing = false;

        // Defer to the real modules if the player already runs one of them, and end the episode
        // once the player is no longer falling into the void (this is what clears the held rotation
        // and lets a modified yaw be released). Also require a recent knockback so Clutch only fires
        // when the player was actually knocked into the drop.
        if (isScaffoldActive() || isSafeWalkActive() || !isFallingIntoVoid() || !armedByTrigger()) {
            falling = false;
            hasRots = false;
            return;
        }

        // Start of a new episode: reset the placement timer so the give-up window is measured from
        // here rather than from a placement in some earlier fall.
        if (!falling)
            lastPlaceTime = System.currentTimeMillis();
        falling = true;

        // Give up on rotations if we've been falling but haven't managed to place a block within the
        // last 200ms — no point fighting the view when we clearly can't catch.
        if (System.currentTimeMillis() - lastPlaceTime > PLACE_TIMEOUT_MS) {
            hasRots = false;
            return;
        }

        event.setSpeed((float) rotationSpeed.getValue().getInput());
        event.setPreventDuplicateLook(true);

        Item item = keyBlock();
        if (item instanceof ItemBlock) {
            // Keep the player's rotations: lock yaw to where they're actually looking rather than
            // deriving it from movement direction like Scaffold does. Only pitch is solved to hit
            // the block face.
            float lockedYaw = mc.thePlayer.rotationYaw;
            BlockData found = findCatchPlacement(lockedYaw);
            if (found != null) {
                blockData = found;
                placing = true;

                float[] solved = Scaffold.getRotationsForFace(found.getPosition(), found.getFacing(), lockedYaw);
                if (solved != null) {
                    lastYaw = lockedYaw;
                    lastPitch = solved[1];
                } else {
                    // Locked yaw can't reach the face — free aim so the player is still caught.
                    float[] free = Scaffold.getFreeRotationsForFace(found.getPosition(), found.getFacing());
                    lastYaw = free[0];
                    lastPitch = free[1];
                }
                hasRots = true;
            }
        }

        // Apply the last calculated rotation whether or not a block was placed this tick, so the aim
        // (including any modified yaw) persists until the void-fall episode ends.
        if (hasRots) {
            event.setYaw(lastYaw);
            event.setPitch(lastPitch);
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> eventSilentRotationPostListener = event -> {
        if (!placing || blockData == null)
            return;

        Item item = keyBlock();
        if (!(item instanceof ItemBlock))
            return;
        ItemBlock itemBlock = (ItemBlock) item;

        MovingObjectPosition mop = event.getRayTraceEntity();
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK)
            return;
        // Never cap a block from below; allow towering (UP) only when the player has clearance above
        // it, otherwise keep the catch at the player's current level (KeepY).
        if (mop.sideHit == EnumFacing.DOWN)
            return;
        if (mop.sideHit == EnumFacing.UP && !canPlaceUpOn(mop.getBlockPos()))
            return;
        if (mc.theWorld.getBlockState(mop.getBlockPos()).getBlock().getMaterial() == Material.air)
            return;
        if (!itemBlock.canPlaceBlockOnSide(mc.theWorld, mop.getBlockPos(), mop.sideHit, mc.thePlayer, mc.thePlayer.getHeldItem()))
            return;

        blockData = new BlockData(mop.getBlockPos(), mop.sideHit);
        mc.playerController.onPlayerRightClick(
                mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getCurrentItem(),
                blockData.getPosition(), blockData.getFacing(), ScaffoldUtil.getNewVector(blockData)
        );
        mc.thePlayer.swingItem();
        lastPlaceTime = System.currentTimeMillis();
        lastClutchTime = lastPlaceTime;
    };

    @EventLink
    public final Listener<EventRenderWorldLast> renderWorldLast = event -> {
        if (!falling || blockData == null)
            return;
        RenderUtils.renderBlock(blockData.getPosition(),
                Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor(), true, false);
        RenderUtils.renderBlockFace(blockData.getPosition(), blockData.getFacing(),
                Arsenic.getArsenic().getThemeManager().getCurrentTheme().getBlack(), true, true);
    };

    // Fast falls / knockback can flicker the raw check between ticks, so we also stay armed for a
    // short window after the last tick the player was genuinely falling into the void.
    private static final long VOID_MEMORY_MS = 200;
    private long lastVoidFallTime = -1;

    // Clutch only arms if the player was actually knocked (non-zero velocity packet) recently — this
    // keeps it to genuine "knocked into the void" situations rather than voluntary jumps off ledges.
    // A clutch already in progress (recent placement) keeps it armed past the knockback window.
    private volatile long lastKnockbackTime = -1;
    private long lastClutchTime = -1;

    @EventLink
    public final Listener<EventPacket.Incoming.Pre> knockbackListener = event -> {
        if (mc.thePlayer == null || !(event.getPacket() instanceof S12PacketEntityVelocity))
            return;
        S12PacketEntityVelocity p = (S12PacketEntityVelocity) event.getPacket();
        if (p.getEntityID() != mc.thePlayer.getEntityId())
            return;
        if (p.getMotionX() != 0 || p.getMotionY() != 0 || p.getMotionZ() != 0)
            lastKnockbackTime = System.currentTimeMillis();
    };

    private boolean withinWindow(long stamp) {
        return stamp != -1 && System.currentTimeMillis() - stamp <= (long) knockbackWindow.getValue().getInput();
    }

    private boolean armedByTrigger() {
        // Knocked recently, OR mid-clutch (placed a block recently) so a long fall keeps going.
        return withinWindow(lastKnockbackTime) || withinWindow(lastClutchTime);
    }

    /**
     * True when the player faces a big drop right now, OR did during the last 200ms. The memory
     * window keeps Clutch armed through the brief frames where a fast/knocked-back player
     * momentarily reads as safe.
     */
    private boolean isFallingIntoVoid() {
        if (bigFallNow()) {
            lastVoidFallTime = System.currentTimeMillis();
            return true;
        }
        return lastVoidFallTime != -1 && System.currentTimeMillis() - lastVoidFallTime <= VOID_MEMORY_MS;
    }

    /**
     * Raw check: the player is airborne and has no collidable block within the configured Fall
     * Distance below them — i.e. a drop long enough to be worth catching.
     */
    private boolean bigFallNow() {
        EntityPlayerSP player = mc.thePlayer;
        if (player.onGround)
            return false;
        // Must actually be about to leave/stay off support this tick.
        if (!ScaffoldUtil.willFallNextTick())
            return false;
        // Scan the column below the player, down by Fall Distance blocks (clamped at world bottom),
        // for anything collidable. Empty -> the drop is at least that far.
        AxisAlignedBB box = player.getEntityBoundingBox();
        double bottom = Math.max(0, box.minY - fallDistance.getValue().getInput());
        AxisAlignedBB column = new AxisAlignedBB(box.minX, bottom, box.minZ, box.maxX, box.minY, box.maxZ);
        return mc.theWorld.getCollidingBoundingBoxes(player, column).isEmpty();
    }

    private boolean isScaffoldActive() {
        Scaffold scaffold = Arsenic.getArsenic().getModuleManager().getModuleByClass(Scaffold.class);
        return scaffold != null && scaffold.isEnabled();
    }

    private boolean isSafeWalkActive() {
        SafeWalk safeWalk = Arsenic.getArsenic().getModuleManager().getModuleByClass(SafeWalk.class);
        return safeWalk != null && safeWalk.isEnabled();
    }

    /** Ensure a stack of blocks is held; returns the held item (or null if none usable). */
    private Item keyBlock() {
        if (mc.thePlayer.inventory.getCurrentItem() == null
                || !(mc.thePlayer.inventory.getCurrentItem().getItem() instanceof ItemBlock)
                || mc.thePlayer.inventory.getCurrentItem().stackSize <= 1) {
            mc.thePlayer.inventory.currentItem = ScaffoldUtil.getBlockSlot();
        }
        if (mc.thePlayer.inventory.getCurrentItem() == null)
            return null;
        return mc.thePlayer.inventory.getCurrentItem().getItem();
    }

    /**
     * KeepY-style placement scan: the support layer beneath the feet plus up to Search Depth layers
     * below it, horizontal faces only (no towering), scored by proximity to the cell the player is
     * heading into. The vertical term is measured from the top layer, so higher catches win and
     * lower layers only get picked when nothing reachable exists above. Candidate faces are
     * ray-verified from the eyes using the locked yaw so we only commit to a face the player can
     * actually reach without turning.
     */
    private BlockData findCatchPlacement(float lockedYaw) {
        EntityPlayerSP player = mc.thePlayer;
        BlockPos topLayer = new BlockPos(player).down();

        AxisAlignedBB predicted = ScaffoldUtil.getPredictedBoundingBox(1.0);
        double targetX = (predicted.minX + predicted.maxX) * 0.5;
        double targetZ = (predicted.minZ + predicted.maxZ) * 0.5;
        double targetY = topLayer.getY() + 0.5;

        BlockData best = null;
        double bestScore = Double.MAX_VALUE;
        // Best score of a block ALREADY there that the player would land on. If nothing we could
        // place beats it, a placement is pointless — they're already going to be caught as well.
        double existingScore = Double.MAX_VALUE;

        Vec3 eyeVec = player.getPositionEyes(1.0f);

        int depth = (int) searchDepth.getValue().getInput();
        for (int down = 0; down <= depth; down++) {
            BlockPos layer = topLayer.down(down);
            if (layer.getY() < 0) break;
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    BlockPos pos = layer.add(x, 0, z);
                    IBlockState state = mc.theWorld.getBlockState(pos);
                    if (state.getBlock() == Blocks.air) continue;
                    if (!state.getBlock().isFullCube()) continue;

                    // Existing catch: a solid block under the landing footprint with air above is
                    // something the player will simply land on. Record how good that catch is.
                    if (overlapsFootprint(pos, predicted)
                            && mc.theWorld.getBlockState(pos.up()).getBlock() == Blocks.air) {
                        double ex = cellScore(pos, targetX, targetY, targetZ);
                        if (ex < existingScore) existingScore = ex;
                    }

                    for (EnumFacing facing : EnumFacing.values()) {
                        // Never place downward; only tower (UP) when there's real clearance above the
                        // block for the player to land on the tower.
                        if (facing == EnumFacing.DOWN) continue;
                        if (facing == EnumFacing.UP && !canPlaceUpOn(pos)) continue;

                        if (!placeholderBlock.canPlaceBlockOnSide(mc.theWorld, pos, facing, player, player.getHeldItem()))
                            continue;

                        BlockPos neighbor = pos.offset(facing);
                        if (mc.theWorld.getBlockState(neighbor).getBlock() != Blocks.air)
                            continue;

                        // Relevance: the placed block has to sit under the player's landing footprint,
                        // otherwise they'll just fall past it — it wouldn't save them.
                        if (!overlapsFootprint(neighbor, predicted))
                            continue;

                        // Reach cull: the aim point is the centre of the face we'd click. Anything
                        // past the 4.5-block reach can't be placed, so skip it before any ray work.
                        double fcx = pos.getX() + 0.5 + facing.getFrontOffsetX() * 0.5;
                        double fcy = pos.getY() + 0.5 + facing.getFrontOffsetY() * 0.5;
                        double fcz = pos.getZ() + 0.5 + facing.getFrontOffsetZ() * 0.5;
                        double edx = fcx - eyeVec.xCoord, edy = fcy - eyeVec.yCoord, edz = fcz - eyeVec.zCoord;
                        if (edx * edx + edy * edy + edz * edz > REACH * REACH)
                            continue;

                        double score = cellScore(neighbor, targetX, targetY, targetZ);
                        if (score >= bestScore)
                            continue;

                        float[] rots = Scaffold.getRotationsForFace(pos, facing, lockedYaw);
                        if (rots == null)
                            rots = Scaffold.getFreeRotationsForFace(pos, facing);

                        Vec3 lookDir = ((IMixinEntity) player).invokeGetVectorForRotation(rots[1], rots[0]);
                        Vec3 traceEnd = eyeVec.addVector(lookDir.xCoord * REACH, lookDir.yCoord * REACH, lookDir.zCoord * REACH);
                        MovingObjectPosition hit = player.worldObj.rayTraceBlocks(eyeVec, traceEnd, false, false, true);

                        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) continue;
                        if (!hit.getBlockPos().equals(pos)) continue;
                        if (hit.sideHit != facing) continue;

                        bestScore = score;
                        best = new BlockData(pos, facing);
                    }
                }
            }
        }

        // Something already under the player catches at least as well as anything we could place —
        // don't spend a block, just let them land on it.
        if (best != null && existingScore <= bestScore)
            return null;

        return best;
    }

    /** The player's feet are more than 0.5m above this block's top, so towering onto it is valid. */
    private boolean canPlaceUpOn(BlockPos pos) {
        return mc.thePlayer.getEntityBoundingBox().minY - (pos.getY() + 1) > 0.5;
    }

    /** How well the block/cell at {@code pos} catches the player: closer & higher scores lower. */
    private double cellScore(BlockPos pos, double targetX, double targetY, double targetZ) {
        double dx = (pos.getX() + 0.5) - targetX;
        double dz = (pos.getZ() + 0.5) - targetZ;
        double dy = (pos.getY() + 0.5) - targetY;
        return dx * dx + dz * dz + dy * dy * 0.25;
    }

    /** Whether the block column horizontally overlaps the player's predicted landing footprint. */
    private boolean overlapsFootprint(BlockPos block, AxisAlignedBB footprint) {
        return block.getX() < footprint.maxX && block.getX() + 1 > footprint.minX
                && block.getZ() < footprint.maxZ && block.getZ() + 1 > footprint.minZ;
    }
}
