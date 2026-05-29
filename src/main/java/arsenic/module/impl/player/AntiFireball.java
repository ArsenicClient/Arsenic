package arsenic.module.impl.player;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.*;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.timer.MSTimer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

@ModuleInfo(name = "AntiFireball", category = ModuleCategory.PLAYER)
public class AntiFireball extends Module {

    public final DoubleProperty range = new DoubleProperty("Range", new DoubleValue(3.0, 8.0, 4.5, 0.1));
    public final EnumProperty<SwingMode> swing = new EnumProperty<>("Swing", SwingMode.NORMAL);
    public final BooleanProperty tickCheck = new BooleanProperty("TickCheck", true);
    public final DoubleProperty minFireballTick = new DoubleProperty("MinTick", new DoubleValue(1, 20, 10, 1));
    public final BooleanProperty indicators = new BooleanProperty("Indicators", true);

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

        Vec3 targetVec = new Vec3(target.posX, target.posY + target.height / 2, target.posZ);
        Vec3 eyePos = mc.thePlayer.getPositionEyes(1f);
        double dx = targetVec.xCoord - eyePos.xCoord;
        double dy = targetVec.yCoord - eyePos.yCoord;
        double dz = targetVec.zCoord - eyePos.zCoord;
        double dist = MathHelper.sqrt_double(dx * dx + dz * dz);
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

        if (RotationUtils.getDistanceToEntityBox(target) > range.getValue().getInput())
            return;

        if (!attackTimer.hasTimeElapsed(150))
            return;

        switch (swing.getValue()) {
            case NORMAL:
                mc.thePlayer.swingItem();
                break;
            case PACKET:
                mc.getNetHandler().addToSendQueue(new C0APacketAnimation());
                break;
        }
        mc.playerController.attackEntity(mc.thePlayer, target);
        attackTimer.reset();
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRender2D> eventRender2D = event -> {
        if (!indicators.getValue())
            return;

        for (Entity e : mc.theWorld.loadedEntityList) {
            if (!(e instanceof EntityFireball))
                continue;
            if (mc.thePlayer.getDistanceToEntity(e) > 64)
                continue;

            drawFireballIndicator(e, event.getSr());
        }
    };

    private void drawFireballIndicator(Entity fireball, ScaledResolution sr) {
        double dx = fireball.posX - mc.thePlayer.posX;
        double dz = fireball.posZ - mc.thePlayer.posZ;
        float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        float angle = MathHelper.wrapAngleTo180_float(yaw - mc.thePlayer.rotationYaw);

        double radians = Math.toRadians(angle + 90);
        int radius = 50;
        int centerX = sr.getScaledWidth() / 2;
        int centerY = sr.getScaledHeight() / 2;

        int indicatorX = centerX + (int) (radius * Math.cos(radians));
        int indicatorY = centerY - (int) (radius * Math.sin(radians));

        int dist = (int) mc.thePlayer.getDistanceToEntity(fireball);

        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glLineWidth(2.0f);

        GL11.glColor4f(1.0f, 0.3f, 0.3f, 0.9f);
        float size = 6.0f;
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex2d(indicatorX, indicatorY - size);
        GL11.glVertex2d(indicatorX - size, indicatorY + size);
        GL11.glVertex2d(indicatorX + size, indicatorY + size);
        GL11.glEnd();

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        mc.fontRendererObj.drawStringWithShadow(dist + "m", indicatorX - 8, indicatorY + 8, new Color(255, 80, 80).getRGB());
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }

    private Entity findBestFireball() {
        List<Entity> fireballs = mc.theWorld.loadedEntityList.stream()
                .filter(e -> e instanceof EntityFireball)
                .filter(e -> {
                    if (tickCheck.getValue() && e.ticksExisted <= minFireballTick.getValue().getInput())
                        return false;
                    if (mc.thePlayer.getDistanceToEntity(e) > range.getValue().getInput())
                        return false;
                    return true;
                })
                .sorted((a, b) -> {
                    double distA = mc.thePlayer.getDistanceToEntity(a);
                    double distB = mc.thePlayer.getDistanceToEntity(b);
                    return Double.compare(distA, distB);
                })
                .collect(Collectors.toList());

        if (fireballs.isEmpty())
            return null;

        for (Entity fb : fireballs) {
            double dx = fb.posX - mc.thePlayer.posX;
            double dz = fb.posZ - mc.thePlayer.posZ;
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

    public enum SwingMode {
        NORMAL, PACKET, NONE
    }
}
