package arsenic.module.impl.ghost;

import arsenic.utils.keystrokes.SyntheticKeys;
import arsenic.utils.keystrokes.SyntheticKey;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventSilentRotation;
import arsenic.main.Arsenic;
import arsenic.injection.accessor.IMixinEntity;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.PropertyInfo;
import arsenic.module.property.impl.EnumProperty;
import arsenic.utils.lag.LagManager;
import arsenic.utils.rotations.SilentRotationManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;
import arsenic.module.property.impl.BooleanProperty;

import java.util.ArrayList;
import java.util.List;

@ModuleInfo(name = "Hitflick", category = ModuleCategory.COMBAT, tier = arsenic.module.ModuleTier.BLATANT)
public class Hitflick extends Module {

    public final EnumProperty<FlickDirection> direction = new EnumProperty<>("Direction", FlickDirection.Right);

    public final BooleanProperty blinkDuringFlick = new BooleanProperty("Blink", false);

    public final BooleanProperty stayOnHitbox = new BooleanProperty("Stay On Hitbox", true);

    public final DoubleProperty cooldown = new DoubleProperty("Cooldown", new DoubleValue(0, 1000, 250, 50));

    @PropertyInfo(reliesOn = "Direction", value = "Custom")
    public final DoubleProperty customAngle = new DoubleProperty("Angle", new DoubleValue(1, 180, 90, 1));

    @PropertyInfo(reliesOn = "Direction", value = "Void")
    public final DoubleProperty maxVoidAngle = new DoubleProperty("Max angle", new DoubleValue(15, 180, 180, 15));

    private long sinceLastFlick = 0;
    private long lastFlickTime = 0;

    private static final double BASE_KNOCKBACK = 0.4;
    private static final double EXTRA_KNOCKBACK = 0.5;
    private static final double EXTRA_KNOCKBACK_Y = 0.1;
    private static final double AIR_STRAFE = 0.026;
    private static final int MAX_SIM_TICKS = 40;
    // A fall this long with nothing below counts as void, even when the map has no void floor
    private static final double VOID_DROP = 10.0;
    private static final int STRAFE_NONE = 0;
    private static final int STRAFE_BACK = 1;
    private static final int STRAFE_LEFT = 2;
    private static final int STRAFE_RIGHT = 3;
    private static final float ROBUST_YAW_SPREAD = 6f;
    private static final double ROBUST_WIDEN = 0.15;
    private static final int ANGLE_STEP = 3;
    private static final float MIN_USEFUL_ANGLE = 3f;
    private static final float SAFE_ANGLE_MARGIN = 1f;
    private static final double HITBOX_SHRINK = 0.1;
    private static final double SAFE_REACH = 2.95;

    private static final double ARROW_LENGTH = 4.0;
    private static final double ARROW_HEAD_SIZE = 1.0;
    private static final double SHAFT_HALF_WIDTH = 0.18;
    private static final double HEAD_HALF_WIDTH = 0.55;
    private static final double OUTLINE_THICKNESS = 0.07;
    private static final long ARROW_LIFETIME_MS = 4000;

    private final List<VoidArrow> voidArrows = new ArrayList<>();

    public enum FlickState {
        IDLE,
        FLICKING_AWAY,
        RESTORING
    }

    private FlickState state = FlickState.IDLE;
    private float flickYaw;
    private float originalYaw;
    private Entity pendingTarget;
    private boolean pendingVoidHit;
    private int flickAppliedTick = -1;
    private int attackTick = -1;

    public boolean attackedThisTick() {
        return mc.thePlayer != null && attackTick == mc.thePlayer.ticksExisted;
    }

    public boolean ownsRotation() {
        return isEnabled() && (state == FlickState.FLICKING_AWAY
                || (mc.thePlayer != null && flickAppliedTick == mc.thePlayer.ticksExisted));
    }

    public boolean armFlick(Entity target) {
        return armFlick(target, mc.thePlayer.rotationYaw);
    }

