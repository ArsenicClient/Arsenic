package arsenic.module.impl.player;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.PropertyInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import net.minecraft.world.level.block.Block;
import net.minecraft.block.BlockCake;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.input.Mouse;

@ModuleInfo(name = "Fast Cake", category = ModuleCategory.PLAYER)
public class FastCake extends Module {

    public final BooleanProperty autoAim = new BooleanProperty("Auto Aim", false);
    @PropertyInfo(reliesOn = "Auto Aim", value = "true")
    public final DoubleProperty range = new DoubleProperty("Range", new DoubleValue(1, 6, 4, 0.5));
    @PropertyInfo(reliesOn = "Auto Aim", value = "true")
    public final DoubleProperty rotSpeed = new DoubleProperty("Rotation Speed", new DoubleValue(1, 360, 180, 1));

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
            if (pendingCake != null && mc.level.getBlockState(pendingCake).getBlock() == Blocks.cake) {
                Block block = Blocks.cake;
                block.setBlockBoundsBasedOnState(mc.level, pendingCake);
                double cx = pendingCake.getX() + (block.getBlockBoundsMinX() + block.getBlockBoundsMaxX()) / 2.0;
                double cz = pendingCake.getZ() + (block.getBlockBoundsMinZ() + block.getBlockBoundsMaxZ()) / 2.0;
                Vec3 hitVec = new Vec3(cx, pendingCake.getY() + block.getBlockBoundsMaxY(), cz);
                mc.gameMode.onPlayerRightClick(
                        mc.player, mc.level, mc.player.getMainHandItem(),
                        pendingCake, Direction.UP, hitVec
                );
            }
            return;
        }

        MovingObjectPosition mop = mc.hitResult;
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK)
            return;

        BlockPos pos = mop.getBlockPos();
        if (mc.level.getBlockState(pos).getBlock() != Blocks.cake)
            return;

        mc.gameMode.onPlayerRightClick(
                mc.player, mc.level, mc.player.getMainHandItem(),
                pos, mop.sideHit, mop.hitVec
        );
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
        IBlockState clientState = mc.level.getBlockState(pos);

        if (clientState.getBlock() != Blocks.cake) {
            event.setCancelled(true);
            return;
        }

        int serverBites = serverState.getValue(BlockCake.BITES);
        int clientBites = clientState.getValue(BlockCake.BITES);
        if (clientBites > serverBites)
            event.setCancelled(true);
    };

    private float[] getCakeRotations(BlockPos cake) {
        Block block = Blocks.cake;
        block.setBlockBoundsBasedOnState(mc.level, cake);
        double cx = cake.getX() + (block.getBlockBoundsMinX() + block.getBlockBoundsMaxX()) / 2.0;
        double cy = cake.getY() + (block.getBlockBoundsMinY() + block.getBlockBoundsMaxY()) / 2.0;
        double cz = cake.getZ() + (block.getBlockBoundsMinZ() + block.getBlockBoundsMaxZ()) / 2.0;

        Vec3 eyes = mc.player.getEyePosition(1f);
        double dx = cx - eyes.x;
        double dy = cy - eyes.y;
        double dz = cz - eyes.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, dist));
        return new float[]{yaw, pitch};
    }

    private BlockPos findNearestCake() {
        int r = (int) Math.ceil(range.getValue().getInput());
        BlockPos playerPos = new BlockPos(mc.player);
        BlockPos closest = null;
        double closestDist = Double.MAX_VALUE;
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    if (mc.level.getBlockState(pos).getBlock() == Blocks.cake) {
                        double dist = mc.player.getDistanceSq(pos);
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
