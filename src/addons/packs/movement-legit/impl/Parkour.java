import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventMovementInput;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import net.minecraft.block.Block;
import net.minecraft.util.BlockPos;

/**
 * Presses jump at a block edge while you sprint forward, so you take the jump instead of walking off the block. It only
 * presses the jump key: it never changes your speed or direction. Nothing happens while sneaking, in the air, or with a
 * GUI open.
 */
@ModuleInfo(name = "Parkour", description = "Jumps at block edges while you sprint forward", category = ModuleCategory.MOVEMENT)
public class Parkour extends Module {

    private boolean jumpNow;

    @Override
    protected void onDisable() {
        jumpNow = false;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        jumpNow = false;
        if (mc.currentScreen != null || !mc.thePlayer.onGround || !mc.thePlayer.isSprinting()
                || mc.thePlayer.isSneaking() || mc.thePlayer.moveForward <= 0) return;
        double yaw = Math.toRadians(mc.thePlayer.rotationYaw);
        double ahead = 0.9;
        int ax = (int) Math.floor(mc.thePlayer.posX - Math.sin(yaw) * ahead);
        int az = (int) Math.floor(mc.thePlayer.posZ + Math.cos(yaw) * ahead);
        int fy = (int) Math.floor(mc.thePlayer.posY);
        BlockPos below = new BlockPos(ax, fy - 1, az);
        if (!mc.theWorld.isBlockLoaded(below)) return;
        boolean groundHere = !isAir(new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 0.1, mc.thePlayer.posZ));
        jumpNow = groundHere && isAir(below);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventMovementInput> onInput = event -> {
        if (jumpNow) {
            event.setJump(true);
            jumpNow = false;
        }
    };

    private static boolean isAir(BlockPos p) {
        Block b = mc.theWorld.getBlockState(p).getBlock();
        return b.getMaterial().isReplaceable();
    }
}
