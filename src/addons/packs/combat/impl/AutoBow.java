import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.AntiBot;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.main.Arsenic;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.rotations.SilentRotationManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.Random;

/**
 * Draws a bow when a player is in range, aims at them on the silent rotation, and releases only once the bow is fully
 * charged (20 ticks) and the ray of the rotation being sent hits that player. If the target goes out of range while the
 * bow is drawn, it is released at whatever the camera aims at, since a drawn bow cannot be put away.
 */
@ModuleInfo(name = "AutoBow", description = "Draws a bow at the nearest enemy and releases once fully charged on the real ray", category = ModuleCategory.COMBAT)
public class AutoBow extends Module {

    public final DoubleProperty range = new DoubleProperty("Range", new DoubleValue(10, 60, 35, 1));

    private static final int FULL_DRAW = 20;

    private final Random random = new Random();
    private EntityPlayer target;
    private boolean drawing;

    @Override
    protected void onDisable() {
        target = null;
        drawing = false;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        ItemStack held = mc.thePlayer.getHeldItem();
        boolean hasBow = held != null && held.getItem() instanceof ItemBow;
        if (!hasBow || mc.currentScreen != null) {
            target = null;
            drawing = false;
            return;
        }
        target = nearestEnemy(range.getValue().getInput());
        if (!mc.thePlayer.isUsingItem() && target != null) {
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, held);
            drawing = true;
        } else if (mc.thePlayer.isUsingItem() && target == null && drawing) {
            mc.playerController.onStoppedUsingItem(mc.thePlayer);
            drawing = false;
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        if (target == null || !drawing) return;
        Vec3 eye = mc.thePlayer.getPositionEyes(1f);
        Vec3 aim = new Vec3(target.posX, target.posY + target.height * 0.7, target.posZ);
        float[] r = RotationUtils.rotationsTo(eye, aim);
        event.setYaw(r[0]);
        event.setPitch(r[1]);
        event.setSpeed((float) (120 * (0.95 + random.nextDouble() * 0.1)));
        event.setPreventDuplicateLook(true);
        event.setBlockUserInput(true);
        event.setMovementFix(SilentRotationManager.MovementFix.SILENT);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onPost = event -> {
        if (!drawing || target == null || !mc.thePlayer.isUsingItem()) return;
        if (mc.thePlayer.getItemInUseDuration() < FULL_DRAW) return;
        MovingObjectPosition ray = event.getRayTraceEntity();
        if (ray != null && ray.entityHit == target) {
            mc.playerController.onStoppedUsingItem(mc.thePlayer);
            drawing = false;
        }
    };

    private static EntityPlayer nearestEnemy(double within) {
        EntityPlayer best = null;
        for (EntityPlayer p : mc.theWorld.playerEntities) {
            if (p == mc.thePlayer || p.isDead || AntiBot.isBot(p)) continue;
            if (Arsenic.getArsenic().getFriendManager().isFriend(p)) continue;
            if (mc.thePlayer.getDistanceToEntity(p) > within) continue;
            if (best == null || mc.thePlayer.getDistanceToEntity(p) < mc.thePlayer.getDistanceToEntity(best)) best = p;
        }
        return best;
    }
}
