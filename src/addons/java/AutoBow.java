import arsenic.module.ModuleTier;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Draws a bow when a player is in range, aims at them on the silent rotation, and releases only once the bow is fully
 * charged (20 ticks) and the ray of the rotation being sent hits that player. If the target goes out of range while the
 * bow is drawn, it is released at whatever the camera aims at, since a drawn bow cannot be put away.
 */
@ModuleInfo(name = "AutoBow", description = "Draws a bow at the nearest enemy and releases once fully charged on the real ray", category = ModuleCategory.COMBAT, tier = ModuleTier.BLATANT)
public class AutoBow extends Module {

    public final DoubleProperty range = new DoubleProperty("Range", new DoubleValue(10, 60, 35, 1));

    private static final int FULL_DRAW = 20;

    private final Random random = new Random();
    private Player target;
    private boolean drawing;

    @Override
    protected void onDisable() {
        target = null;
        drawing = false;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        ItemStack held = mc.player.getMainHandItem();
        boolean hasBow = held != null && held.getItem() instanceof BowItem;
        if (!hasBow || mc.gui.screen() != null) {
            target = null;
            drawing = false;
            return;
        }
        target = nearestEnemy(range.getValue().getInput());
        if (!mc.player.isUsingItem() && target != null) {
            mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
            drawing = true;
        } else if (mc.player.isUsingItem() && target == null && drawing) {
            mc.gameMode.releaseUsingItem(mc.player);
            drawing = false;
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        if (target == null || !drawing) return;
        Vec3 eye = mc.player.getEyePosition(1f);
        Vec3 aim = new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.7, target.getZ());
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
        if (!drawing || target == null || !mc.player.isUsingItem()) return;
        if (mc.player.getTicksUsingItem() < FULL_DRAW) return;
        HitResult ray = event.getRayTraceEntity();
        if (ray instanceof net.minecraft.world.phys.EntityHitResult entityHit && entityHit.getEntity() == target) {
            mc.gameMode.releaseUsingItem(mc.player);
            drawing = false;
        }
    };

    private static Player nearestEnemy(double within) {
        Player best = null;
        for (Player p : mc.level.players()) {
            if (p == mc.player || !p.isAlive() || AntiBot.isBot(p)) continue;
            if (Arsenic.getArsenic().getFriendManager().isFriend(p)) continue;
            if (mc.player.distanceTo(p) > within) continue;
            if (best == null || mc.player.distanceTo(p) < mc.player.distanceTo(best)) best = p;
        }
        return best;
    }
}