    public boolean armFlick(Entity target, float baseYaw) {
        originalYaw = baseYaw;
        boolean clamp = stayOnHitbox.getValue();

        if (direction.getValue() == FlickDirection.Void) {
            if (!(target instanceof EntityLivingBase))
                return false;
            EntityLivingBase living = (EntityLivingBase) target;
            if (living.hurtTime > 0)
                return false;
            float safeRight = clamp ? hitboxHalfAngle(target, originalYaw, 1, 180) : 180f;
            float safeLeft = clamp ? hitboxHalfAngle(target, originalYaw, -1, 180) : 180f;
            Float voidYaw = findVoidYaw(living, safeRight, safeLeft);
            if (voidYaw == null)
                return false;
            flickYaw = voidYaw;
            pendingVoidHit = true;
        } else {
            float angle = getFlickAngle();
            if (clamp) {
                int sign = angle < 0 ? -1 : 1;
                float safe = hitboxHalfAngle(target, originalYaw, sign, Math.abs(angle)) - SAFE_ANGLE_MARGIN;
                if (safe < MIN_USEFUL_ANGLE)
                    return false;
                angle = sign * Math.min(Math.abs(angle), safe);
            }
            flickYaw = originalYaw + angle;
            pendingVoidHit = false;
        }

        this.pendingTarget = target;
        state = FlickState.FLICKING_AWAY;
        lastFlickTime = System.currentTimeMillis();
        if (blinkDuringFlick.getValue())
            LagManager.acquire(getClass());
        return true;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onSilentRotation = event -> {
        switch (state) {
            case FLICKING_AWAY:
                flickAppliedTick = mc.thePlayer.ticksExisted;
                event.setYaw(flickYaw);
                event.setSpeed(360f);
                event.setMovementFix(SilentRotationManager.MovementFix.STRICT);
                state = FlickState.RESTORING;
                break;

            case RESTORING:
                if (pendingTarget != null) {
                    attackTick = mc.thePlayer.ticksExisted;
                    mc.thePlayer.swingItem();
                    attack(pendingTarget);
                    if (pendingVoidHit)
                        spawnVoidArrow(pendingTarget, flickYaw);
                    pendingTarget = null;
                    pendingVoidHit = false;
                }
                state = FlickState.IDLE;
                sinceLastFlick = 0;
                if (blinkDuringFlick.getValue())
                    LagManager.release(getClass());
                break;

            case IDLE:
                sinceLastFlick++;
                break;
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRenderVoidArrows = event -> {
        if (voidArrows.isEmpty())
            return;

        long now = System.currentTimeMillis();
        voidArrows.removeIf(arrow -> now - arrow.spawnTime > ARROW_LIFETIME_MS);
        if (voidArrows.isEmpty())
            return;

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDepthMask(false);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        double vx = mc.getRenderManager().viewerPosX;
        double vy = mc.getRenderManager().viewerPosY;
        double vz = mc.getRenderManager().viewerPosZ;

        for (VoidArrow arrow : voidArrows) {
            float alpha = 1f - (now - arrow.spawnTime) / (float) ARROW_LIFETIME_MS;
            double baseX = arrow.x, baseY = arrow.y + 1.2, baseZ = arrow.z;

            wr.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
            addArrowTriangles(wr, baseX, baseY, baseZ, arrow.dirX, arrow.dirZ, OUTLINE_THICKNESS,
                    0f, 0f, 0f, alpha, vx, vy, vz);
            tessellator.draw();

            wr.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
            addArrowTriangles(wr, baseX, baseY, baseZ, arrow.dirX, arrow.dirZ, 0,
                    1f, 1f, 1f, alpha, vx, vy, vz);
            tessellator.draw();
        }

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_BLEND);
    };

    private static void addArrowTriangles(WorldRenderer wr, double baseX, double baseY, double baseZ,
                                           double dirX, double dirZ, double expand,
                                           float r, float g, float b, float a,
                                           double vx, double vy, double vz) {
        double perpX = -dirZ, perpZ = dirX;

        double tailX = baseX - dirX * expand;
        double tailZ = baseZ - dirZ * expand;
        double tipX = baseX + dirX * (ARROW_LENGTH + expand);
        double tipZ = baseZ + dirZ * (ARROW_LENGTH + expand);
        double shaftEndX = baseX + dirX * (ARROW_LENGTH - ARROW_HEAD_SIZE);
        double shaftEndZ = baseZ + dirZ * (ARROW_LENGTH - ARROW_HEAD_SIZE);

        double shaftHalf = SHAFT_HALF_WIDTH + expand;
        double headHalf = HEAD_HALF_WIDTH + expand;

        double tailLeftX = tailX + perpX * shaftHalf, tailLeftZ = tailZ + perpZ * shaftHalf;
        double tailRightX = tailX - perpX * shaftHalf, tailRightZ = tailZ - perpZ * shaftHalf;
        double shaftLeftX = shaftEndX + perpX * shaftHalf, shaftLeftZ = shaftEndZ + perpZ * shaftHalf;
        double shaftRightX = shaftEndX - perpX * shaftHalf, shaftRightZ = shaftEndZ - perpZ * shaftHalf;
        double headLeftX = shaftEndX + perpX * headHalf, headLeftZ = shaftEndZ + perpZ * headHalf;
        double headRightX = shaftEndX - perpX * headHalf, headRightZ = shaftEndZ - perpZ * headHalf;

        vertex(wr, tailLeftX, baseY, tailLeftZ, vx, vy, vz, r, g, b, a);
        vertex(wr, shaftLeftX, baseY, shaftLeftZ, vx, vy, vz, r, g, b, a);
        vertex(wr, shaftRightX, baseY, shaftRightZ, vx, vy, vz, r, g, b, a);

        vertex(wr, tailLeftX, baseY, tailLeftZ, vx, vy, vz, r, g, b, a);
        vertex(wr, shaftRightX, baseY, shaftRightZ, vx, vy, vz, r, g, b, a);
        vertex(wr, tailRightX, baseY, tailRightZ, vx, vy, vz, r, g, b, a);

        vertex(wr, headLeftX, baseY, headLeftZ, vx, vy, vz, r, g, b, a);
        vertex(wr, tipX, baseY, tipZ, vx, vy, vz, r, g, b, a);
        vertex(wr, headRightX, baseY, headRightZ, vx, vy, vz, r, g, b, a);
    }

    private static void vertex(WorldRenderer wr, double x, double y, double z,
                                double vx, double vy, double vz, float r, float g, float b, float a) {
        wr.pos(x - vx, y - vy, z - vz).color(r, g, b, a).endVertex();
    }

    private void spawnVoidArrow(Entity target, float yaw) {
        double[] push = knockbackVelocity(target, yaw, knockbackLevel());
        double len = Math.sqrt(push[0] * push[0] + push[2] * push[2]);
        if (len < 1.0E-4)
            return;
        voidArrows.add(new VoidArrow(target.posX, target.posY, target.posZ,
                push[0] / len, push[2] / len, System.currentTimeMillis()));
    }

    private int knockbackLevel() {
        int level = EnchantmentHelper.getKnockbackModifier(mc.thePlayer);
        if (mc.thePlayer.isSprinting())
            level++;
        return level;
    }

    private Float findVoidYaw(EntityLivingBase target, float safeRight, float safeLeft) {
        int level = knockbackLevel();
        if (level <= 0)
            return null;
        int maxDelta = (int) maxVoidAngle.getValue().getInput();
        for (int delta = 0; delta <= maxDelta; delta += ANGLE_STEP) {
            if (delta <= safeRight - SAFE_ANGLE_MARGIN && isRobustVoid(target, originalYaw + delta, level))
                return originalYaw + delta;
            if (delta != 0 && delta != 180 && delta <= safeLeft - SAFE_ANGLE_MARGIN
                    && isRobustVoid(target, originalYaw - delta, level))
                return originalYaw - delta;
        }
        return null;
    }

    private boolean isRobustVoid(EntityLivingBase target, float yaw, int level) {
        if (!simulateVoid(target, yaw, level, STRAFE_BACK, 0))
            return false;
        for (float off : new float[]{-ROBUST_YAW_SPREAD, ROBUST_YAW_SPREAD}) {
            if (!simulateVoid(target, yaw + off, level, STRAFE_BACK, 0))
                return false;
        }
        for (int mode = STRAFE_NONE; mode <= STRAFE_RIGHT; mode++) {
            if (!simulateVoid(target, yaw, level, mode, ROBUST_WIDEN))
                return false;
        }
        return simulateVoid(target, yaw, level, STRAFE_BACK, ROBUST_WIDEN);
    }

    private float hitboxHalfAngle(Entity target, float baseYaw, int sign, float limit) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1f);
        float pitch = Arsenic.getArsenic().getSilentRotationManager().pitch;
        AxisAlignedBB box = target.getEntityBoundingBox().contract(HITBOX_SHRINK, HITBOX_SHRINK, HITBOX_SHRINK);
        float best = 0f;
        for (int d = 0; d <= (int) limit; d++) {
            Vec3 look = ((IMixinEntity) mc.thePlayer).invokeGetVectorForRotation(pitch, baseYaw + sign * d);
            Vec3 end = eyes.addVector(look.xCoord * SAFE_REACH, look.yCoord * SAFE_REACH, look.zCoord * SAFE_REACH);
            if (box.calculateIntercept(eyes, end) == null)
                break;
            best = d;
        }
        return best;
    }

