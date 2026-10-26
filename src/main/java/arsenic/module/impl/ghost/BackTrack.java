package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventAttack;
import arsenic.event.impl.EventPlayerJoinWorld;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventTick;
import arsenic.injection.accessor.IMixinS14PacketEntity;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.Group;
import arsenic.module.property.PropertyGroup;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.ColourProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.SliderScale;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.lag.LagManager;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.render.RenderUtils;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S29PacketSoundEffect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

/**
 * While the target is closer on screen than the server has them, holds the target's movement (and its hurt, health
 * and sound feedback) for the delay, so hits land against the older, closer position. Stops as soon as holding
 * would put the target farther than where they really are, on a teleport from the server, or on knockback.
 */
@ModuleInfo(name = "Backtrack", category = ModuleCategory.COMBAT, tier = ModuleTier.LEGIT)
public class BackTrack extends Module {

    private static final int COMBAT_WINDOW_TICKS = 20;

    public final DoubleProperty delay = new DoubleProperty("Delay (ms)", new DoubleValue(10, 500, 100, 10), SliderScale.LOG);
    public final DoubleProperty cooldown = new DoubleProperty("Cooldown (ticks)", new DoubleValue(0, 40, 10, 1));
    public final RangeProperty targetDist = new RangeProperty("Target Dist", new RangeValue(0, 10, 2, 6, 0.1));
    public final DoubleProperty maxHurtTime = new DoubleProperty("Max Hurt Time", new DoubleValue(0, 10, 10, 1));
    public final BooleanProperty requireMousePressed = new BooleanProperty("Require Mouse Pressed", false);
    public final BooleanProperty requireWeapon = new BooleanProperty("Require Weapon", false);
    public final BooleanProperty disableOnHit = new BooleanProperty("Disable On Hit", true);

    @PropertyGroup(Group.VISUAL)
    public final EnumProperty<EspMode> espMode = new EnumProperty<>("ESP", EspMode.BOX);
    @PropertyGroup(Group.VISUAL)
    public final BooleanProperty rotate = new BooleanProperty("Rotate Box", true);
    @PropertyGroup(Group.VISUAL)
    public final DoubleProperty lineWidth = new DoubleProperty("Line Width", new DoubleValue(1, 5, 2, 0.5));
    @PropertyGroup(Group.VISUAL)
    public final ColourProperty color = new ColourProperty("Color", -1);

    private volatile boolean active;
    private volatile boolean abort;
    private volatile int targetId;
    private volatile long holdMs;
    private volatile Vec3 truePos = zero();
    private volatile float trueYaw;
    private volatile boolean hasYaw;

    private EntityPlayer lastTarget;
    private int lastAttackTick = -1000;
    private int cooldownTicks;
    private int interpolation;
    private Vec3 previous = zero();
    private Vec3 current = zero();
    private float previousYaw;
    private float currentYaw;

    public enum EspMode {
        NONE, BOX, FILLED, MODEL
    }

    @Override
    public String getHudInfo() {
        return String.valueOf((int) delay.getValue().getInput());
    }

    @Override
    public void onEnable() {
        reset();
        LagManager.delay(BackTrack.class, this::isHeld, this::onDelayPacket);
    }

    @Override
    public void onDisable() {
        LagManager.undelay(BackTrack.class);
        LagManager.releaseDelayedFor(BackTrack.class);
        reset();
    }

    @EventLink
    public final Listener<EventPlayerJoinWorld> onJoinWorld = event -> {
        if (event.getEntity() == mc.thePlayer)
            onEnable();
    };

    private void reset() {
        active = false;
        abort = false;
        lastTarget = null;
        cooldownTicks = 0;
        interpolation = 0;
        hasYaw = false;
    }

    private void stop(boolean withCooldown) {
        if (active && withCooldown)
            cooldownTicks = (int) cooldown.getValue().getInput();
        active = false;
        abort = false;
        hasYaw = false;
        LagManager.releaseDelayedFor(BackTrack.class);
    }

    // ---- packet side (netty thread) ----

    private boolean isHeld(Packet<?> p) {
        if (!active)
            return false;
        if (p instanceof S14PacketEntity)
            return ((IMixinS14PacketEntity) p).getEntityId() == targetId;
        if (p instanceof S18PacketEntityTeleport)
            return ((S18PacketEntityTeleport) p).getEntityId() == targetId;
        if (p instanceof S19PacketEntityStatus) {
            Entity e = mc.theWorld == null ? null : ((S19PacketEntityStatus) p).getEntity(mc.theWorld);
            return e != null && e.getEntityId() == targetId;
        }
        if (p instanceof S08PacketPlayerPosLook)
            return true;
        if (p instanceof S12PacketEntityVelocity)
            return disableOnHit.getValue() && mc.thePlayer != null
                    && ((S12PacketEntityVelocity) p).getEntityID() == mc.thePlayer.getEntityId();
        return p instanceof S06PacketUpdateHealth || p instanceof S29PacketSoundEffect;
    }

    private long onDelayPacket(Packet<?> p) {
        // a teleport of ours or knockback ends the hold; the packet itself is not delayed
        if (p instanceof S08PacketPlayerPosLook || p instanceof S12PacketEntityVelocity) {
            abort = true;
            return 0L;
        }

        if (p instanceof S14PacketEntity) {
            S14PacketEntity move = (S14PacketEntity) p;
            truePos = truePos.addVector(move.func_149062_c() / 32.0D, move.func_149061_d() / 32.0D, move.func_149064_e() / 32.0D);
            if (move.func_149060_h()) {
                trueYaw = move.func_149066_f() * 360.0F / 256.0F;
                hasYaw = true;
            }
        } else if (p instanceof S18PacketEntityTeleport) {
            S18PacketEntityTeleport tp = (S18PacketEntityTeleport) p;
            truePos = new Vec3(tp.getX() / 32.0D, tp.getY() / 32.0D, tp.getZ() / 32.0D);
            trueYaw = tp.getYaw() * 360.0F / 256.0F;
            hasYaw = true;
        }
        return holdMs;
    }

