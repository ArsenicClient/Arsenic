package arsenic.module.impl.blatant;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventSilentRotation;
import arsenic.gui.themes.ThemeManager;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.impl.client.AntiBot;
import arsenic.module.property.Group;
import arsenic.module.property.PropertyGroup;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.FolderProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.ItemUtils;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.rotations.SilentRotationManager;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * When a left click misses (or would start breaking a block with a sword), it turns your server-side aim onto the
 * closest enemy in reach and lands the hit there instead. Your camera never moves.
 */
@ModuleInfo(name = "SilentAim", description = "Redirects missed hits onto the closest enemy in reach", category = ModuleCategory.COMBAT, tier = ModuleTier.BLATANT)
public class SilentAim extends Module {

    /** Regions of the hitbox the aim may point at, as a fraction of the hitbox height. */
    private static final String[] POINT_NAMES = {"Head", "Neck", "Body", "Arms", "Thighs", "Legs", "Feet"};
    private static final double[] POINT_HEIGHTS = {0.95, 0.85, 0.7, 0.65, 0.35, 0.2, 0.05};
    private static final boolean[] POINT_DEFAULTS = {true, false, true, false, false, false, false};

    public final BooleanProperty playerOnly = new BooleanProperty("Player Only", true);
    public final BooleanProperty weaponOnly = new BooleanProperty("Weapon Only", false);
    public final BooleanProperty continuousTracking = new BooleanProperty("Continuous Tracking", false);
    public final DoubleProperty maxYaw = new DoubleProperty("Max Yaw", new DoubleValue(0, 180, 180, 1));
    public final DoubleProperty maxPitch = new DoubleProperty("Max Pitch", new DoubleValue(0, 90, 90, 1));

    private final BooleanProperty[] aimPoints = new BooleanProperty[POINT_NAMES.length];
    public final FolderProperty aimPointFolder;

    @PropertyGroup(Group.VISUAL)
    public final BooleanProperty renderDot = new BooleanProperty("Render Dot", false);
    @PropertyGroup(Group.VISUAL)
    public final DoubleProperty dotSize = new DoubleProperty("Dot Size", new DoubleValue(0.02, 0.5, 0.1, 0.01));

    /** Target armed by a redirected click this tick, attacked once the rotation is committed. */
    private Entity pending;
    /** Entity the rotation is aimed at this tick (pending or tracked). */
    private Entity aimed;
    /** Where the silent aim points, for the dot. */
    private Vec3 aimPoint;

    public SilentAim() {
        for (int i = 0; i < POINT_NAMES.length; i++)
            aimPoints[i] = new BooleanProperty(POINT_NAMES[i], POINT_DEFAULTS[i]);
        aimPointFolder = new FolderProperty("Aim Points", aimPoints);
        dotSize.setVisible(renderDot::getValue);
    }

    @Override
    protected void onEnable() {
        reset();
    }

    @Override
    protected void onDisable() {
        reset();
    }

    private void reset() {
        pending = null;
        aimed = null;
        aimPoint = null;
    }

    /**
     * Called from the left click (Minecraft.startAttack) before vanilla handles it. Returns true when the click was
     * taken over: vanilla's miss (or block break) is skipped and the hit lands on the target from the rotation hook.
     */
    public boolean interceptClick() {
        if (!isEnabled() || mc.player == null || mc.level == null || mc.gui.screen() != null || !mc.isWindowActive())
            return false;
        if (killAuraActive() || !holdingAllowedItem())
            return false;
        HitResult hit = mc.hitResult;
        if (hit == null)
            return false;
        boolean sword = ItemUtils.isSword(mc.player.getMainHandItem());
        if (!(hit.getType() == HitResult.Type.MISS || (hit.getType() == HitResult.Type.BLOCK && sword)))
            return false;
        Entity target = findTarget();
        if (target == null || !withinCaps(aimPointFor(target)))
            return false;
        pending = target;
        return true;
    }