    private double[] knockbackVelocity(Entity target, float yaw, int level) {
        double motionX = 0, motionZ = 0;
        double offX = mc.thePlayer.posX - target.posX;
        double offZ = mc.thePlayer.posZ - target.posZ;
        double offLen = Math.sqrt(offX * offX + offZ * offZ);
        if (offLen > 1.0E-4) {
            motionX -= offX / offLen * BASE_KNOCKBACK;
            motionZ -= offZ / offLen * BASE_KNOCKBACK;
        }
        double motionY = BASE_KNOCKBACK;

        if (level > 0) {
            float rad = yaw * (float) Math.PI / 180f;
            motionX += -MathHelper.sin(rad) * level * EXTRA_KNOCKBACK;
            motionZ += MathHelper.cos(rad) * level * EXTRA_KNOCKBACK;
            motionY += EXTRA_KNOCKBACK_Y;
        }
        return new double[]{motionX, motionY, motionZ};
    }

    private boolean simulateVoid(EntityLivingBase target, float yaw, int level, int strafeMode, double widen) {
        double[] push = knockbackVelocity(target, yaw, level);
        double motionX = push[0], motionY = push[1], motionZ = push[2];

        double[] input = {0, 0};
        double pushLen = Math.sqrt(motionX * motionX + motionZ * motionZ);
        if (pushLen > 1.0E-4 && strafeMode != STRAFE_NONE) {
            double dx = motionX / pushLen, dz = motionZ / pushLen;
            switch (strafeMode) {
                case STRAFE_BACK:
                    input[0] = -dx * AIR_STRAFE;
                    input[1] = -dz * AIR_STRAFE;
                    break;
                case STRAFE_LEFT:
                    input[0] = -dz * AIR_STRAFE;
                    input[1] = dx * AIR_STRAFE;
                    break;
                case STRAFE_RIGHT:
                    input[0] = dz * AIR_STRAFE;
                    input[1] = -dx * AIR_STRAFE;
                    break;
            }
        }

        double posX = target.posX, posY = target.posY, posZ = target.posZ;
        double startY = posY;

        for (int tick = 0; tick < MAX_SIM_TICKS; tick++) {
            motionX += input[0];
            motionZ += input[1];

            double nextX = posX + motionX;
            double nextY = posY + motionY;
            double nextZ = posZ + motionZ;

            if (collides(target, nextX, posY, nextY, nextZ, target.height, widen))
                return false;

            posX = nextX;
            posY = nextY;
            posZ = nextZ;

            motionY -= 0.08;
            motionY *= 0.98;
            motionX *= 0.91;
            motionZ *= 0.91;

            if (posY < 0)
                return true;
            if (startY - posY > VOID_DROP)
                return !collides(target, posX, 0, posY, posZ, 0, widen);
        }
        return false;
    }

