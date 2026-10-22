import arsenic.module.ModuleTier;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventMovementInput;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;

/**
 * Presses jump at a block edge while you sprint forward, so you take the jump instead of walking off the block. It only
 * presses the jump key: it never changes your speed or direction. Nothing happens while sneaking, in the air, or with a
 * GUI open.
 */
@ModuleInfo(name = "Parkour", description = "Jumps at block edges while you sprint forward", category = ModuleCategory.MOVEMENT, tier = ModuleTier.LEGIT)
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
        if (mc.gui.screen() != null || !mc.player.onGround() || !mc.player.isSprinting()
                || mc.player.isShiftKeyDown() || mc.player.zza <= 0) return;
        double yaw = Math.toRadians(mc.player.getYRot());
        double ahead = 0.9;
        int ax = (int) Math.floor(mc.player.getX() - Math.sin(yaw) * ahead);
        int az = (int) Math.floor(mc.player.getZ() + Math.cos(yaw) * ahead);
        int fy = (int) Math.floor(mc.player.getY());
        BlockPos below = new BlockPos(ax, fy - 1, az);
        if (!mc.level.isLoaded(below)) return;
        boolean groundHere = !isAir(BlockPos.containing(mc.player.getX(), mc.player.getY() - 0.1, mc.player.getZ()));
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
        Block b = mc.level.getBlockState(p).getBlock();
        return b.defaultBlockState().canBeReplaced();
    }
}
