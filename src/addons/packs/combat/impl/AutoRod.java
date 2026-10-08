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
 * Keeps a fishing rod in hand while an enemy is in close range. It aims at them on the silent rotation, casts once the
 * rotation being sent is on them, and reels in when the hook is pulled down (the bite). Your slot is put back when the
 * module is disabled.
 */
@ModuleInfo(name = "AutoRod", description = "Casts a fishing rod at a nearby enemy and reels in on a bite", category = ModuleCategory.COMBAT)
public class AutoRod extends Module {

    public final DoubleProperty range = new DoubleProperty("Range", new DoubleValue(3, 6, 5, 0.1));
    public final DoubleProperty cooldown = new DoubleProperty("Cooldown (ms)", new DoubleValue(200, 1500, 600, 50));

    private final MSTimer action = MSTimer.expired();
    private EntityPlayer target;
    private int savedSlot = -1;

    @Override
    protected void onDisable() {
        target = null;
        if (savedSlot != -1 && mc.thePlayer != null) mc.thePlayer.inventory.currentItem = savedSlot;
        savedSlot = -1;
    }

    @Override
    public boolean isSwappingHotbar() {
        return savedSlot != -1;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (mc.currentScreen != null) {
            target = null;
            return;
        }
        ItemStack held = mc.thePlayer.inventory.getCurrentItem();
        if (held == null || held.getItem() != Items.fishing_rod) {
            int slot = findRod();
            if (slot == -1) return;
            if (savedSlot == -1) savedSlot = mc.thePlayer.inventory.currentItem;
            mc.thePlayer.inventory.currentItem = slot;
            return;
        }
        target = nearestEnemy(range.getValue().getInput());
        if (!action.hasTimeElapsed((long) cooldown.getValue().getInput(), false)) return;
        if (mc.thePlayer.fishEntity != null && mc.thePlayer.fishEntity.motionY < -0.04) {
            cast(held);   // bite: reel in
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        if (target == null) return;
        Vec3 eye = mc.thePlayer.getPositionEyes(1f);
        float[] r = RotationUtils.rotationsTo(eye, new Vec3(target.posX, target.posY + target.height * 0.6, target.posZ));
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
        if (target == null || mc.thePlayer.fishEntity != null || !action.hasTimeElapsed((long) cooldown.getValue().getInput(), false)) return;
        Vec3 eye = mc.thePlayer.getPositionEyes(1f);
        float[] want = RotationUtils.rotationsTo(eye, new Vec3(target.posX, target.posY + target.height * 0.6, target.posZ));
        if (Math.abs(RotationUtils.getYawDifference(event.getYaw(), want[0])) > 3 || Math.abs(event.getPitch() - want[1]) > 3) return;
        cast(mc.thePlayer.inventory.getCurrentItem());   // cast: the rod's use with no hook out
    };

    private void cast(ItemStack rod) {
        if (rod != null) mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, rod);
        action.reset();
    }

    private static int findRod() {
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.thePlayer.inventory.mainInventory[i];
            if (s != null && s.getItem() == Items.fishing_rod) return i;
        }
        return -1;
    }

    private static EntityPlayer nearestEnemy(double within) {
        EntityPlayer best = null;
        for (EntityPlayer p : mc.theWorld.playerEntities) {
            if (p == mc.thePlayer || p.isDead || AntiBot.isBot(p)) continue;
            if (Arsenic.getArsenic().getFriendManager().isFriend(p)) continue;
            double d = mc.thePlayer.getDistanceToEntity(p);
            if (d > within) continue;
            if (best == null || d < mc.thePlayer.getDistanceToEntity(best)) best = p;
        }
        return best;
    }
}
