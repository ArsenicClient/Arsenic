package arsenic.utils.bot;

import arsenic.utils.botcore.BlockView;
import arsenic.utils.botcore.Box;
import arsenic.utils.minecraft.ContainerUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockCactus;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockFire;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockPane;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockVine;
import net.minecraft.block.BlockWall;
import net.minecraft.block.BlockWeb;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public final class McWorldView implements BlockView {
    private final World world;

    public McWorldView(World world) {
        this.world = world;
    }

    private boolean loaded(BlockPos pos) {
        return world.isBlockLoaded(pos);
    }

    @Override
    public void collisionBoxes(int x, int y, int z, List<Box> out) {
        BlockPos pos = new BlockPos(x, y, z);
        if (!loaded(pos)) return;
        IBlockState st = world.getBlockState(pos);
        Block b = st.getBlock();
        if (b.getMaterial() == Material.air) return;
        if (b instanceof BlockStairs) {
            stairBoxes(pos, st, out);
        } else if (b instanceof BlockSlab) {
            BlockSlab slab = (BlockSlab) b;
            if (slab.isDouble()) out.add(Box.local(x, y, z, 0, 0, 0, 1, 1, 1));
            else if (st.getValue(BlockSlab.HALF) == BlockSlab.EnumBlockHalf.TOP) out.add(Box.local(x, y, z, 0, 0.5, 0, 1, 1, 1));
            else out.add(Box.local(x, y, z, 0, 0, 0, 1, 0.5, 1));
        } else if (b instanceof BlockLadder) {
            double f = 0.125;
            switch (st.getValue(BlockLadder.FACING)) {
                case NORTH: out.add(Box.local(x, y, z, 0, 0, 1 - f, 1, 1, 1)); break;
                case SOUTH: out.add(Box.local(x, y, z, 0, 0, 0, 1, 1, f)); break;
                case WEST: out.add(Box.local(x, y, z, 1 - f, 0, 0, 1, 1, 1)); break;
                default: out.add(Box.local(x, y, z, 0, 0, 0, f, 1, 1)); break;
            }
        } else if (b instanceof BlockVine || b.getMaterial().isLiquid()) {
        } else if (b.isFullCube()) {
            out.add(Box.local(x, y, z, 0, 0, 0, 1, 1, 1));
        } else if (b instanceof BlockPane) {
            paneBoxes(pos, b, out);
        } else if (b instanceof BlockFence) {
            fenceBoxes(pos, b, out);
        } else if (b instanceof BlockWall) {
            wallBoxes(pos, b, out);
        } else if (b instanceof BlockFenceGate) {
            if (!st.getValue(BlockFenceGate.OPEN)) {
                EnumFacing.Axis axis = st.getValue(BlockFenceGate.FACING).getAxis();
                out.add(axis == EnumFacing.Axis.Z ? Box.local(x, y, z, 0, 0, 0.375, 1, 1.5, 0.625)
                        : Box.local(x, y, z, 0.375, 0, 0, 0.625, 1.5, 1));
            }
        } else if (!Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
            if (b.getMaterial().blocksMovement()) {
                out.add(Box.local(x, y, z, b.getBlockBoundsMinX(), b.getBlockBoundsMinY(), b.getBlockBoundsMinZ(),
                        b.getBlockBoundsMaxX(), b.getBlockBoundsMaxY(), b.getBlockBoundsMaxZ()));
            }
        } else {
            List<AxisAlignedBB> list = new ArrayList<>();
            AxisAlignedBB mask = new AxisAlignedBB(x - 1, y - 1, z - 1, x + 2, y + 2, z + 2);
            try {
                b.addCollisionBoxesToList(world, pos, st, mask, list, null);
            } catch (RuntimeException ignored) {
                out.add(Box.local(x, y, z, 0, 0, 0, 1, 1, 1));
                return;
            }
            for (AxisAlignedBB a : list) {
                out.add(new Box(a.minX, a.minY, a.minZ, a.maxX, a.maxY, a.maxZ));
            }
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
        IBlockState st = world.getBlockState(pos);
        Block b = st.getBlock();
        Material m = b.getMaterial();
        int f = 0;
        if (b instanceof BlockLadder || b instanceof BlockVine) f |= CLIMBABLE;
        if (m.isLiquid()) f |= LIQUID;
        if (m == Material.lava || b instanceof BlockFire || b instanceof BlockCactus || b instanceof BlockWeb) f |= DANGER;
        if (!m.isLiquid() && b.isReplaceable(world, pos)) f |= REPLACEABLE;
        if (m.isSolid() && !m.isLiquid() && !ContainerUtils.isInteractable(b)) f |= PLACE_AGAINST;
        if (BotDriver.isOwnBlock(x, y, z, st)) f |= OWN_BLOCK;
        return f;
    }


    private boolean paneConnects(BlockPos pos, EnumFacing side, Block self) {
        BlockPos off = pos.offset(side);
        Block b = world.getBlockState(off).getBlock();
        return b.isFullBlock() || b == self || b == Blocks.glass || b == Blocks.stained_glass || b instanceof BlockPane
                || b.isSideSolid(world, off, side.getOpposite());
    }

    private void paneBoxes(BlockPos pos, Block self, List<Box> out) {
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        boolean n = paneConnects(pos, EnumFacing.NORTH, self), s = paneConnects(pos, EnumFacing.SOUTH, self);
        boolean w = paneConnects(pos, EnumFacing.WEST, self), e = paneConnects(pos, EnumFacing.EAST, self);
        boolean any = n || s || w || e;
        if ((!w || !e) && any) {
            if (w) out.add(Box.local(x, y, z, 0, 0, 0.4375, 0.5, 1, 0.5625));
            else if (e) out.add(Box.local(x, y, z, 0.5, 0, 0.4375, 1, 1, 0.5625));
        } else {
            out.add(Box.local(x, y, z, 0, 0, 0.4375, 1, 1, 0.5625));
        }
        if ((!n || !s) && any) {
            if (n) out.add(Box.local(x, y, z, 0.4375, 0, 0, 0.5625, 1, 0.5));
            else if (s) out.add(Box.local(x, y, z, 0.4375, 0, 0.5, 0.5625, 1, 1));
        } else {
            out.add(Box.local(x, y, z, 0.4375, 0, 0, 0.5625, 1, 1));
        }
    }

    private boolean postConnects(BlockPos p, Block self, boolean wall) {
        Block b = world.getBlockState(p).getBlock();
        if (b == Blocks.barrier) return false;
        if (b instanceof BlockFenceGate) return true;
        if (wall ? b == self : b instanceof BlockFence && b.getMaterial() == self.getMaterial()) return true;
        return b.getMaterial().isOpaque() && b.isFullCube() && b.getMaterial() != Material.gourd;
    }

    private void fenceBoxes(BlockPos pos, Block self, List<Box> out) {
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        boolean n = postConnects(pos.north(), self, false), s = postConnects(pos.south(), self, false);
        boolean w = postConnects(pos.west(), self, false), e = postConnects(pos.east(), self, false);
        if (n || s) out.add(Box.local(x, y, z, 0.375, 0, n ? 0 : 0.375, 0.625, 1.5, s ? 1 : 0.625));
        if (w || e || (!n && !s)) out.add(Box.local(x, y, z, w ? 0 : 0.375, 0, 0.375, e ? 1 : 0.625, 1.5, 0.625));
    }

    private void wallBoxes(BlockPos pos, Block self, List<Box> out) {
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        boolean n = postConnects(pos.north(), self, true), s = postConnects(pos.south(), self, true);
        boolean w = postConnects(pos.west(), self, true), e = postConnects(pos.east(), self, true);
        double x0 = w ? 0 : 0.25, x1 = e ? 1 : 0.75, z0 = n ? 0 : 0.25, z1 = s ? 1 : 0.75;
        if (n && s && !w && !e) {
            x0 = 0.3125;
            x1 = 0.6875;
        } else if (!n && !s && w && e) {
            z0 = 0.3125;
            z1 = 0.6875;
        }
        out.add(Box.local(x, y, z, x0, 0, z0, x1, 1.5, z1));
    }


    private IBlockState stair(BlockPos p) {
        IBlockState s = world.getBlockState(p);
        return s.getBlock() instanceof BlockStairs ? s : null;
    }

    private static boolean top(IBlockState s) {
        return s.getValue(BlockStairs.HALF) == BlockStairs.EnumHalf.TOP;
    }

    private static EnumFacing facing(IBlockState s) {
        return s.getValue(BlockStairs.FACING);
    }

    private boolean same(BlockPos p, EnumFacing f, boolean top) {
        IBlockState s = stair(p);
        return s != null && top(s) == top && facing(s) == f;
    }

    private void stairBoxes(BlockPos pos, IBlockState st, List<Box> out) {
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        boolean top = top(st);
        EnumFacing f = facing(st);
        out.add(top ? Box.local(x, y, z, 0, 0.5, 0, 1, 1, 1) : Box.local(x, y, z, 0, 0, 0, 1, 0.5, 1));
        double y0 = top ? 0 : 0.5, y1 = top ? 0.5 : 1;
        double x0 = 0, x1 = 1, z0 = 0, z1 = 0.5;
        boolean straight = true;
        IBlockState n;
        if (f == EnumFacing.EAST) {
            x0 = 0.5; z1 = 1;
            n = stair(pos.east());
            if (n != null && top(n) == top) {
                if (facing(n) == EnumFacing.NORTH && !same(pos.south(), f, top)) { z1 = 0.5; straight = false; }
                else if (facing(n) == EnumFacing.SOUTH && !same(pos.north(), f, top)) { z0 = 0.5; straight = false; }
            }
        } else if (f == EnumFacing.WEST) {
            x1 = 0.5; z1 = 1;
            n = stair(pos.west());
            if (n != null && top(n) == top) {
                if (facing(n) == EnumFacing.NORTH && !same(pos.south(), f, top)) { z1 = 0.5; straight = false; }
                else if (facing(n) == EnumFacing.SOUTH && !same(pos.north(), f, top)) { z0 = 0.5; straight = false; }
            }
        } else if (f == EnumFacing.SOUTH) {
            z0 = 0.5; z1 = 1;
            n = stair(pos.south());
            if (n != null && top(n) == top) {
                if (facing(n) == EnumFacing.WEST && !same(pos.east(), f, top)) { x1 = 0.5; straight = false; }
                else if (facing(n) == EnumFacing.EAST && !same(pos.west(), f, top)) { x0 = 0.5; straight = false; }
            }
        } else {
            n = stair(pos.north());
            if (n != null && top(n) == top) {
                if (facing(n) == EnumFacing.WEST && !same(pos.east(), f, top)) { x1 = 0.5; straight = false; }
                else if (facing(n) == EnumFacing.EAST && !same(pos.west(), f, top)) { x0 = 0.5; straight = false; }
            }
        }
        out.add(Box.local(x, y, z, x0, y0, z0, x1, y1, z1));
        if (!straight) return;
        double a0 = 0, a1 = 0.5, c0 = 0.5, c1 = 1;
        boolean inner = false;
        if (f == EnumFacing.EAST) {
            n = stair(pos.west());
            if (n != null && top(n) == top) {
                if (facing(n) == EnumFacing.NORTH && !same(pos.north(), f, top)) { c0 = 0; c1 = 0.5; inner = true; }
                else if (facing(n) == EnumFacing.SOUTH && !same(pos.south(), f, top)) { c0 = 0.5; c1 = 1; inner = true; }
            }
        } else if (f == EnumFacing.WEST) {
            n = stair(pos.east());
            if (n != null && top(n) == top) {
                a0 = 0.5; a1 = 1;
                if (facing(n) == EnumFacing.NORTH && !same(pos.north(), f, top)) { c0 = 0; c1 = 0.5; inner = true; }
                else if (facing(n) == EnumFacing.SOUTH && !same(pos.south(), f, top)) { c0 = 0.5; c1 = 1; inner = true; }
            }
        } else if (f == EnumFacing.SOUTH) {
            n = stair(pos.north());
            if (n != null && top(n) == top) {
                c0 = 0; c1 = 0.5;
                if (facing(n) == EnumFacing.WEST && !same(pos.west(), f, top)) { inner = true; }
                else if (facing(n) == EnumFacing.EAST && !same(pos.east(), f, top)) { a0 = 0.5; a1 = 1; inner = true; }
            }
        } else {
            n = stair(pos.south());
            if (n != null && top(n) == top) {
                if (facing(n) == EnumFacing.WEST && !same(pos.west(), f, top)) { inner = true; }
                else if (facing(n) == EnumFacing.EAST && !same(pos.east(), f, top)) { a0 = 0.5; a1 = 1; inner = true; }
            }
        }
        if (inner) out.add(Box.local(x, y, z, a0, y0, c0, a1, y1, c1));
    }
}
