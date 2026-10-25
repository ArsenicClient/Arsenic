package arsenic.module.impl.ghost;

import arsenic.module.ModuleTier;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventSilentRotation;
import arsenic.gui.design.M3;
import arsenic.main.Arsenic;
import arsenic.injection.accessor.IMixinEntity;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.ShowWhen;
import arsenic.module.property.impl.EnumProperty;
import arsenic.utils.lag.LagManager;
import arsenic.utils.minecraft.BadPacketsManager;
import arsenic.utils.render.RenderUtils;
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
import java.util.Random;

@ModuleInfo(name = "Hitflick", category = ModuleCategory.COMBAT, tier = ModuleTier.BLATANT)
public class Hitflick extends Module {

    public final EnumProperty<FlickDirection> direction = new EnumProperty<>("Direction", FlickDirection.Right);

    public final BooleanProperty blinkDuringFlick = new BooleanProperty("Blink", false);

    public final BooleanProperty stayOnHitbox = new BooleanProperty("Stay On Hitbox", true);

    public final DoubleProperty cooldown = new DoubleProperty("Cooldown", new DoubleValue(0, 1000, 250, 50));

    @ShowWhen(property = "Direction", is = "Custom")
    public final DoubleProperty customAngle = new DoubleProperty("Angle", new DoubleValue(1, 180, 90, 1));

    @ShowWhen(property = "Direction", is = "Void")
    public final DoubleProperty maxVoidAngle = new DoubleProperty("Max angle", new DoubleValue(15, 180, 180, 15));