    // ---- client side ----

    @RequiresPlayer
    @EventLink
    public final Listener<EventAttack> eventAttack = event -> {
        if (event.getTarget() instanceof EntityPlayer) {
            lastTarget = (EntityPlayer) event.getTarget();
            lastAttackTick = mc.thePlayer.ticksExisted;
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> eventTick = event -> {
        if (abort) {
            stop(true);
            return;
        }
        if (cooldownTicks > 0 && --cooldownTicks > 0)
            return;

        EntityPlayer target = lastTarget;
        double low = targetDist.getValue().getMin();
        double high = targetDist.getValue().getMax();
        double distSq = target == null ? 0 : mc.thePlayer.getDistanceSqToEntity(target);

        boolean allowed = mc.currentScreen == null
                && (!requireMousePressed.getValue() || mc.gameSettings.keyBindAttack.isKeyDown())
                && (!requireWeapon.getValue() || PlayerUtils.isPlayerHoldingWeapon())
                && mc.thePlayer.ticksExisted - lastAttackTick <= COMBAT_WINDOW_TICKS
                && target != null && !target.isDead
                && target.hurtTime <= maxHurtTime.getValue().getInput()
                && distSq >= low * low && distSq <= high * high;

        if (!allowed) {
            stop(true);
            return;
        }

        Vec3 self = mc.thePlayer.getPositionVector();
        Vec3 server = serverPos(target);

        if (!active) {
            // only worth holding while the server has the target farther away than they are drawn
            if (distance(self, server) - distance(self, target.getPositionVector()) <= 0.01)
                return;
            targetId = target.getEntityId();
            holdMs = (long) delay.getValue().getInput();
            truePos = server;
            hasYaw = false;
            previous = current = target.getPositionVector();
            previousYaw = currentYaw = target.rotationYaw;
            interpolation = 0;
            active = true;
            return;
        }

        if (targetId != target.getEntityId()) {
            stop(true);
            return;
        }

        holdMs = (long) delay.getValue().getInput();
        Vec3 real = truePos;
        // holding made the target closer than the server has them: nothing to gain, so let go
        if (distance(self, real) - distance(self, server) < -0.05) {
            stop(true);
            return;
        }
        updateIndicator(real, hasYaw ? trueYaw : target.rotationYaw);
    };

    private void updateIndicator(Vec3 position, float yaw) {
        previous = current;
        previousYaw = currentYaw;
        if (current.squareDistanceTo(position) > 1.0E-6 || yaw != currentYaw)
            interpolation = 3;
        if (interpolation < 1) {
            current = position;
            currentYaw = yaw;
            return;
        }
        double fraction = 1.0 / interpolation;
        current = current.addVector((position.xCoord - current.xCoord) * fraction,
                (position.yCoord - current.yCoord) * fraction,
                (position.zCoord - current.zCoord) * fraction);
        currentYaw += MathHelper.wrapAngleTo180_float(yaw - currentYaw) * (float) fraction;
        interpolation--;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> eventRenderWorldLast = event -> {
        EntityPlayer target = lastTarget;
        EspMode mode = espMode.getValue();
        if (!active || target == null || mode == EspMode.NONE)
            return;

        double x = previous.xCoord + (current.xCoord - previous.xCoord) * event.partialTicks;
        double y = previous.yCoord + (current.yCoord - previous.yCoord) * event.partialTicks;
        double z = previous.zCoord + (current.zCoord - previous.zCoord) * event.partialTicks;
        float yaw = previousYaw + (currentYaw - previousYaw) * event.partialTicks;

        Color c = new Color(color.getValue(), true);

        GlStateManager.pushMatrix();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDepthMask(false);

        if (mode == EspMode.MODEL) {
            GlStateManager.translate(x - target.posX, y - target.posY, z - target.posZ);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            mc.getRenderManager().renderEntityStatic(target, event.partialTicks, false);
        } else {
            AxisAlignedBB box = target.getEntityBoundingBox();
            double w = box.maxX - box.minX;
            double h = box.maxY - box.minY;
            GlStateManager.translate(x - mc.getRenderManager().viewerPosX,
                    y - mc.getRenderManager().viewerPosY,
                    z - mc.getRenderManager().viewerPosZ);
            if (rotate.getValue())
                GlStateManager.rotate(-yaw, 0, 1, 0);
            AxisAlignedBB local = new AxisAlignedBB(-w / 2, 0, -w / 2, w / 2, h, w / 2);
            if (mode == EspMode.BOX) {
                GL11.glLineWidth((float) lineWidth.getValue().getInput());
                RenderGlobal.drawOutlinedBoundingBox(local, c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
            } else {
                RenderUtils.drawShadedBoundingBox(local, c.getRed(), c.getGreen(), c.getBlue(), 63);
            }
        }

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDepthMask(true);
        GL11.glLineWidth(1.0F);
        GlStateManager.popMatrix();
    };

    private static Vec3 serverPos(Entity e) {
        return new Vec3(e.serverPosX / 32.0D, e.serverPosY / 32.0D, e.serverPosZ / 32.0D);
    }

    private static double distance(Vec3 a, Vec3 b) {
        return a.distanceTo(b);
    }

    private static Vec3 zero() {
        return new Vec3(0.0, 0.0, 0.0);
    }
}
