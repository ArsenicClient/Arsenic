package arsenic.module.impl.visual;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.gui.themes.ThemeManager;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.AntiBot;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.minecraft.WorldUtils;
import arsenic.utils.render.RenderUtils;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Draws where the held bow or throwable will land: the flight path, and a marker on whatever it
 * hits first. The path turns red when it ends on an entity.
 */
@ModuleInfo(name = "Trajectories", category = ModuleCategory.RENDER, hidden = true)
public class Trajectories extends Module {

    private static final int MAX_STEPS = 200;
    private static final double DRAG = 0.99;

    @EventLink
    public final Listener<EventRenderWorldLast> renderWorldLast = event -> {
        ItemStack held = mc.player.getMainHandItem();
        boolean bow = held.getItem() instanceof BowItem;
        boolean throwable = held.getItem() instanceof SnowballItem || held.getItem() instanceof EggItem
                || held.getItem() instanceof EnderpearlItem;
        if (!bow && !throwable)
            return;

        float speed;
        double gravity;
        if (bow) {
            if (!mc.player.isUsingItem())
                return;
            float power = BowItem.getPowerForTime(mc.player.getTicksUsingItem());
            if (power < 0.1f)
                return;
            speed = power * 3.0f;
            gravity = 0.05;
        } else {
            speed = 1.5f;
            gravity = 0.03;
        }

        float yaw = mc.player.getYRot(event.partialTicks);
        float pitch = mc.player.getXRot(event.partialTicks);
        Vec3 pos = mc.player.getEyePosition(event.partialTicks)
                .add(-Mth.cos(yaw * Mth.DEG_TO_RAD) * 0.16, -0.1, -Mth.sin(yaw * Mth.DEG_TO_RAD) * 0.16);
        Vec3 motion = Vec3.directionFromRotation(pitch, yaw).scale(speed);

        int color = 0xFF000000 | ThemeManager.getMainColor();
        HitResult hit = null;
        Vec3 previous = pos;
        for (int step = 0; step < MAX_STEPS && hit == null; step++) {
            Vec3 next = pos.add(motion);

            HitResult entityHit = getEntityHit(pos, next);
            BlockHitResult blockHit = PlayerUtils.rayTraceBlocks(pos, next);
            if (entityHit != null) {
                hit = entityHit;
                next = entityHit.getLocation();
                color = 0xFF000000 | ThemeManager.getError();
            } else if (blockHit.getType() == HitResult.Type.BLOCK) {
                hit = blockHit;
                next = blockHit.getLocation();
            }

            RenderUtils.drawLine(previous, next, color, 2f);
            previous = next;
            pos = next;
            motion = motion.scale(DRAG).subtract(0, gravity, 0);
            if (pos.y < mc.level.getMinY())
                break;
        }

        if (hit != null)
            drawImpactMarker(hit, color);
    };

    /** A flat square on the face that was hit, or a box around the entity. */
    private void drawImpactMarker(HitResult hit, int color) {
        if (hit instanceof BlockHitResult blockHit) {
            Direction face = blockHit.getDirection();
            Vec3 at = blockHit.getLocation();
            double s = 0.25;
            Vec3 a = at.add(face.getAxis() == Direction.Axis.X ? 0 : -s, face.getAxis() == Direction.Axis.Y ? 0 : -s, face.getAxis() == Direction.Axis.Z ? 0 : -s);
            Vec3 b = at.add(face.getAxis() == Direction.Axis.X ? 0 : s, face.getAxis() == Direction.Axis.Y ? 0 : s, face.getAxis() == Direction.Axis.Z ? 0 : s);
            Gizmos.rect(a, b, face, GizmoStyle.strokeAndFill(color, 2f, RenderUtils.withAlpha(color, 70))).setAlwaysOnTop();
        } else {
            Vec3 at = hit.getLocation();
            RenderUtils.drawBoundingBox(new AABB(at, at).inflate(0.15), color);
        }
    }

    public HitResult getEntityHit(Vec3 origin, Vec3 destination) {
        HitResult closest = null;
        double closestDist = Double.MAX_VALUE;
        for (Entity e : WorldUtils.entities()) {
            if (!(e instanceof LivingEntity) || e == mc.player)
                continue;
            if (e instanceof Player && AntiBot.isBot(e))
                continue;
            AABB boundingBox = e.getBoundingBox().inflate(0.3);
            Optional<Vec3> intercept = boundingBox.clip(origin, destination);
            if (intercept.isPresent()) {
                double dist = origin.distanceToSqr(intercept.get());
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = new net.minecraft.world.phys.EntityHitResult(e, intercept.get());
                }
            }
        }
        return closest;
    }
}
