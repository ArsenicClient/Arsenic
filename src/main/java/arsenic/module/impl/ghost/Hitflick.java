package arsenic.module.impl.ghost;

import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventSilentRotation;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.PropertyInfo;
import arsenic.module.property.impl.EnumProperty;
import arsenic.utils.lag.LagManager;
import arsenic.utils.rotations.SilentRotationManager;
import arsenic.utils.minecraft.ItemUtils;
import arsenic.utils.minecraft.PlayerUtils;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import arsenic.module.property.impl.BooleanProperty;

import java.util.ArrayList;
import java.util.List;

@ModuleInfo(name = "Hitflick", category = ModuleCategory.COMBAT)
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
    private static final double VOID_DROP = 14.0;
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
        return mc.player != null && attackTick == mc.player.tickCount;
    }

    public boolean ownsRotation() {
        return isEnabled() && (state == FlickState.FLICKING_AWAY
                || (mc.player != null && flickAppliedTick == mc.player.tickCount));
    }

    public boolean armFlick(Entity target) {
        return armFlick(target, mc.player.getYRot());
    }

    public boolean armFlick(Entity target, float baseYaw) {
        originalYaw = baseYaw;
        boolean clamp = stayOnHitbox.getValue();

        if (direction.getValue() == FlickDirection.Void) {
            if (!(target instanceof LivingEntity))
                return false;
            LivingEntity living = (LivingEntity) target;
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
                flickAppliedTick = mc.player.tickCount;
                event.setYaw(flickYaw);
                event.setSpeed(360f);
                event.setMovementFix(SilentRotationManager.MovementFix.STRICT);
                state = FlickState.RESTORING;
                break;

            case RESTORING:
                if (pendingTarget != null) {
                    attackTick = mc.player.tickCount;
                    PlayerUtils.swingItem();
                    mc.gameMode.attack(mc.player, pendingTarget);
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

        for (VoidArrow arrow : voidArrows) {
            float alpha = 1f - (now - arrow.spawnTime) / (float) ARROW_LIFETIME_MS;
            double baseX = arrow.x, baseY = arrow.y + 1.2, baseZ = arrow.z;
            int a = (int) (alpha * 255) << 24;
            drawArrow(baseX, baseY - 0.01, baseZ, arrow.dirX, arrow.dirZ, OUTLINE_THICKNESS, a);
            drawArrow(baseX, baseY, baseZ, arrow.dirX, arrow.dirZ, 0, a | 0xFFFFFF);
        }
    };

    private static void drawArrow(double baseX, double baseY, double baseZ, double dirX, double dirZ,
                                  double expand, int color) {
        double perpX = -dirZ, perpZ = dirX;

        double tailX = baseX - dirX * expand;
        double tailZ = baseZ - dirZ * expand;
        double tipX = baseX + dirX * (ARROW_LENGTH + expand);
        double tipZ = baseZ + dirZ * (ARROW_LENGTH + expand);
        double shaftEndX = baseX + dirX * (ARROW_LENGTH - ARROW_HEAD_SIZE);
        double shaftEndZ = baseZ + dirZ * (ARROW_LENGTH - ARROW_HEAD_SIZE);

        double shaftHalf = SHAFT_HALF_WIDTH + expand;
        double headHalf = HEAD_HALF_WIDTH + expand;

        Vec3 tailLeft = new Vec3(tailX + perpX * shaftHalf, baseY, tailZ + perpZ * shaftHalf);
        Vec3 tailRight = new Vec3(tailX - perpX * shaftHalf, baseY, tailZ - perpZ * shaftHalf);
        Vec3 shaftLeft = new Vec3(shaftEndX + perpX * shaftHalf, baseY, shaftEndZ + perpZ * shaftHalf);
        Vec3 shaftRight = new Vec3(shaftEndX - perpX * shaftHalf, baseY, shaftEndZ - perpZ * shaftHalf);
        Vec3 headLeft = new Vec3(shaftEndX + perpX * headHalf, baseY, shaftEndZ + perpZ * headHalf);
        Vec3 headRight = new Vec3(shaftEndX - perpX * headHalf, baseY, shaftEndZ - perpZ * headHalf);
        Vec3 tip = new Vec3(tipX, baseY, tipZ);

        GizmoStyle style = GizmoStyle.fill(color);
        Gizmos.rect(tailLeft, shaftLeft, shaftRight, tailRight, style).setAlwaysOnTop();
        // the head is a triangle: a quad with its last corner folded onto the tip
        Gizmos.rect(headLeft, tip, headRight, headRight, style).setAlwaysOnTop();
    }

    private void spawnVoidArrow(Entity target, float yaw) {
        double[] push = knockbackVelocity(target, yaw, knockbackLevel());
        double len = Math.sqrt(push[0] * push[0] + push[2] * push[2]);
        if (len < 1.0E-4)
            return;
        voidArrows.add(new VoidArrow(target.getX(), target.getY(), target.getZ(),
                push[0] / len, push[2] / len, System.currentTimeMillis()));
    }

    private int knockbackLevel() {
        int level = ItemUtils.enchantLevel(Enchantments.KNOCKBACK, mc.player.getMainHandItem());
        if (mc.player.isSprinting())
            level++;
        return level;
    }

    private Float findVoidYaw(LivingEntity target, float safeRight, float safeLeft) {
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

    private boolean isRobustVoid(LivingEntity target, float yaw, int level) {
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
        Vec3 eyes = mc.player.getEyePosition(1f);
        float pitch = Arsenic.getArsenic().getSilentRotationManager().pitch;
        AABB box = target.getBoundingBox().deflate(HITBOX_SHRINK);
        float best = 0f;
        for (int d = 0; d <= (int) limit; d++) {
            Vec3 look = net.minecraft.world.entity.Entity.calculateViewVector(pitch, baseYaw + sign * d);
            Vec3 end = eyes.add(look.x * SAFE_REACH, look.y * SAFE_REACH, look.z * SAFE_REACH);
            if (box.clip(eyes, end).isEmpty())
                break;
            best = d;
        }
        return best;
    }

    private double[] knockbackVelocity(Entity target, float yaw, int level) {
        double motionX = 0, motionZ = 0;
        double offX = mc.player.getX() - target.getX();
        double offZ = mc.player.getZ() - target.getZ();
        double offLen = Math.sqrt(offX * offX + offZ * offZ);
        if (offLen > 1.0E-4) {
            motionX -= offX / offLen * BASE_KNOCKBACK;
            motionZ -= offZ / offLen * BASE_KNOCKBACK;
        }
        double motionY = BASE_KNOCKBACK;

        if (level > 0) {
            float rad = yaw * (float) Math.PI / 180f;
            motionX += -Mth.sin(rad) * level * EXTRA_KNOCKBACK;
            motionZ += Mth.cos(rad) * level * EXTRA_KNOCKBACK;
            motionY += EXTRA_KNOCKBACK_Y;
        }
        return new double[]{motionX, motionY, motionZ};
    }

    private boolean simulateVoid(LivingEntity target, float yaw, int level, int strafeMode, double widen) {
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

        double posX = target.getX(), posY = target.getY(), posZ = target.getZ();
        double startY = posY;

        for (int tick = 0; tick < MAX_SIM_TICKS; tick++) {
            motionX += input[0];
            motionZ += input[1];

            double nextX = posX + motionX;
            double nextY = posY + motionY;
            double nextZ = posZ + motionZ;

            if (collides(target, nextX, posY, nextY, nextZ, target.getBbHeight(), widen))
                return false;

            posX = nextX;
            posY = nextY;
            posZ = nextZ;

            motionY -= 0.08;
            motionY *= 0.98;
            motionX *= 0.91;
            motionZ *= 0.91;

            if (posY < mc.level.getMinY()) // the world floor is no longer y=0
                return true;
            if (startY - posY > VOID_DROP)
                return !collides(target, posX, mc.level.getMinY(), posY, posZ, 0, widen);
        }
        return false;
    }

    private boolean collides(LivingEntity target, double x, double fromY, double toY, double z, double height,
                             double widen) {
        double minY = Math.min(fromY, toY) - 0.1;
        double maxY = Math.max(fromY, toY) + height + 0.1;
        double half = 0.3 + widen;
        AABB probe = new AABB(x - half, minY, z - half, x + half, maxY, z + half);
        return !mc.level.noCollision(target, probe);
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
}
