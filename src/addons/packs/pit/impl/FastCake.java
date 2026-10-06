
import arsenic.utils.rotations.RotationUtils;
import arsenic.module.property.impl.SliderScale;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.PropertyInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import net.minecraft.block.Block;
import net.minecraft.block.BlockCake;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Mouse;

@ModuleInfo(name = "FastCake", category = ModuleCategory.PLAYER, tier = ModuleTier.BLATANT)
public class FastCake extends Module {

    public final BooleanProperty autoAim = new BooleanProperty("Auto Aim", false);
    @PropertyInfo(reliesOn = "Auto Aim", value = "true")
    public final DoubleProperty range = new DoubleProperty("Range", new DoubleValue(1, 6, 4, 0.5));
    @PropertyInfo(reliesOn = "Auto Aim", value = "true")
    public final DoubleProperty rotSpeed = new DoubleProperty("Rotation Speed", new DoubleValue(1, 360, 180, 1), SliderScale.LOG);

    private BlockPos pendingCake;

    @Override
    protected void onDisable() {
        pendingCake = null;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        if (!Mouse.isButtonDown(1) || !autoAim.getValue())
            return;

        BlockPos cake = findNearestCake();
        if (cake != null) {
            float[] rots = getCakeRotations(cake);
            event.setYaw(rots[0]);
            event.setPitch(rots[1]);
            event.setSpeed((float) rotSpeed.getValue().getInput());
        }
        pendingCake = cake;
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (!Mouse.isButtonDown(1)) {
            pendingCake = null;
            return;
        }

        if (autoAim.getValue()) {
            if (pendingCake != null && mc.theWorld.getBlockState(pendingCake).getBlock() == Blocks.cake) {
                Block block = Blocks.cake;
                block.setBlockBoundsBasedOnState(mc.theWorld, pendingCake);
                double cx = pendingCake.getX() + (block.getBlockBoundsMinX() + block.getBlockBoundsMaxX()) / 2.0;
                double cz = pendingCake.getZ() + (block.getBlockBoundsMinZ() + block.getBlockBoundsMaxZ()) / 2.0;
                Vec3 hitVec = new Vec3(cx, pendingCake.getY() + block.getBlockBoundsMaxY(), cz);
                eat(pendingCake, EnumFacing.UP, hitVec);
            }
            return;
        }

        MovingObjectPosition mop = mc.objectMouseOver;
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK)
            return;

        BlockPos pos = mop.getBlockPos();
        if (mc.theWorld.getBlockState(pos).getBlock() != Blocks.cake)
            return;

        eat(pos, mop.sideHit, mop.hitVec);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventPacket.Incoming.Pre> onBlockUpdate = event -> {
        if (!(event.getPacket() instanceof S23PacketBlockChange))
            return;

        S23PacketBlockChange packet = (S23PacketBlockChange) event.getPacket();
        IBlockState serverState = packet.getBlockState();
        if (serverState.getBlock() != Blocks.cake)
            return;

        BlockPos pos = packet.getBlockPosition();
        IBlockState clientState = mc.theWorld.getBlockState(pos);

        if (clientState.getBlock() != Blocks.cake) {
            event.setCancelled(true);
            return;
        }

        int serverBites = serverState.getValue(BlockCake.BITES);
        int clientBites = clientState.getValue(BlockCake.BITES);
        if (clientBites > serverBites)
            event.setCancelled(true);
    };

    /**
     * Right clicks the cake. Vanilla only takes the bite on the client while you are hungry, so when it did not,
     * the bite is applied here the way an incoming block change from the server would (the same call the
     * client makes for one). The cake then goes down at once and the next click is not wasted on it.
     */
    private void eat(BlockPos pos, EnumFacing side, Vec3 hitVec) {
        IBlockState before = mc.theWorld.getBlockState(pos);
        mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem(), pos, side, hitVec);

        if (before.getBlock() != Blocks.cake || mc.theWorld.getBlockState(pos) != before)
            return;
        int bites = before.getValue(BlockCake.BITES);
        mc.theWorld.invalidateRegionAndSetBlock(pos,
                bites < 6 ? before.withProperty(BlockCake.BITES, bites + 1) : Blocks.air.getDefaultState());
    }

    private float[] getCakeRotations(BlockPos cake) {
        Block block = Blocks.cake;
        block.setBlockBoundsBasedOnState(mc.theWorld, cake);
        double cx = cake.getX() + (block.getBlockBoundsMinX() + block.getBlockBoundsMaxX()) / 2.0;
        double cy = cake.getY() + (block.getBlockBoundsMinY() + block.getBlockBoundsMaxY()) / 2.0;
        double cz = cake.getZ() + (block.getBlockBoundsMinZ() + block.getBlockBoundsMaxZ()) / 2.0;

        Vec3 eyes = mc.thePlayer.getPositionEyes(1f);
        return RotationUtils.rotationsTo(eyes, new Vec3(cx, cy, cz));
    }

    private BlockPos findNearestCake() {
        int r = (int) Math.ceil(range.getValue().getInput());
        BlockPos playerPos = new BlockPos(mc.thePlayer);
        BlockPos closest = null;
        double closestDist = Double.MAX_VALUE;
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    if (mc.theWorld.getBlockState(pos).getBlock() == Blocks.cake) {
                        double dist = mc.thePlayer.getDistanceSq(pos);
                        if (dist < closestDist) {
                            closestDist = dist;
                            closest = pos;
                        }
                    }
                }
            }
        }
        return closest;
    }
}
