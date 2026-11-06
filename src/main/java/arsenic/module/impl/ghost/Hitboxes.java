package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.gui.themes.ThemeManager;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.impl.client.TargetManager;
import arsenic.module.property.ShowWhen;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.BadPacketsManager;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.rotations.SilentRotationManager;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Makes other players' hitboxes bigger. None mode grows the box your crosshair picks; Grim/Vulcan mode keeps the real
 * box (GrimVulcan) and turns a swing that only missed by the expansion into a silent-rotation hit on the real box instead.
 */
@ModuleInfo(name = "Hitboxes", description = "Expands entity hitboxes", category = ModuleCategory.COMBAT, tier = ModuleTier.LEGIT)
public class Hitboxes extends Module {

    /** Vanilla attack reach; the Grim/Vulcan redirect never goes past it. */
    private static final double ATTACK_RANGE = 3.0;
    /** Aim this far inside the real box so the server's own ray still lands. */
    private static final double AIM_INSET = 0.05;
    /** Ticks to wait for the silent rotation to land before giving up on a redirected swing. */
    private static final int MAX_AIM_TICKS = 2;

    private static Hitboxes instance;

    public final EnumProperty<AntiCheat> antiCheat = new EnumProperty<>("Anti Cheat", AntiCheat.None);
    public final DoubleProperty expand = new DoubleProperty("Expand", new DoubleValue(0, 1, 0.1, 0.01));
    public final DoubleProperty distance = new DoubleProperty("Distance", new DoubleValue(0.1, 20, 5, 0.1));
    @ShowWhen(property = "Anti Cheat", is = "None")
    public final BooleanProperty playerOnly = new BooleanProperty("Player Only", false);
    @ShowWhen(property = "Anti Cheat", is = "GrimVulcan")
    public final BooleanProperty ignoreWalls = new BooleanProperty("Ignore Walls", false);
    @ShowWhen(property = "Anti Cheat", is = "GrimVulcan")
    public final BooleanProperty skipShielding = new BooleanProperty("Skip Shielding", true);
    public final BooleanProperty render = new BooleanProperty("Render Hitboxes", true);

    /** Closest player the Grim/Vulcan redirect would hit, refreshed each tick (also coloured differently in the render). */
    private Player candidate;
    /** Swing being redirected, and how many ticks the rotation has had to land. */
    private Player pending;
    private int aimTicks;

    public Hitboxes() {
        instance = this;
    }

    @Override
    public String getHudInfo() {
        return antiCheat.getValue().toString();
    }

    /** Extra pick radius for an entity, called from the Entity mixin; 0 unless None mode applies to it. */
    public static float extraPickRadius(Entity entity) {
        Hitboxes h = instance;
        if (h == null || !h.isEnabled() || h.antiCheat.getValue() != AntiCheat.None)
            return 0f;
        Player self = h.mc.player;
        if (self == null || entity == self || !entity.level().isClientSide())
            return 0f;
        if (entity instanceof Player p) {
            if (!TargetManager.isValidTarget(p))
                return 0f;
        } else if (h.playerOnly.getValue()) {
            return 0f;
        }
        double range = h.distance.getValue().getInput();
        if (self.distanceToSqr(entity) > range * range)
            return 0f;
        return (float) h.expand.getValue().getInput();
    }

    /**
     * Called at the head of a left click. In Grim/Vulcan mode, a click that missed only by the expansion is taken over:
     * vanilla's miss swing is cancelled and the real box is hit next tick through the silent rotation.
     * @return true to cancel the vanilla click
     */
    public static boolean interceptAttack() {
        Hitboxes h = instance;
        if (h == null || !h.isEnabled() || h.antiCheat.getValue() != AntiCheat.GrimVulcan)
            return false;
        return h.tryRedirect();
    }

    private boolean tryRedirect() {
        if (mc.player == null || mc.level == null || pending != null || mc.missTime > 0 || mc.player.isHandsBusy())
            return false;
        HitResult hit = mc.hitResult;
        if (hit == null || hit.getType() != HitResult.Type.MISS)
            return false;
        Player target = findCandidate();
        if (target == null)
            return false;
        Vec3 eyes = mc.player.getEyePosition(1f);
        if (target.getBoundingBox().inflate(expand.getValue().getInput()).clip(eyes, hit.getLocation()).isEmpty())
            return false;
        pending = target;
        aimTicks = 0;
        return true;
    }