    /** Called from Minecraft.continueAttack: true stops a sword from mining while an enemy is there to hit instead. */
    public boolean blockMining() {
        if (!isEnabled() || mc.player == null || mc.level == null || mc.gui.screen() != null || !mc.isWindowActive())
            return false;
        if (killAuraActive())
            return false;
        HitResult hit = mc.hitResult;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK || !ItemUtils.isSword(mc.player.getMainHandItem()))
            return false;
        Entity target = findTarget();
        return target != null && withinCaps(aimPointFor(target));
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        aimed = null;
        if (killAuraActive()) {
            pending = null;
            aimPoint = null;
            return;
        }
        Entity target = pending;
        if (target != null && !valid(target)) {
            target = null;
            pending = null;
        }
        if (target == null && continuousTracking.getValue() && holdingAllowedItem())
            target = findTarget();
        if (target == null) {
            aimPoint = null;
            return;
        }
        Vec3 point = aimPointFor(target);
        if (!withinCaps(point)) {
            pending = null;
            aimPoint = null;
            return;
        }
        float[] rots = RotationUtils.rotationsTo(mc.player.getEyePosition(1f), point);
        aimed = target;
        aimPoint = point;
        event.setYaw(rots[0]);
        event.setPitch(Mth.clamp(rots[1], -90f, 90f));
        event.setSpeed(180f);
        event.setSmoothing(false);
        event.setMovementFix(SilentRotationManager.MovementFix.SILENT);
    };

    /** Lands the redirected hit once the aim is committed, before the movement packet goes out. */
    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onPost = event -> {
        Entity target = pending;
        pending = null;
        if (target == null || target != aimed || !valid(target))
            return;
        Vec3 eyes = mc.player.getEyePosition(1f);
        Vec3 end = eyes.add(Entity.calculateViewVector(event.getPitch(), event.getYaw()).scale(mc.player.entityInteractionRange()));
        // the committed (GCD patched) look must still run through the hitbox
        if (target.getBoundingBox().inflate(target.getPickRadius()).clip(eyes, end).isEmpty())
            return;
        // attack before swing, as a 1.9+ client does
        mc.gameMode.attack(mc.player, target);
        PlayerUtils.swingItem();
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRender = event -> {
        Vec3 point = aimPoint;
        if (!renderDot.getValue() || point == null)
            return;
        double r = dotSize.getValue().getInput();
        RenderUtils.renderBox(new AABB(point.x - r, point.y - r, point.z - r, point.x + r, point.y + r, point.z + r),
                RenderUtils.withAlpha(ThemeManager.getMainColor(), 200), true, true);
    };

    private boolean killAuraActive() {
        KillAura aura = Arsenic.getArsenic().getModuleManager().getModuleByClass(KillAura.class);
        return aura != null && aura.isEnabled();
    }

    private boolean holdingAllowedItem() {
        if (!weaponOnly.getValue())
            return true;
        ItemStack held = mc.player.getMainHandItem();
        return ItemUtils.isSword(held) || ItemUtils.isAxe(held) || ItemUtils.isMace(held);
    }

    /** Closest valid enemy whose aim point is inside reach. */
    private Entity findTarget() {
        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!valid(e))
                continue;
            double d = mc.player.distanceToSqr(e);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    private boolean valid(Entity e) {
        if (e == null || e == mc.player || !e.isAlive() || e.isSpectator() || !e.isPickable())
            return false;
        if (!(e instanceof LivingEntity) || e instanceof ArmorStand)
            return false;
        if (playerOnly.getValue() && !(e instanceof Player))
            return false;
        if (e instanceof Player p && (Arsenic.getArsenic().getFriendManager().isFriend(p) || AntiBot.isBot(p)))
            return false;
        Vec3 eyes = mc.player.getEyePosition(1f);
        Vec3 point = aimPointFor(e);
        double range = mc.player.entityInteractionRange();
        if (eyes.distanceToSqr(point) > range * range)
            return false;
        if (!mc.player.hasLineOfSight(e))
            return false;
        // no block between the eye and the chosen point
        return mc.level.clip(new ClipContext(eyes, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player))
                .getType() == HitResult.Type.MISS;
    }

    /**
     * Each enabled region gives a candidate: X/Z clamped to the hitbox (vanilla's nearest point) and Y at that
     * region's height. The candidate closest to the eye wins; with none enabled it falls back to the target's eyes.
     */
    private Vec3 aimPointFor(Entity target) {
        Vec3 eyes = mc.player.getEyePosition(1f);
        AABB box = target.getBoundingBox();
        double height = box.maxY - box.minY;
        if (height <= 0)
            return target.getEyePosition();
        double x = Mth.clamp(eyes.x, box.minX, box.maxX);
        double z = Mth.clamp(eyes.z, box.minZ, box.maxZ);
        double horizontal = (eyes.x - x) * (eyes.x - x) + (eyes.z - z) * (eyes.z - z);
        double bestDist = Double.POSITIVE_INFINITY;
        double bestY = Double.NaN;
        for (int i = 0; i < aimPoints.length; i++) {
            if (!aimPoints[i].getValue())
                continue;
            double y = box.minY + POINT_HEIGHTS[i] * height;
            double dy = eyes.y - y;
            double dist = horizontal + dy * dy;
            if (dist < bestDist) {
                bestDist = dist;
                bestY = y;
            }
        }
        return Double.isNaN(bestY) ? target.getEyePosition() : new Vec3(x, bestY, z);
    }

    /** Whether looking at {@code point} stays inside the Max Yaw / Max Pitch caps from your real view. */
    private boolean withinCaps(Vec3 point) {
        float[] rots = RotationUtils.rotationsTo(mc.player.getEyePosition(1f), point);
        return Math.abs(Mth.wrapDegrees(rots[0] - mc.player.getYRot())) <= maxYaw.getValue().getInput()
                && Math.abs(rots[1] - mc.player.getXRot()) <= maxPitch.getValue().getInput();
    }
}
