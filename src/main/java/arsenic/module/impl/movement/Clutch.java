package arsenic.module.impl.movement;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventLiving;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.PropertyInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.timer.MSTimer;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;

@ModuleInfo(name = "Clutch", category = ModuleCategory.MOVEMENT)
public class Clutch extends Module {

    public final BooleanProperty damageMode = new BooleanProperty("Damage", true);

    @PropertyInfo(reliesOn = "Damage", value = "true")
    public final DoubleProperty hitWindow = new DoubleProperty("Hit Window", new DoubleValue(100, 3000, 2000, 100));

    public final BooleanProperty heightMode = new BooleanProperty("Height", false);

    @PropertyInfo(reliesOn = "Height", value = "true")
    public final DoubleProperty fallHeight = new DoubleProperty("Fall Height", new DoubleValue(2.0, 20.0, 5.0, 0.5));

    public final DoubleProperty placeDelay = new DoubleProperty("Place Delay", new DoubleValue(0, 500, 100, 10));
    public final BooleanProperty switchBack = new BooleanProperty("Switch Back", true);
    public final BooleanProperty silentRotation = new BooleanProperty("Silent Rotation", false);

    private long lastHitTime;
    private float lastHealth = 20f;
    private final MSTimer placeTimer = new MSTimer();
    private BlockPos targetBlock;
    private EnumFacing targetFacing;
    private int prevSlot = -1;

    @RequiresPlayer
    @EventLink
    public final Listener<EventLiving> onLiving = event -> {
        float health = mc.thePlayer.getHealth();
        if (health < lastHealth) {
            lastHitTime = System.currentTimeMillis();
        }
        lastHealth = health;
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        targetBlock = null;
        targetFacing = null;

        if (!shouldClutch()) return;
        if (!placeTimer.hasTimeElapsed((long) placeDelay.getValue().getInput())) return;

        int slot = getBlockSlot();
        if (slot == -1) return;

        BlockPos under = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 1, mc.thePlayer.posZ);
        BlockData data = findPlacement(under);
        if (data == null) return;

        if (silentRotation.getValue()) {
            prevSlot = mc.thePlayer.inventory.currentItem;
            mc.thePlayer.inventory.currentItem = slot;
            targetBlock = data.position;
            targetFacing = data.facing;
        } else {
            placeBlock(slot, data.position, data.facing);
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        if (targetBlock == null) return;

        float[] rots = getRotations(targetBlock, targetFacing);
        if (rots != null) {
            event.setYaw(rots[0]);
            event.setPitch(rots[1]);
            event.setSpeed(360f);
            event.setPreventDuplicateLook(true);
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onRotationPost = event -> {
        if (targetBlock == null) return;

        MovingObjectPosition mop = rayTrace(targetBlock, targetFacing);
        if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            mc.playerController.onPlayerRightClick(
                    mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem(),
                    targetBlock, targetFacing,
                    getHitVec(targetBlock, targetFacing)
            );
            mc.thePlayer.swingItem();
            placeTimer.reset();

            if (switchBack.getValue() && prevSlot != -1) {
                mc.thePlayer.inventory.currentItem = prevSlot;
            }
        }

        targetBlock = null;
        targetFacing = null;
        prevSlot = -1;
    };

    private boolean shouldClutch() {
        if (mc.thePlayer == null || !mc.thePlayer.isEntityAlive()) return false;
        if (mc.thePlayer.onGround) return false;
        if (mc.thePlayer.motionY >= -0.05) return false;

        boolean damageHit = damageMode.getValue() && System.currentTimeMillis() - lastHitTime <= hitWindow.getValue().getInput();
        boolean heightHit = heightMode.getValue() && mc.thePlayer.fallDistance >= fallHeight.getValue().getInput();

        return damageHit || heightHit;
    }

    private void placeBlock(int slot, BlockPos pos, EnumFacing facing) {
        int prev = mc.thePlayer.inventory.currentItem;
        mc.thePlayer.inventory.currentItem = slot;

        Vec3 hitVec = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem(), pos, facing, hitVec);
        mc.thePlayer.swingItem();
        placeTimer.reset();

        if (switchBack.getValue()) {
            mc.thePlayer.inventory.currentItem = prev;
        }
    }

    private BlockData findPlacement(BlockPos under) {
        if (!mc.theWorld.isAirBlock(under)) return null;

        if (!mc.theWorld.isAirBlock(under.down()))
            return new BlockData(under.down(), EnumFacing.UP);

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue;
                BlockPos neighbor = under.add(x, 0, z);
                if (!mc.theWorld.isAirBlock(neighbor))
                    return new BlockData(neighbor, getFacingTowards(under, neighbor));
            }
        }

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue;
                BlockPos neighbor = under.add(x, -1, z);
                if (!mc.theWorld.isAirBlock(neighbor))
                    return new BlockData(neighbor, EnumFacing.UP);
            }
        }

        return null;
    }

    private EnumFacing getFacingTowards(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        if (dx == 1) return EnumFacing.WEST;
        if (dx == -1) return EnumFacing.EAST;
        if (dz == 1) return EnumFacing.NORTH;
        if (dz == -1) return EnumFacing.SOUTH;
        return EnumFacing.UP;
    }

    private float[] getRotations(BlockPos pos, EnumFacing facing) {
        double dx = pos.getX() + 0.5 - mc.thePlayer.posX;
        double dy = pos.getY() + 0.5 - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double dz = pos.getZ() + 0.5 - mc.thePlayer.posZ;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, dist));
        return new float[]{yaw, pitch};
    }

    private MovingObjectPosition rayTrace(BlockPos pos, EnumFacing facing) {
        return mc.theWorld.rayTraceBlocks(
                new Vec3(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ),
                new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
        );
    }

    private Vec3 getHitVec(BlockPos pos, EnumFacing facing) {
        Vec3 vec = new Vec3(pos);
        if (facing == EnumFacing.UP) vec = vec.addVector(0.5, 1, 0.5);
        else if (facing == EnumFacing.DOWN) vec = vec.addVector(0.5, 0, 0.5);
        else if (facing == EnumFacing.EAST) vec = vec.addVector(1, 0.5, 0.5);
        else if (facing == EnumFacing.WEST) vec = vec.addVector(0, 0.5, 0.5);
        else if (facing == EnumFacing.NORTH) vec = vec.addVector(0.5, 0.5, 0);
        else if (facing == EnumFacing.SOUTH) vec = vec.addVector(0.5, 0.5, 1);
        return vec;
    }

    private int getBlockSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.mainInventory[i];
            if (stack != null && stack.getItem() instanceof ItemBlock && stack.stackSize > 0) {
                Block block = ((ItemBlock) stack.getItem()).getBlock();
                if (isValidBlock(block)) return i;
            }
        }
        return -1;
    }

    private boolean isValidBlock(Block block) {
        if (!block.isFullBlock() && block != Blocks.glass) return false;
        if (block == Blocks.sand || block == Blocks.gravel || block == Blocks.tnt) return false;
        return true;
    }

    private static class BlockData {
        private final BlockPos position;
        private final EnumFacing facing;

        BlockData(BlockPos position, EnumFacing facing) {
            this.position = position;
            this.facing = facing;
        }
    }

    @Override
    protected void onDisable() {
        lastHitTime = 0;
        lastHealth = 20f;
        targetBlock = null;
        targetFacing = null;
        prevSlot = -1;
    }
}