    private Player findCandidate() {
        Player best = null;
        double bestDist = ATTACK_RANGE;
        for (Player p : mc.level.players()) {
            if (p == mc.player || !p.isAlive() || !TargetManager.isValidTarget(p))
                continue;
            double d = mc.player.distanceTo(p);
            if (d > bestDist)
                continue;
            if (!ignoreWalls.getValue() && !mc.player.hasLineOfSight(p))
                continue;
            if (skipShielding.getValue() && isShielding(p))
                continue;
            best = p;
            bestDist = d;
        }
        return best;
    }

    private static boolean isShielding(Player p) {
        return p.isUsingItem() && p.getUseItem().is(Items.SHIELD);
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        candidate = antiCheat.getValue() == AntiCheat.GrimVulcan ? findCandidate() : null;
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onSilentRotation = event -> {
        if (pending == null)
            return;
        if (antiCheat.getValue() != AntiCheat.GrimVulcan || !pending.isAlive() || pending.isRemoved()
                || mc.player.distanceTo(pending) > ATTACK_RANGE
                || (skipShielding.getValue() && isShielding(pending))) {
            pending = null;
            return;
        }

        Vec3 eyes = mc.player.getEyePosition(1f);
        AABB real = pending.getBoundingBox();
        SilentRotationManager rotations = Arsenic.getArsenic().getSilentRotationManager();

        // the rotation sent with last tick's movement packet already looks at the real box: hit now, before this
        // tick's movement packet (attack, then swing)
        if (aimTicks > 0 && looksAt(eyes, rotations.yaw, rotations.pitch, real)) {
            if (!BadPacketsManager.serverUsingItem()) {
                aim(event, eyes, real);
                mc.gameMode.attack(mc.player, pending);
                PlayerUtils.swingItem();
            }
            pending = null;
            return;
        }
        if (aimTicks++ >= MAX_AIM_TICKS) {
            pending = null;
            return;
        }
        aim(event, eyes, real);
    };

    /** Silently turns to the point of the real box nearest to where the crosshair is pointing. */
    private void aim(EventSilentRotation event, Vec3 eyes, AABB real) {
        AABB inner = real.deflate(AIM_INSET);
        Vec3 look = mc.player.getViewVector(1f);
        Vec3 end = eyes.add(look.scale(ATTACK_RANGE + 1));
        Optional<Vec3> entry = real.inflate(expand.getValue().getInput()).clip(eyes, end);
        Vec3 from = entry.orElse(inner.getCenter());
        Vec3 point = new Vec3(Mth.clamp(from.x, inner.minX, inner.maxX), Mth.clamp(from.y, inner.minY, inner.maxY),
                Mth.clamp(from.z, inner.minZ, inner.maxZ));
        float[] rot = RotationUtils.rotationsTo(eyes, point);
        // keep the yaw next to the player's own so the silent rotation takes the short way round
        float yaw = mc.player.getYRot() + Mth.wrapDegrees(rot[0] - mc.player.getYRot());
        event.setYaw(yaw);
        event.setPitch(rot[1]);
        event.setSpeed(360f);
        event.setSmoothing(false);
    }

    private static boolean looksAt(Vec3 eyes, float yaw, float pitch, AABB box) {
        Vec3 end = eyes.add(Entity.calculateViewVector(pitch, yaw).scale(ATTACK_RANGE));
        return box.contains(eyes) || box.clip(eyes, end).isPresent();
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRender = event -> {
        if (!render.getValue())
            return;
        double range = distance.getValue().getInput();
        double grow = expand.getValue().getInput();
        int main = ThemeManager.getMainColor();
        int darker = ThemeManager.getDarkerColor();
        for (Player p : mc.level.players()) {
            if (p == mc.player || !p.isAlive() || mc.player.distanceTo(p) > range || !TargetManager.isValidTarget(p))
                continue;
            AABB box = RenderUtils.interpolatedBox(p).inflate(grow);
            if (p == candidate || p == pending)
                RenderUtils.renderBox(box, RenderUtils.withAlpha(main, 120), true, true);
            else
                RenderUtils.drawBoundingBox(box, darker);
        }
    };

    @Override
    protected void onDisable() {
        candidate = null;
        pending = null;
        aimTicks = 0;
    }

    public enum AntiCheat {
        None, GrimVulcan
    }
}