    private boolean collides(EntityLivingBase target, double x, double fromY, double toY, double z, double height,
                             double widen) {
        double minY = Math.min(fromY, toY) - 0.1;
        double maxY = Math.max(fromY, toY) + height + 0.1;
        double half = 0.3 + widen;
        AxisAlignedBB probe = new AxisAlignedBB(x - half, minY, z - half, x + half, maxY, z + half);
        return !mc.theWorld.getCollidingBoundingBoxes(target, probe).isEmpty();
    }

    private float getFlickAngle() {
        switch (direction.getValue()) {
            case Left:   return -90f;
            case Right:  return  90f;
            case Back:   return 180f;
            case Custom: return (float) customAngle.getValue().getInput();
            default:     return  90f;
        }
    }

    public boolean shouldFlick() {
        return state == FlickState.IDLE && sinceLastFlick >= 1
                && System.currentTimeMillis() - lastFlickTime >= cooldown.getValue().getInput();
    }

    @Override
    protected void onDisable() {
        LagManager.release(getClass());
        state = FlickState.IDLE;
        flickYaw = 0;
        originalYaw = 0;
        pendingTarget = null;
        pendingVoidHit = false;
        flickAppliedTick = -1;
        attackTick = -1;
        voidArrows.clear();
    }

    public enum FlickDirection {
        Left, Right, Back, Custom, Void;
    }
    private static final class VoidArrow {
        final double x, y, z;
        final double dirX, dirZ;
        final long spawnTime;

        VoidArrow(double x, double y, double z, double dirX, double dirZ, long spawnTime) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dirX = dirX;
            this.dirZ = dirZ;
            this.spawnTime = spawnTime;
        }
    }

    @SyntheticKey(SyntheticKeys.Key.LMB)
    private void attack(Entity target) {
        mc.playerController.attackEntity(mc.thePlayer, target);
    }
}
