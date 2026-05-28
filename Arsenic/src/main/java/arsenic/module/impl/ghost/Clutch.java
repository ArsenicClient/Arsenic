package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventLiving;
import arsenic.event.impl.EventUpdate;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.timer.MSTimer;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;

@ModuleInfo(name = "Clutch", category = ModuleCategory.GHOST, dev = true)
public class Clutch extends Module {

    public final DoubleProperty delay = new DoubleProperty("Place Delay", new DoubleValue(0, 500, 100, 10));
    public final BooleanProperty switchBack = new BooleanProperty("Switch Back", true);

    private long lastHitTime;
    private float lastHealth = 20f;
    private final MSTimer placeTimer = new MSTimer();

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
    public final Listener<EventUpdate.Pre> onUpdate = event -> {
        if (shouldClutch()) {
            placeBlockUnder();
        }
    };

    private boolean shouldClutch() {
        if (!mc.thePlayer.isEntityAlive()) return false;

        long timeSinceHit = System.currentTimeMillis() - lastHitTime;
        if (timeSinceHit > 2000) return false;

        if (mc.thePlayer.onGround) return false;
        if (mc.thePlayer.motionY >= -0.05) return false;

        return true;
    }

    private void placeBlockUnder() {
        if (!placeTimer.hasTimeElapsed((long) delay.getValue().getInput())) return;

        int slot = getBlockSlot();
        if (slot == -1) return;

        int prevSlot = mc.thePlayer.inventory.currentItem;
        mc.thePlayer.inventory.currentItem = slot;

        BlockPos under = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 1, mc.thePlayer.posZ);

        if (mc.theWorld.isAirBlock(under)) {
            BlockPos neighbor = null;
            EnumFacing side = null;

            if (!mc.theWorld.isAirBlock(under.down())) {
                neighbor = under.down();
                side = EnumFacing.UP;
            } else if (!mc.theWorld.isAirBlock(under.north())) {
                neighbor = under.north();
                side = EnumFacing.SOUTH;
            } else if (!mc.theWorld.isAirBlock(under.south())) {
                neighbor = under.south();
                side = EnumFacing.NORTH;
            } else if (!mc.theWorld.isAirBlock(under.east())) {
                neighbor = under.east();
                side = EnumFacing.WEST;
            } else if (!mc.theWorld.isAirBlock(under.west())) {
                neighbor = under.west();
                side = EnumFacing.EAST;
            }

            if (neighbor != null) {
                Vec3 hitVec = new Vec3(neighbor.getX() + 0.5, neighbor.getY() + 0.5, neighbor.getZ() + 0.5);
                mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem(), neighbor, side, hitVec);
                mc.thePlayer.swingItem();
                placeTimer.reset();
            }
        }

        if (switchBack.getValue()) {
            mc.thePlayer.inventory.currentItem = prevSlot;
        }
    }

    private int getBlockSlot() {
        for (int i = 0; i < 9; i++) {
            net.minecraft.item.ItemStack itemStack = mc.thePlayer.inventory.mainInventory[i];
            if (itemStack != null && itemStack.getItem() instanceof net.minecraft.item.ItemBlock && itemStack.stackSize > 0) {
                net.minecraft.block.Block block = ((net.minecraft.item.ItemBlock) itemStack.getItem()).getBlock();
                if (block.isFullBlock() || block == Blocks.glass) {
                    return i;
                }
            }
        }
        return -1;
    }

    @Override
    protected void onDisable() {
        lastHitTime = 0;
        lastHealth = 20f;
    }
}
