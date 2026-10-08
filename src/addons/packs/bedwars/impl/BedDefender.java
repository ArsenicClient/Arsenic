import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.AntiBot;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.rotations.SilentRotationManager;
import arsenic.utils.timer.MSTimer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Places blocks next to your bed when an enemy comes near. Stand next to your bed and enable it.
 *
 * Placement follows the silent rotation pipeline: the camera stays where it is, the rotation is requested for the
 * chosen face, and the block is placed only from the ray of the rotation that is actually sent (EventSilentRotation.Post)
 * when that ray hits the intended face. Placements are spaced by a random delay and never happen with a GUI open.
 */
@ModuleInfo(name = "BedDefender", description = "Places blocks next to your bed when an enemy comes near. Stand next to your bed and enable it.", category = ModuleCategory.PLAYER)
public class BedDefender extends Module {

    public final BooleanProperty render = new BooleanProperty("Render", true);
    public final DoubleProperty range = new DoubleProperty("Range", new DoubleValue(2, 4.5, 4.5, 0.1));
    public final DoubleProperty delay = new DoubleProperty("Place Delay (ms)", new DoubleValue(50, 500, 150, 10));
    public final DoubleProperty alertRange = new DoubleProperty("Enemy Range", new DoubleValue(4, 16, 8, 1));

    private final MSTimer placeTimer = new MSTimer();
    private final Random random = new Random();
    private BlockPos aimTarget, aimSupport;
    private EnumFacing aimFace;
    private Vec3 aimPoint;
    private float aimYaw, aimPitch;
    private boolean hasAim;
    private int missTicks, restoreSlot = -1;

    @Override
    protected void onDisable() {
        hasAim = false;
        restore();
    }

    @Override
    public boolean isSwappingHotbar() {
        return restoreSlot != -1;
    }

    /** Candidate positions to fill, from the player's position. */
    private List<BlockPos> targets() {
        List<BlockPos> out = new ArrayList<>();
        BlockPos origin = new BlockPos(mc.thePlayer);
        for (int x = -3; x <= 3; x++) for (int y = -2; y <= 2; y++) for (int z = -3; z <= 3; z++) {
            BlockPos bed = origin.add(x, y, z);
            if (!mc.theWorld.isBlockLoaded(bed) || !(mc.theWorld.getBlockState(bed).getBlock() instanceof BlockBed)) continue;
            for (EnumFacing f : EnumFacing.HORIZONTALS) out.add(bed.offset(f));
        }
        return out;
    }

    /** Whether the situation is right for placing at all (for example, standing still). */
    private boolean guard() {
        return nearestEnemy(alertRange.getValue().getInput()) != null;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        restore();
        if (hasAim || mc.currentScreen != null || !guard() || !placeTimer.hasTimeElapsed(0, false)) return;
        if (findBlockSlot() == -1) return;
        pick();
    };

