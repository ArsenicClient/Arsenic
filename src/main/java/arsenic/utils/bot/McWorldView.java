package arsenic.utils.bot;

import arsenic.utils.botcore.BlockView;
import arsenic.utils.botcore.Box;
import arsenic.utils.minecraft.ContainerUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.MagmaBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * The bot core's view of the world. On 1.8 this hand-coded the collision boxes of stairs, slabs,
 * fences, panes and walls because they depended on neighbouring blocks; every block state now
 * carries its exact collision shape, so the boxes come straight from it.
 */
public final class McWorldView implements BlockView {
    private final Level world;

    public McWorldView(Level world) {
        this.world = world;
    }

    private boolean loaded(BlockPos pos) {
        return world.isLoaded(pos);
    }

    @Override
    public void collisionBoxes(int x, int y, int z, List<Box> out) {
        BlockPos pos = new BlockPos(x, y, z);
        if (!loaded(pos)) return;
        BlockState st = world.getBlockState(pos);
        if (st.isAir()) return;
        for (AABB a : st.getCollisionShape(world, pos).toAabbs()) {
            out.add(new Box(a.minX + x, a.minY + y, a.minZ + z, a.maxX + x, a.maxY + y, a.maxZ + z));
        }
    }

    @Override
    public void rayBoxes(int x, int y, int z, List<Box> out) {
        collisionBoxes(x, y, z, out);
    }

    @Override
    public int flags(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (!loaded(pos)) return UNLOADED;
        BlockState st = world.getBlockState(pos);
        Block b = st.getBlock();
        boolean liquid = !st.getFluidState().isEmpty();
        int f = 0;
        if (st.is(BlockTags.CLIMBABLE)) f |= CLIMBABLE;
        if (liquid) f |= LIQUID;
        if (st.getFluidState().is(FluidTags.LAVA) || b instanceof BaseFireBlock || b instanceof CactusBlock
                || b instanceof WebBlock || b instanceof MagmaBlock || b instanceof SweetBerryBushBlock) f |= DANGER;
        if (!liquid && st.canBeReplaced()) f |= REPLACEABLE;
        if (st.isSolid() && !liquid && !ContainerUtils.isInteractable(b)) f |= PLACE_AGAINST;
        if (BotDriver.isOwnBlock(x, y, z, st)) f |= OWN_BLOCK;
        return f;
    }
}
