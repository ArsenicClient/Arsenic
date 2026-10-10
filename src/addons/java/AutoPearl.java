import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.AntiBot;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.rotations.SilentRotationManager;
import arsenic.utils.timer.MSTimer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Vec3;

/**
 * Throws an ender pearl at the nearest enemy at a distance, aiming the pearl at their feet. The throw happens only when the
 * rotation being sent points at that spot (within a few degrees), and then at most once per cooldown. The pearl's arc is
 * not modelled, so it lands roughly where the aim point is; expect it to miss at long range.
 */
@ModuleInfo(name = "AutoPearl", description = "Throws an ender pearl at an enemy from a distance, aimed at their feet", category = ModuleCategory.COMBAT)
public class AutoPearl extends Module {

    public final DoubleProperty minRange = new DoubleProperty("Min Range", new DoubleValue(6, 30, 8, 1));
    public final DoubleProperty maxRange = new DoubleProperty("Max Range", new DoubleValue(8, 40, 22, 1));
    public final DoubleProperty cooldown = new DoubleProperty("Cooldown (s)", new DoubleValue(1, 20, 6, 0.5));

    private final MSTimer throwTimer = MSTimer.expired();
    private EntityPlayer target;
    private int restoreSlot = -1;

    @Override
    protected void onDisable() {
        target = null;
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
        if (mc.currentScreen != null) {
            target = null;
            return;
        }
        target = nearestEnemy(minRange.getValue().getInput(), maxRange.getValue().getInput());
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        if (target == null || findPearl() == -1) return;
        Vec3 eye = mc.thePlayer.getPositionEyes(1f);
        float[] r = RotationUtils.rotationsTo(eye, new Vec3(target.posX, target.posY, target.posZ));
        event.setYaw(r[0]);
        event.setPitch(r[1]);
        event.setSpeed(90);
        event.setPreventDuplicateLook(true);
        event.setBlockUserInput(true);
        event.setMovementFix(SilentRotationManager.MovementFix.SILENT);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onPost = event -> {
        if (target == null || restoreSlot != -1 || !throwTimer.hasTimeElapsed((long) (cooldown.getValue().getInput() * 1000), false)) return;
        int slot = findPearl();
        if (slot == -1) return;
        Vec3 eye = mc.thePlayer.getPositionEyes(1f);
        float[] want = RotationUtils.rotationsTo(eye, new Vec3(target.posX, target.posY, target.posZ));
        // act only when the rotation being sent is on the aim point
        if (Math.abs(RotationUtils.getYawDifference(event.getYaw(), want[0])) > 3 || Math.abs(event.getPitch() - want[1]) > 3) return;

        restoreSlot = mc.thePlayer.inventory.currentItem;
        mc.thePlayer.inventory.currentItem = slot;
        ItemStack pearl = mc.thePlayer.inventory.getCurrentItem();
        if (pearl != null) mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, pearl);
        throwTimer.reset();
        target = null;
    };

    private void restore() {
        if (restoreSlot != -1 && mc.thePlayer != null) mc.thePlayer.inventory.currentItem = restoreSlot;
        restoreSlot = -1;
    }

    private static int findPearl() {
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.thePlayer.inventory.mainInventory[i];
            if (s != null && s.getItem() == Items.ender_pearl) return i;
        }
        return -1;
    }

    private static EntityPlayer nearestEnemy(double min, double max) {
        EntityPlayer best = null;
        for (EntityPlayer p : mc.theWorld.playerEntities) {
            if (p == mc.thePlayer || p.isDead || AntiBot.isBot(p)) continue;
            if (Arsenic.getArsenic().getFriendManager().isFriend(p)) continue;
            double d = mc.thePlayer.getDistanceToEntity(p);
            if (d < min || d > max) continue;
            if (best == null || d < mc.thePlayer.getDistanceToEntity(best)) best = p;
        }
        return best;
    }
}
