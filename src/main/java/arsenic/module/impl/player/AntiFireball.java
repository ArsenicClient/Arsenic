package arsenic.module.impl.player;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.*;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.timer.MSTimer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.world.entity.Entity;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

@ModuleInfo(name = "AntiFireball", category = ModuleCategory.PLAYER)
public class AntiFireball extends Module {

    /** Reach used both to pick a fireball and to decide it is close enough to hit. */
    private static final double RANGE = 4.5;

    /**
     * Fireballs younger than this are ignored. A fireball is at its spawn point for the first few
     * ticks, so swinging immediately hits nothing and just looks like a random swing at the air.
     */
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

        Vec3 targetVec = new Vec3(target.getX(), target.getY() + target.height / 2, target.getZ());
        Vec3 eyePos = mc.player.getEyePosition(1f);
        double dx = targetVec.x - eyePos.x;
        double dy = targetVec.y - eyePos.y;
        double dz = targetVec.z - eyePos.z;
        double dist = (float) Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, dist)));

        event.setYaw(yaw);
        event.setPitch(pitch);
        event.setSpeed(180);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> eventTick = event -> {
        if (target == null || !target.isEntityAlive())
            return;

        if (RotationUtils.getDistanceToEntityBox(target) > RANGE)
            return;

        if (!attackTimer.hasTimeElapsed(150))
            return;

        // Always a real swing: a bare animation packet without the client-side arm movement is
        // both easier to spot and pointless here, since the swing is visible anyway.
        mc.player.swingItem();
        mc.gameMode.attackEntity(mc.player, target);
        attackTimer.reset();
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRender2D> eventRender2D = event -> {
        for (Entity e : mc.level.loadedEntityList) {
            if (!(e instanceof EntityFireball))
                continue;
            if (mc.player.distanceTo(e) > 64)
                continue;

            drawFireballIndicator(e, event.getSr());
        }
    };

    private void drawFireballIndicator(Entity fireball, ScaledResolution sr) {
        double dx = fireball.getX() - mc.player.getX();
        double dz = fireball.getZ() - mc.player.getZ();
        float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        float angle = Mth.wrapDegrees(yaw - mc.player.getYRot());

        double radians = Math.toRadians(angle + 90);
        int radius = 50;
        int centerX = sr.getScaledWidth() / 2;
        int centerY = sr.getScaledHeight() / 2;

        int indicatorX = centerX + (int) (radius * Math.cos(radians));
        int indicatorY = centerY - (int) (radius * Math.sin(radians));

        int dist = (int) mc.player.distanceTo(fireball);


        float size = 6.0f;
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex2d(indicatorX, indicatorY - size);
        GL11.glVertex2d(indicatorX - size, indicatorY + size);
        GL11.glVertex2d(indicatorX + size, indicatorY + size);
        GL11.glEnd();

        mc.font.drawStringWithShadow(dist + "m", indicatorX - 8, indicatorY + 8, new Color(255, 80, 80).getRGB());
    }

    private Entity findBestFireball() {
        List<Entity> fireballs = mc.level.loadedEntityList.stream()
                .filter(e -> e instanceof EntityFireball)
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
            double horizontalDist = Math.sqrt(dx * dx + dz * dz);
            if (horizontalDist < 0.1)
                return fb;

            double dot = dx * fb.motionX + dz * fb.motionZ;
            if (dot < 0)
                continue;

            double cross = Math.abs(dx * fb.motionZ - dz * fb.motionX);
            if (cross / horizontalDist < 2.0)
                return fb;
        }

        return fireballs.get(0);
    }

}
