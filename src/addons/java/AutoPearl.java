import arsenic.module.ModuleTier;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Throws an ender pearl at the nearest enemy at a distance, aiming the pearl at their feet. The throw happens only when the
 * rotation being sent points at that spot (within a few degrees), and then at most once per cooldown. The pearl's arc is
 * not modelled, so it lands roughly where the aim point is; expect it to miss at long range.
 */
@ModuleInfo(name = "AutoPearl", description = "Throws an ender pearl at an enemy from a distance, aimed at their feet", category = ModuleCategory.COMBAT, tier = ModuleTier.BLATANT)
public class AutoPearl extends Module {

    public final DoubleProperty minRange = new DoubleProperty("Min Range", new DoubleValue(6, 30, 8, 1));
    public final DoubleProperty maxRange = new DoubleProperty("Max Range", new DoubleValue(8, 40, 22, 1));
    public final DoubleProperty cooldown = new DoubleProperty("Cooldown (s)", new DoubleValue(1, 20, 6, 0.5));

    private final MSTimer throwTimer = MSTimer.expired();
    private Player target;
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
        if (mc.gui.screen() != null) {
            target = null;
            return;
        }
        target = nearestEnemy(minRange.getValue().getInput(), maxRange.getValue().getInput());
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        if (target == null || findPearl() == -1) return;
        Vec3 eye = mc.player.getEyePosition(1f);
        float[] r = RotationUtils.rotationsTo(eye, new Vec3(target.getX(), target.getY(), target.getZ()));
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
        Vec3 eye = mc.player.getEyePosition(1f);
        float[] want = RotationUtils.rotationsTo(eye, new Vec3(target.getX(), target.getY(), target.getZ()));
        // act only when the rotation being sent is on the aim point
        if (Math.abs(RotationUtils.getYawDifference(event.getYaw(), want[0])) > 3 || Math.abs(event.getPitch() - want[1]) > 3) return;

        restoreSlot = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(slot);
        ItemStack pearl = mc.player.getMainHandItem();
        if (pearl != null) mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
        throwTimer.reset();
        target = null;
    };

    private void restore() {
        if (restoreSlot != -1 && mc.player != null) mc.player.getInventory().setSelectedSlot(restoreSlot);
        restoreSlot = -1;
    }

    private static int findPearl() {
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (s != null && s.getItem() == Items.ENDER_PEARL) return i;
        }
        return -1;
    }

    private static Player nearestEnemy(double min, double max) {
        Player best = null;
        for (Player p : mc.level.players()) {
            if (p == mc.player || !p.isAlive() || AntiBot.isBot(p)) continue;
            if (Arsenic.getArsenic().getFriendManager().isFriend(p)) continue;
            double d = mc.player.distanceTo(p);
            if (d < min || d > max) continue;
            if (best == null || d < mc.player.distanceTo(best)) best = p;
        }
        return best;
    }
}
