package arsenic.utils.minecraft;

import arsenic.utils.botcore.Box;
import arsenic.utils.scaffoldcore.ScaffoldWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public final class McScaffoldWorld implements ScaffoldWorld {

    private static final Minecraft mc = Minecraft.getInstance();

    @Override
    public boolean isAir(int x, int y, int z) {
        return mc.level.getBlockState(new BlockPos(x, y, z)).canBeReplaced();
    }

    @Override
    public boolean isFullCube(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = mc.level.getBlockState(pos);
        return state.isSolid() && !state.getCollisionShape(mc.level, pos).isEmpty();
    }

    /** 1.8's ItemBlock#canPlaceBlockOnSide: whether the held block (TNT if none) could go against this face. */
    @Override
    public boolean canPlaceOnSide(int x, int y, int z, int face) {
        ItemStack held = mc.player.getMainHandItem();
        Block block = held.getItem() instanceof BlockItem item ? item.getBlock() : Blocks.TNT;
        BlockPos against = new BlockPos(x, y, z);
        BlockPos pos = mc.level.getBlockState(against).canBeReplaced() ? against : against.relative(Direction.from3DDataValue(face));
        BlockState state = block.defaultBlockState();
        return mc.level.getBlockState(pos).canBeReplaced() && state.canSurvive(mc.level, pos)
                && mc.level.isUnobstructed(state, pos, CollisionContext.empty());
    }

    @Override
    public boolean boxFree(Box b) {
        return mc.level.noCollision(mc.player, new AABB(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ));
    }

    @Override
    public boolean rayTrace(double sx, double sy, double sz, double ex, double ey, double ez, Hit out) {
        BlockHitResult hit = PlayerUtils.rayTraceBlocks(new Vec3(sx, sy, sz), new Vec3(ex, ey, ez));
        if (hit.getType() != HitResult.Type.BLOCK) return false;
        BlockPos pos = hit.getBlockPos();
        out.x = pos.getX();
        out.y = pos.getY();
        out.z = pos.getZ();
        out.face = hit.getDirection().get3DDataValue();
        out.hx = hit.getLocation().x;
        out.hy = hit.getLocation().y;
        out.hz = hit.getLocation().z;
        return true;
    }
}
