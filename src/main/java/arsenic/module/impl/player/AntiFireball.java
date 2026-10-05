package arsenic.module.impl.player;

import arsenic.utils.java.MathUtils;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.*;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.timer.MSTimer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

@ModuleInfo(name = "AntiFireball", category = ModuleCategory.PLAYER, tier = ModuleTier.EXTRA)
public class AntiFireball extends Module {

    private static final double RANGE = 4.5;

    private static final int MIN_FIREBALL_AGE_TICKS = 10;

    private Entity target;
    private final MSTimer attackTimer = new MSTimer();

    @Override
    protected void onEnable() {
        target = null;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> eventSilentRotation = event -> {
        target = findBestFireball();
        if (target == null)
            return;

        Vec3 targetVec = new Vec3(target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ());
        Vec3 eyePos = mc.player.getEyePosition(1f);
        float[] rots = RotationUtils.rotationsTo(eyePos, targetVec);
        float yaw = rots[0];
        float pitch = rots[1];

        event.setYaw(yaw);
        event.setPitch(pitch);
        event.setSpeed(180);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> eventTick = event -> {
        if (target == null || !target.isAlive())
            return;

        if (RotationUtils.getDistanceToEntityBox(target) > RANGE)
            return;

        if (!attackTimer.hasTimeElapsed(150))
            return;

        PlayerUtils.swingItem();
        mc.gameMode.attack(mc.player, target);
        attackTimer.reset();
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRender2D> eventRender2D = event -> {
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof Fireball))
                continue;
            if (mc.player.distanceTo(e) > 64)
                continue;

            drawFireballIndicator(e, event.getWidth(), event.getHeight());
        }
    };

    private void drawFireballIndicator(Entity fireball, int screenWidth, int screenHeight) {
        double dx = fireball.getX() - mc.player.getX();
        double dz = fireball.getZ() - mc.player.getZ();
        float yaw = RotationUtils.yawTo(dx, dz);
        float angle = Mth.wrapDegrees(yaw - mc.player.getYRot());

        double radians = Math.toRadians(angle + 90);
        int radius = 50;
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;

        int indicatorX = centerX + (int) (radius * Math.cos(radians));
        int indicatorY = centerY - (int) (radius * Math.sin(radians));

        int dist = (int) mc.player.distanceTo(fireball);


        float size = 6.0f;
        new arsenic.utils.render.QuadBatch().triangle(indicatorX, indicatorY - size, indicatorX - size, indicatorY + size,
                indicatorX + size, indicatorY + size, 0xFFFF5050).submit();

        arsenic.utils.render.RenderContext.graphics().text(mc.font, dist + "m", indicatorX - 8, indicatorY + 8, new Color(255, 80, 80).getRGB(), true);
    }

    private Entity findBestFireball() {
        List<Entity> fireballs = arsenic.utils.minecraft.WorldUtils.entities().stream()
                .filter(e -> e instanceof Fireball)
                .filter(e -> {
                    if (e.tickCount <= MIN_FIREBALL_AGE_TICKS)
                        return false;
                    if (mc.player.distanceTo(e) > RANGE)
                        return false;
                    return true;
                })
                .sorted((a, b) -> {
                    double distA = mc.player.distanceTo(a);
                    double distB = mc.player.distanceTo(b);
                    return Double.compare(distA, distB);
                })
                .collect(Collectors.toList());

        if (fireballs.isEmpty())
            return null;

        for (Entity fb : fireballs) {
            double dx = fb.getX() - mc.player.getX();
            double dz = fb.getZ() - mc.player.getZ();
            double horizontalDist = MathUtils.horizontalDistance(dx, dz);
            if (horizontalDist < 0.1)
                return fb;

            double dot = dx * fb.getDeltaMovement().x + dz * fb.getDeltaMovement().z;
            if (dot < 0)
                continue;

            double cross = Math.abs(dx * fb.getDeltaMovement().z - dz * fb.getDeltaMovement().x);
            if (cross / horizontalDist < 2.0)
                return fb;
        }

        return fireballs.get(0);
    }

}
