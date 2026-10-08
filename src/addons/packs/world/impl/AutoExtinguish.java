import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.rotations.SilentRotationManager;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

/**
 * When you are on fire, pours a water bucket at your feet to put it out. The camera turns down to the top of the block
 * under you on the silent rotation, and the bucket is used only when the ray of the rotation being sent hits that face.
 * The slot is put back on the next tick.
 */
@ModuleInfo(name = "AutoExtinguish", description = "Puts out fire by pouring water at your feet", category = ModuleCategory.PLAYER)
public class AutoExtinguish extends Module {

    private int restoreSlot = -1;
    private boolean aiming;
    private float aimYaw, aimPitch;
    private BlockPos support;

    @Override
    protected void onDisable() {
        aiming = false;
        restore();
    }

    @Override
    public boolean isSwappingHotbar() {
        return restoreSlot != -1;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        restore();
        aiming = false;
        if (mc.currentScreen != null || !mc.thePlayer.isBurning() || findBucket() == -1) return;
        support = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 0.2, mc.thePlayer.posZ);
        if (!mc.theWorld.isBlockLoaded(support)) return;
        Vec3 point = new Vec3(support.getX() + 0.5, support.getY() + 1.0, support.getZ() + 0.5);
        float[] r = RotationUtils.rotationsTo(mc.thePlayer.getPositionEyes(1f), point);
        aimYaw = r[0];
        aimPitch = r[1];
        aiming = true;
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        if (!aiming) return;
        event.setYaw(aimYaw);
        event.setPitch(aimPitch);
        event.setSpeed(120);
        event.setPreventDuplicateLook(true);
        event.setBlockUserInput(true);
        event.setMovementFix(SilentRotationManager.MovementFix.SILENT);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onPost = event -> {
        if (!aiming || restoreSlot != -1) return;
        MovingObjectPosition ray = event.getRayTrace();
        if (ray == null || ray.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || !ray.getBlockPos().equals(support) || ray.sideHit != EnumFacing.UP) return;
        int slot = findBucket();
        if (slot == -1) return;
        restoreSlot = mc.thePlayer.inventory.currentItem;
        mc.thePlayer.inventory.currentItem = slot;
        ItemStack bucket = mc.thePlayer.inventory.getCurrentItem();
        if (bucket != null) mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, bucket);
        aiming = false;
    };

    private void restore() {
        if (restoreSlot != -1 && mc.thePlayer != null) mc.thePlayer.inventory.currentItem = restoreSlot;
        restoreSlot = -1;
    }

    private static int findBucket() {
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.thePlayer.inventory.mainInventory[i];
            if (s != null && s.getItem() == Items.water_bucket) return i;
        }
        return -1;
    }
}