    private void pick() {
        Vec3 eye = mc.thePlayer.getPositionEyes(1f);
        SilentRotationManager srm = Arsenic.getArsenic().getSilentRotationManager();
        double bestTurn = Double.MAX_VALUE;
        double reach = range.getValue().getInput();
        for (BlockPos t : targets()) {
            if (!replaceable(t) || intersectsPlayer(t)) continue;
            if (eye.distanceTo(new Vec3(t.getX() + 0.5, t.getY() + 0.5, t.getZ() + 0.5)) > reach) continue;
            for (EnumFacing f : EnumFacing.values()) {
                BlockPos support = t.offset(f.getOpposite());
                if (!solid(support)) continue;
                // a point on the support's face, pulled off the exact centre
                Vec3 point = new Vec3(support.getX() + 0.5 + f.getFrontOffsetX() * 0.5 + jitter(),
                        support.getY() + 0.5 + f.getFrontOffsetY() * 0.5 + jitter(),
                        support.getZ() + 0.5 + f.getFrontOffsetZ() * 0.5 + jitter());
                float[] r = RotationUtils.rotationsTo(eye, point);
                double turn = Math.abs(RotationUtils.getYawDifference(r[0], srm.yaw)) + Math.abs(r[1] - srm.pitch);
                if (turn < bestTurn) {
                    bestTurn = turn;
                    aimTarget = t;
                    aimSupport = support;
                    aimFace = f;
                    aimPoint = point;
                    aimYaw = r[0];
                    aimPitch = r[1];
                    hasAim = true;
                    missTicks = 0;
                }
            }
        }
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        if (!hasAim || mc.currentScreen != null) return;
        event.setYaw(aimYaw);
        event.setPitch(aimPitch);
        event.setSpeed((float) (120 * (0.95 + random.nextDouble() * 0.1)));
        event.setPreventDuplicateLook(true);
        event.setBlockUserInput(true);
        event.setMovementFix(SilentRotationManager.MovementFix.SILENT);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onPost = event -> {
        if (!hasAim) return;
        MovingObjectPosition ray = event.getRayTrace();
        if (ray == null || ray.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || !ray.getBlockPos().equals(aimSupport) || ray.sideHit != aimFace) {
            if (++missTicks >= 4) hasAim = false;   // give up on this spot; the next tick picks again
            return;
        }
        if (!placeTimer.hasTimeElapsed((long) (delay.getValue().getInput() * (0.85 + random.nextDouble() * 0.3)), false)) return;

        int slot = findBlockSlot();
        if (slot == -1) {
            hasAim = false;
            return;
        }
        if (restoreSlot == -1) restoreSlot = mc.thePlayer.inventory.currentItem;
        mc.thePlayer.inventory.currentItem = slot;
        ItemStack held = mc.thePlayer.inventory.getCurrentItem();
        if (held != null && held.getItem() instanceof ItemBlock
                && ((ItemBlock) held.getItem()).canPlaceBlockOnSide(mc.theWorld, aimSupport, aimFace, mc.thePlayer, held)) {
            if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, held, aimSupport, aimFace, ray.hitVec)) {
                mc.thePlayer.swingItem();
            }
        }
        placeTimer.reset();
        hasAim = false;
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRenderWorld = event -> {
        if (!render.getValue() || !hasAim || aimTarget == null) return;
        int main = Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor();
        RenderUtils.renderBlock(aimTarget, ColorUtils.withAlpha(main, 80), false, true);
        RenderUtils.renderBlock(aimTarget, ColorUtils.withAlpha(main, 230), true, false);
    };

    private void restore() {
        if (restoreSlot != -1 && mc.thePlayer != null) mc.thePlayer.inventory.currentItem = restoreSlot;
        restoreSlot = -1;
    }

    private double jitter() {
        return (random.nextDouble() - 0.5) * 0.4;
    }

    private static boolean replaceable(BlockPos p) {
        return mc.theWorld.isBlockLoaded(p) && mc.theWorld.getBlockState(p).getBlock().getMaterial().isReplaceable();
    }

    private static boolean solid(BlockPos p) {
        if (!mc.theWorld.isBlockLoaded(p)) return false;
        Block b = mc.theWorld.getBlockState(p).getBlock();
        return !b.getMaterial().isReplaceable() && b.getMaterial().isSolid();
    }

    private static boolean intersectsPlayer(BlockPos p) {
        return mc.thePlayer.getEntityBoundingBox().intersectsWith(new AxisAlignedBB(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1));
    }

    private static int findBlockSlot() {
        int best = -1, bestSize = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.thePlayer.inventory.mainInventory[i];
            if (s == null || s.stackSize <= 0 || !(s.getItem() instanceof ItemBlock)) continue;
            Block b = ((ItemBlock) s.getItem()).getBlock();
            if (!b.isFullCube() || b instanceof BlockBed) continue;
            if (s.stackSize > bestSize) {
                bestSize = s.stackSize;
                best = i;
            }
        }
        return best;
    }

    /** The nearest enemy player within the given distance: not a bot, not a friend, not dead. */
    static EntityPlayer nearestEnemy(double within) {
        EntityPlayer best = null;
        for (EntityPlayer p : mc.theWorld.playerEntities) {
            if (p == mc.thePlayer || p.isDead || AntiBot.isBot(p)) continue;
            if (Arsenic.getArsenic().getFriendManager().isFriend(p)) continue;
            double d = mc.thePlayer.getDistanceToEntity(p);
            if (d > within) continue;
            if (best == null || d < mc.thePlayer.getDistanceToEntity(best)) best = p;
        }
        return best;
    }
}