    @ShowWhen(property = "Direction", is = "Void2")
    public final DoubleProperty void2Angle = new DoubleProperty("Void2 Angle", new DoubleValue(1, 180, 90, 1));

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
    /** Void2's last side, kept when both sides look the same. */
    private int void2Side = -1;
    private final Random random = new Random();

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
        } else if (direction.getValue() == FlickDirection.Void2) {
            int side = void2Side(target, void2Side);
            if (side == -1)
                return false;
            void2Side = side;
            // OpenB4's Displace: the side's angle, give or take 5 degrees
            float angle = ((float) void2Angle.getValue().getInput() + (random.nextFloat() * 10f - 5f)) * (side == 0 ? -1 : 1);
            if (clamp) {
                int sign = angle < 0 ? -1 : 1;
                float safe = hitboxHalfAngle(target, originalYaw, sign, Math.abs(angle)) - SAFE_ANGLE_MARGIN;
                if (safe < MIN_USEFUL_ANGLE)
                    return false;
                angle = sign * Math.min(Math.abs(angle), safe);
            }
            flickYaw = originalYaw + angle;
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
                // a block went up since the flick (the server sees an item in use): no swing or attack now
                if (pendingTarget != null && !BadPacketsManager.serverUsingItem()) {
                    attackTick = mc.thePlayer.ticksExisted;
                    mc.thePlayer.swingItem();
                    mc.playerController.attackEntity(mc.thePlayer, pendingTarget);
                    if (pendingVoidHit)
                        spawnVoidArrow(pendingTarget, flickYaw);
                }
                pendingTarget = null;
                pendingVoidHit = false;
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

        RenderUtils.beginWorldOverlay();

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        double vx = mc.getRenderManager().viewerPosX;
        double vy = mc.getRenderManager().viewerPosY;
        double vz = mc.getRenderManager().viewerPosZ;

        for (VoidArrow arrow : voidArrows) {
            float raw = 1f - (now - arrow.spawnTime) / (float) ARROW_LIFETIME_MS;
            float alpha = raw * raw * (3f - 2f * raw);
            double baseX = arrow.x, baseY = arrow.y + 1.2, baseZ = arrow.z;

            int glowCol = M3.primary();
            int outlineCol = M3.surfaceContainerLowest();
            int tailCol = M3.primaryContainer();
            int tipCol = M3.primary();

            wr.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
            addM3ArrowTriangles(wr, baseX, baseY, baseZ, arrow.dirX, arrow.dirZ, OUTLINE_THICKNESS * 2.1,
                    glowCol, glowCol, alpha * 0.24f, vx, vy, vz);
            addM3ArrowTriangles(wr, baseX, baseY, baseZ, arrow.dirX, arrow.dirZ, OUTLINE_THICKNESS,
                    outlineCol, outlineCol, alpha * 0.88f, vx, vy, vz);
            addM3ArrowTriangles(wr, baseX, baseY, baseZ, arrow.dirX, arrow.dirZ, 0,
                    tailCol, tipCol, alpha * 0.96f, vx, vy, vz);
            tessellator.draw();
        }

        RenderUtils.endWorldOverlay();
    };

    /** An upright M3 arrow: it points along the push direction and its width runs vertically. */
    private static void addM3ArrowTriangles(WorldRenderer wr, double baseX, double baseY, double baseZ,
                                            double dirX, double dirZ, double expand,
                                            int tailRgb, int tipRgb, float a,
                                            double vx, double vy, double vz) {
        float tr = ((tailRgb >> 16) & 0xFF) / 255f;
        float tg = ((tailRgb >> 8) & 0xFF) / 255f;
        float tb = (tailRgb & 0xFF) / 255f;
        float hr = ((tipRgb >> 16) & 0xFF) / 255f;
        float hg = ((tipRgb >> 8) & 0xFF) / 255f;
        float hb = (tipRgb & 0xFF) / 255f;

        double tailX = baseX - dirX * expand;
        double tailZ = baseZ - dirZ * expand;
        double tipX = baseX + dirX * (ARROW_LENGTH + expand);
        double tipZ = baseZ + dirZ * (ARROW_LENGTH + expand);
        double shaftEndX = baseX + dirX * (ARROW_LENGTH - ARROW_HEAD_SIZE);
        double shaftEndZ = baseZ + dirZ * (ARROW_LENGTH - ARROW_HEAD_SIZE);

        double shaftHalf = SHAFT_HALF_WIDTH + expand;
        double headHalf = HEAD_HALF_WIDTH + expand;

        vertex(wr, tailX, baseY + shaftHalf, tailZ, vx, vy, vz, tr, tg, tb, a);
        vertex(wr, shaftEndX, baseY + shaftHalf, shaftEndZ, vx, vy, vz, hr, hg, hb, a);
        vertex(wr, shaftEndX, baseY - shaftHalf, shaftEndZ, vx, vy, vz, hr, hg, hb, a);

        vertex(wr, tailX, baseY + shaftHalf, tailZ, vx, vy, vz, tr, tg, tb, a);
        vertex(wr, shaftEndX, baseY - shaftHalf, shaftEndZ, vx, vy, vz, hr, hg, hb, a);
        vertex(wr, tailX, baseY - shaftHalf, tailZ, vx, vy, vz, tr, tg, tb, a);

        vertex(wr, shaftEndX, baseY + headHalf, shaftEndZ, vx, vy, vz, hr, hg, hb, a);
        vertex(wr, tipX, baseY, tipZ, vx, vy, vz, hr, hg, hb, a);
        vertex(wr, shaftEndX, baseY - headHalf, shaftEndZ, vx, vy, vz, hr, hg, hb, a);
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

    /**
     * Void2, from OpenB4's Displace: which side of the target, across our line to it, has more open drop (0 the side a
     * smaller yaw pushes to, 1 the other), or -1 if neither side does or straight behind it has the most. Columns are
     * sampled 1 to 8 blocks out, each looking 6 blocks down for ground.
     */
    private int void2Side(Entity entity, int previous) {
        double dx = entity.posX - mc.thePlayer.posX;
        double dz = entity.posZ - mc.thePlayer.posZ;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.001)
            return -1;
        dx /= len;
        dz /= len;
        double px = -dz, pz = dx;
        double y = entity.posY;
        int minusSide = 0, plusSide = 0, behind = 0;
        for (int n : new int[]{1, 2, 3, 4, 6, 8}) {
            int weight = n < 6 ? 2 : 1;
            for (int i = -1; i <= 1; i++) {
                if (open(entity.posX + dx * n + px * i, y, entity.posZ + dz * n + pz * i))
                    behind += weight;
                if (open(entity.posX - px * n + dx * i, y, entity.posZ - pz * n + dz * i))
                    minusSide += weight;
                if (open(entity.posX + px * n + dx * i, y, entity.posZ + pz * n + dz * i))
                    plusSide += weight;
            }
        }
        if (minusSide == 0 && plusSide == 0)
            return -1;
        if (behind >= minusSide && behind >= plusSide)
            return -1;
        if (minusSide == plusSide && previous != -1)
            return previous;
        return minusSide > plusSide ? 0 : 1;
    }

    /** No ground within 6 blocks below this point. */
    private boolean open(double x, double y, double z) {
        return mc.theWorld.rayTraceBlocks(new Vec3(x, y, z), new Vec3(x, y - 6.0, z), true) == null;
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
        void2Side = -1;
        voidArrows.clear();
    }

    public enum FlickDirection {
        Left, Right, Back, Custom, Void, Void2;
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
