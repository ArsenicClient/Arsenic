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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import arsenic.utils.io.Keys;

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
        if (!Keys.isMouseDown(1) || !autoAim.getValue())
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
        if (!Keys.isMouseDown(1)) {
            pendingCake = null;
            return;
        }

        if (autoAim.getValue()) {
            if (pendingCake != null && mc.level.getBlockState(pendingCake).is(Blocks.CAKE)) {
                AABB bounds = cakeBounds(pendingCake);
                Vec3 hitVec = new Vec3((bounds.minX + bounds.maxX) / 2.0, bounds.maxY, (bounds.minZ + bounds.maxZ) / 2.0);
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(hitVec, Direction.UP, pendingCake, false));
            }
            return;
        }

        if (!(mc.hitResult instanceof BlockHitResult mop) || mop.getType() != HitResult.Type.BLOCK)
            return;

        BlockPos pos = mop.getBlockPos();
        if (!mc.level.getBlockState(pos).is(Blocks.CAKE))
            return;

        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, mop);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventPacket.Incoming.Pre> onBlockUpdate = event -> {
        if (!(event.getPacket() instanceof ClientboundBlockUpdatePacket packet))
            return;

        BlockState serverState = packet.getBlockState();
        if (!serverState.is(Blocks.CAKE))
            return;

        BlockPos pos = packet.getPos();
        BlockState clientState = mc.level.getBlockState(pos);

        if (!clientState.is(Blocks.CAKE)) {
            event.setCancelled(true);
            return;
        }

        int serverBites = serverState.getValue(CakeBlock.BITES);
        int clientBites = clientState.getValue(CakeBlock.BITES);
        if (clientBites > serverBites)
            event.setCancelled(true);
    };

    /** The cake's actual shape in world space - it shrinks as it is eaten. */
    private AABB cakeBounds(BlockPos cake) {
        return mc.level.getBlockState(cake).getShape(mc.level, cake).bounds().move(cake);
    }

    private float[] getCakeRotations(BlockPos cake) {
        Vec3 centre = cakeBounds(cake).getCenter();

        Vec3 eyes = mc.player.getEyePosition(1f);
        double dx = centre.x - eyes.x;
        double dy = centre.y - eyes.y;
        double dz = centre.z - eyes.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, dist));
        return new float[]{yaw, pitch};
    }

    private BlockPos findNearestCake() {
        int r = (int) Math.ceil(range.getValue().getInput());
        BlockPos playerPos = mc.player.blockPosition();
        BlockPos closest = null;
        double closestDist = Double.MAX_VALUE;
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = playerPos.offset(x, y, z);
                    if (mc.level.getBlockState(pos).is(Blocks.CAKE)) {
                        double dist = mc.player.distanceToSqr(Vec3.atCenterOf(pos));
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
