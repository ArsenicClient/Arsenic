package arsenic.utils.minecraft;

import arsenic.utils.botcore.Box;
import arsenic.utils.scaffoldcore.ScaffoldWorld;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

public final class McScaffoldWorld implements ScaffoldWorld {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final ItemBlock placeholder = new ItemBlock(Blocks.tnt);

    @Override
    public boolean isAir(int x, int y, int z) {
        return mc.theWorld.getBlockState(new BlockPos(x, y, z)).getBlock().getMaterial().isReplaceable();
    }

    @Override
    public boolean isFullCube(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        IBlockState state = mc.theWorld.getBlockState(pos);
        return state.getBlock().getMaterial().isSolid()
                && state.getBlock().getCollisionBoundingBox(mc.theWorld, pos, state) != null;
    }

    @Override
    public boolean canPlaceOnSide(int x, int y, int z, int face) {
        ItemStack held = mc.thePlayer.getHeldItem();
        ItemBlock item = held != null && held.getItem() instanceof ItemBlock ? (ItemBlock) held.getItem() : placeholder;
        return item.canPlaceBlockOnSide(mc.theWorld, new BlockPos(x, y, z), EnumFacing.getFront(face), mc.thePlayer, held);
    }

    @Override
    public boolean boxFree(Box b) {
        return mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer,
                new AxisAlignedBB(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ)).isEmpty();
    }

    @Override
    public boolean rayTrace(double sx, double sy, double sz, double ex, double ey, double ez, Hit out) {
        MovingObjectPosition hit = mc.thePlayer.worldObj.rayTraceBlocks(new Vec3(sx, sy, sz), new Vec3(ex, ey, ez), false, false, true);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return false;
        BlockPos pos = hit.getBlockPos();
        out.x = pos.getX();
        out.y = pos.getY();
        out.z = pos.getZ();
        out.face = hit.sideHit.getIndex();
        out.hx = hit.hitVec.xCoord;
        out.hy = hit.hitVec.yCoord;
        out.hz = hit.hitVec.zCoord;
        return true;
    }
}
